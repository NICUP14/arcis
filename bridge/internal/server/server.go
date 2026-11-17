// Package server accepts phones over TLS, handles pairing and performs their input.
package server

import (
	"crypto/tls"
	"errors"
	"fmt"
	"log"
	"math"
	"net"
	"runtime"
	"sync"
	"sync/atomic"
	"time"

	"github.com/NICUP14/arcis/bridge/internal/input"
	"github.com/NICUP14/arcis/bridge/internal/mic"
	"github.com/NICUP14/arcis/bridge/internal/pairing"
	"github.com/NICUP14/arcis/bridge/internal/proto"
)

// Server is the bridge's network side.
type Server struct {
	Store    *pairing.Store
	Input    input.Injector
	Cert     tls.Certificate
	HostName string
	Log      *log.Logger
	// Addresses lists where phones can reach this computer (sent so they can fall back
	// from the LAN to e.g. Tailscale on their own). May be nil.
	Addresses func() []string
	// MACs lists this computer's MAC addresses (sent in welcome). May be nil.
	MACs func() []string
	// Clipboard syncs text with phones. May be nil (no sync).
	Clipboard Clipboard
	// Wake broadcasts Wake-on-LAN for another computer (wake relay). May be nil.
	Wake func(mac string) error
	// OpenMic opens the virtual microphone on first use. May be nil (no mic).
	OpenMic func() (mic.Sink, error)
	// SunshinePIN completes Moonlight's pairing with Sunshine on this computer using the
	// PIN the phone read from Moonlight. fromIP is the phone's address. May be nil.
	SunshinePIN func(pin, name, fromIP string) error

	micMu     sync.Mutex
	micSink   mic.Sink
	micFailed bool

	inputMu   sync.Mutex // input from several phones is serialized
	sessMu    sync.Mutex
	sessions  map[*sessionConn]struct{}
	lastClip  atomic.Value // string set by a phone, not to be echoed back
	connected atomic.Int32
	lastPhone atomic.Value // string
}

// Clipboard is the computer's text clipboard.
type Clipboard interface {
	Get() (string, error)
	Set(text string) error
}

// sessionConn serializes writes to one phone.
type sessionConn struct {
	mu sync.Mutex
	c  net.Conn
}

func (s *sessionConn) write(m proto.Message) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	return proto.Write(s.c, m)
}

// Broadcast sends m to every connected phone.
func (s *Server) Broadcast(m proto.Message) {
	s.sessMu.Lock()
	list := make([]*sessionConn, 0, len(s.sessions))
	for sc := range s.sessions {
		list = append(list, sc)
	}
	s.sessMu.Unlock()
	for _, sc := range list {
		_ = sc.write(m)
	}
}

// ClipboardSetByPhone reports whether text is what a phone last put on the clipboard.
func (s *Server) ClipboardSetByPhone(text string) bool {
	v, _ := s.lastClip.Load().(string)
	return v == text
}

// playMic sends phone audio to the virtual microphone, opening it on first use.
func (s *Server) playMic(pcm []byte) {
	if s.OpenMic == nil || len(pcm) == 0 {
		return
	}
	s.micMu.Lock()
	defer s.micMu.Unlock()
	if s.micSink == nil {
		if s.micFailed {
			return
		}
		sink, err := s.OpenMic()
		if err != nil {
			s.micFailed = true // say it once, not 50 times a second
			s.logf("phone microphone unavailable: %v", err)
			return
		}
		s.logf("phone microphone -> %s", sink.Name())
		s.micSink = sink
	}
	if err := s.micSink.Write(pcm); err != nil {
		s.logf("microphone: %v", err)
	}
}

// CloseMic releases the virtual microphone.
func (s *Server) CloseMic() {
	s.micMu.Lock()
	defer s.micMu.Unlock()
	if s.micSink != nil {
		_ = s.micSink.Close()
		s.micSink = nil
	}
}

// Connected is the number of phones currently connected.
func (s *Server) Connected() int { return int(s.connected.Load()) }

// LastPhone is the name of the most recently connected phone.
func (s *Server) LastPhone() string {
	v, _ := s.lastPhone.Load().(string)
	return v
}

// Serve accepts connections on ln until it is closed.
func (s *Server) Serve(ln net.Listener) error {
	tl := tls.NewListener(ln, &tls.Config{
		Certificates: []tls.Certificate{s.Cert},
		MinVersion:   tls.VersionTLS12,
	})
	for {
		c, err := tl.Accept()
		if err != nil {
			if errors.Is(err, net.ErrClosed) {
				return nil
			}
			return err
		}
		go s.handle(c)
	}
}

func (s *Server) macs() []string {
	if s.MACs == nil {
		return nil
	}
	return s.MACs()
}

func (s *Server) addresses() []string {
	if s.Addresses == nil {
		return nil
	}
	return s.Addresses()
}

func (s *Server) logf(format string, args ...any) {
	if s.Log != nil {
		s.Log.Printf(format, args...)
	}
}

func (s *Server) handle(c net.Conn) {
	defer c.Close()
	remote := c.RemoteAddr().String()
	_ = c.SetReadDeadline(time.Now().Add(15 * time.Second))
	r := proto.NewReader(c)
	first, err := r.Read()
	if err != nil {
		s.logf("%s: %v", remote, err)
		return
	}
	switch first.T {
	case proto.TypePair:
		token, err := s.Store.Pair(first.PIN, first.Name)
		if err != nil {
			s.logf("%s: pairing refused: %v", remote, err)
			_ = proto.Write(c, proto.Message{T: proto.TypeError, Msg: "Wrong PIN"})
			return
		}
		s.logf("paired %q from %s", first.Name, remote)
		_ = proto.Write(c, proto.Message{T: proto.TypePaired, Token: token, Host: s.HostName, Addresses: s.addresses()})
		return
	case proto.TypeHello:
		dev, ok := s.Store.Check(first.Token)
		if !ok {
			s.logf("%s: unknown device", remote)
			_ = proto.Write(c, proto.Message{T: proto.TypeError, Msg: "Not paired with this computer"})
			return
		}
		if err := proto.Write(c, proto.Message{
			T: proto.TypeWelcome, Host: s.HostName, OS: runtime.GOOS, Version: proto.Version,
			Addresses: s.addresses(), MACs: s.macs(),
		}); err != nil {
			return
		}
		s.logf("%q connected from %s", dev.Name, remote)
		s.connected.Add(1)
		s.lastPhone.Store(dev.Name)
		defer s.connected.Add(-1)
		_ = c.SetReadDeadline(time.Time{})
		s.session(c, r)
		s.logf("%q disconnected", dev.Name)
	default:
		_ = proto.Write(c, proto.Message{T: proto.TypeError, Msg: "say hello first"})
	}
}

// session performs messages from an authenticated phone until it disconnects.
func (s *Server) session(c net.Conn, r *proto.Reader) {
	sc := &sessionConn{c: c}
	s.sessMu.Lock()
	if s.sessions == nil {
		s.sessions = map[*sessionConn]struct{}{}
	}
	s.sessions[sc] = struct{}{}
	s.sessMu.Unlock()
	defer func() {
		s.sessMu.Lock()
		delete(s.sessions, sc)
		s.sessMu.Unlock()
	}()

	var remX, remY float64 // sub-pixel pointer movement
	for {
		m, err := r.Read()
		if err != nil {
			return
		}
		switch m.T {
		case proto.TypePing:
			_ = sc.write(proto.Message{T: proto.TypePong, Time: m.Time})
			continue
		case proto.TypeClip:
			if s.Clipboard != nil {
				s.lastClip.Store(m.Text)
				if err := s.Clipboard.Set(m.Text); err != nil {
					s.logf("clipboard: %v", err)
				}
			}
			continue
		case proto.TypeAudio:
			s.playMic(m.Audio)
			continue
		case proto.TypeSunshinePIN:
			// Takes a few seconds (waits for Sunshine to see Moonlight's request).
			go s.sunshinePIN(sc, m, c.RemoteAddr())
			continue
		case proto.TypeWake:
			if s.Wake != nil {
				if err := s.Wake(m.MAC); err != nil {
					s.logf("wake %s: %v", m.MAC, err)
				} else {
					s.logf("wake relay: sent Wake-on-LAN to %s", m.MAC)
				}
			}
			continue
		}
		if err := s.perform(m, &remX, &remY); err != nil {
			s.logf("%s: %v", m.T, err)
		}
	}
}

func (s *Server) sunshinePIN(sc *sessionConn, m proto.Message, remote net.Addr) {
	res := proto.Message{T: proto.TypeSunshineResult}
	from := ""
	if ta, ok := remote.(*net.TCPAddr); ok {
		from = ta.IP.String()
	}
	switch {
	case s.SunshinePIN == nil:
		res.Msg = "This Arcis Bridge can't pair Moonlight"
	case len(m.PIN) != 4:
		res.Msg = "Moonlight's PIN should have 4 digits"
	default:
		if err := s.SunshinePIN(m.PIN, m.Name, from); err != nil {
			res.Msg = err.Error()
		}
	}
	if res.Msg == "" {
		s.logf("Moonlight on %s paired with Sunshine", m.Name)
	} else {
		s.logf("Moonlight pairing for %s: %s", m.Name, res.Msg)
	}
	_ = sc.write(res)
}

func (s *Server) perform(m proto.Message, remX, remY *float64) error {
	if s.Input == nil {
		return errors.New("no input on this system")
	}
	s.inputMu.Lock()
	defer s.inputMu.Unlock()
	switch m.T {
	case proto.TypeMove:
		*remX += m.DX
		*remY += m.DY
		dx, dy := math.Trunc(*remX), math.Trunc(*remY)
		*remX -= dx
		*remY -= dy
		return s.Input.Move(int(dx), int(dy))
	case proto.TypeButton:
		return s.Input.Button(m.Button, m.Down)
	case proto.TypeClick:
		return input.Click(s.Input, m.Button)
	case proto.TypeScroll:
		return s.Input.Scroll(m.DX, m.DY)
	case proto.TypeKey:
		return s.Input.Key(m.Key, m.Mods)
	case proto.TypeText:
		return s.Input.Text(m.Text)
	default:
		return fmt.Errorf("unknown message type %q", m.T)
	}
}
