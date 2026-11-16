package server

import (
	"crypto/tls"
	"fmt"
	"net"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/NICUP14/arcis/bridge/internal/certs"
	"github.com/NICUP14/arcis/bridge/internal/pairing"
	"github.com/NICUP14/arcis/bridge/internal/proto"
)

// fakeInput records what the server asked it to do.
type fakeInput struct {
	mu  sync.Mutex
	log []string
}

func (f *fakeInput) add(s string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.log = append(f.log, s)
	return nil
}
func (f *fakeInput) Move(dx, dy int) error { return f.add(fmt.Sprintf("move %d %d", dx, dy)) }
func (f *fakeInput) Button(b string, down bool) error {
	return f.add(fmt.Sprintf("button %s %v", b, down))
}
func (f *fakeInput) Scroll(dx, dy float64) error { return f.add(fmt.Sprintf("scroll %g %g", dx, dy)) }
func (f *fakeInput) Key(k string, mods []string) error {
	return f.add("key " + k + " " + strings.Join(mods, "+"))
}
func (f *fakeInput) Text(t string) error { return f.add("text " + t) }
func (f *fakeInput) Close() error        { return nil }
func (f *fakeInput) entries() []string {
	f.mu.Lock()
	defer f.mu.Unlock()
	return append([]string(nil), f.log...)
}

type fakeClip struct {
	mu   sync.Mutex
	text string
}

func (f *fakeClip) Get() (string, error) { f.mu.Lock(); defer f.mu.Unlock(); return f.text, nil }
func (f *fakeClip) Set(t string) error   { f.mu.Lock(); defer f.mu.Unlock(); f.text = t; return nil }

func startServer(t *testing.T) (*Server, *fakeInput, string) {
	t.Helper()
	dir := t.TempDir()
	cert, err := certs.LoadOrCreate(dir)
	if err != nil {
		t.Fatal(err)
	}
	store, err := pairing.Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	in := &fakeInput{}
	s := &Server{Store: store, Input: in, Cert: cert, HostName: "test-pc",
		Addresses: func() []string { return []string{"192.168.1.5", "100.64.1.2"} },
		MACs:      func() []string { return []string{"aa:bb:cc:dd:ee:ff"} },
		Clipboard: &fakeClip{},
	}
	var woke []string
	s.Wake = func(mac string) error { woke = append(woke, mac); return nil }
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go s.Serve(ln)
	return s, in, ln.Addr().String()
}

// dial connects like the phone: TLS, checking the pinned fingerprint.
func dial(t *testing.T, addr, fingerprint string) (*tls.Conn, *proto.Reader) {
	t.Helper()
	c, err := tls.Dial("tcp", addr, &tls.Config{
		InsecureSkipVerify: true, // self-signed: verified by pinning below
		VerifyConnection: func(cs tls.ConnectionState) error {
			got := certs.Fingerprint(tls.Certificate{Certificate: [][]byte{cs.PeerCertificates[0].Raw}})
			if fingerprint != "" && got != fingerprint {
				return fmt.Errorf("fingerprint mismatch")
			}
			return nil
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })
	return c, proto.NewReader(c)
}

func TestPairThenControl(t *testing.T) {
	s, in, addr := startServer(t)
	fp := certs.Fingerprint(s.Cert)

	// Pair with the PIN shown on the bridge.
	c, r := dial(t, addr, fp)
	if err := proto.Write(c, proto.Message{T: proto.TypePair, PIN: s.Store.PIN(), Name: "Galaxy"}); err != nil {
		t.Fatal(err)
	}
	paired, err := r.Read()
	if err != nil || paired.T != proto.TypePaired || paired.Token == "" || paired.Host != "test-pc" || len(paired.Addresses) != 2 {
		t.Fatalf("pairing reply %+v %v", paired, err)
	}

	// Reconnect with the token and control the computer.
	c2, r2 := dial(t, addr, fp)
	_ = proto.Write(c2, proto.Message{T: proto.TypeHello, Token: paired.Token})
	welcome, err := r2.Read()
	if err != nil || welcome.T != proto.TypeWelcome || welcome.Version != proto.Version {
		t.Fatalf("welcome %+v %v", welcome, err)
	}
	msgs := []proto.Message{
		{T: proto.TypeMove, DX: 2.5, DY: -1},
		{T: proto.TypeMove, DX: 0.5, DY: 0},
		{T: proto.TypeClick, Button: "left"},
		{T: proto.TypeScroll, DY: 1.5},
		{T: proto.TypeKey, Key: "Z", Mods: []string{"ctrl"}},
		{T: proto.TypeText, Text: "hi"},
		{T: proto.TypePing, Time: 42},
	}
	for _, m := range msgs {
		_ = proto.Write(c2, m)
	}
	pong, err := r2.Read()
	if err != nil || pong.T != proto.TypePong || pong.Time != 42 {
		t.Fatalf("pong %+v %v", pong, err)
	}
	want := []string{"move 2 -1", "move 1 0", "button left true", "button left false", "scroll 0 1.5", "key Z ctrl", "text hi"}
	got := in.entries()
	if strings.Join(got, "|") != strings.Join(want, "|") {
		t.Fatalf("input\n got %v\nwant %v", got, want)
	}
	if s.Connected() != 1 || s.LastPhone() != "Galaxy" {
		t.Fatalf("connected=%d last=%q", s.Connected(), s.LastPhone())
	}
}

func TestClipboardAndWake(t *testing.T) {
	s, _, addr := startServer(t)
	fp := certs.Fingerprint(s.Cert)
	c, r := dial(t, addr, fp)
	_ = proto.Write(c, proto.Message{T: proto.TypePair, PIN: s.Store.PIN(), Name: "P"})
	paired, _ := r.Read()
	c2, r2 := dial(t, addr, fp)
	_ = proto.Write(c2, proto.Message{T: proto.TypeHello, Token: paired.Token})
	welcome, _ := r2.Read()
	if len(welcome.MACs) != 1 {
		t.Fatalf("welcome without MACs: %+v", welcome)
	}
	// Phone -> computer clipboard.
	_ = proto.Write(c2, proto.Message{T: proto.TypeClip, Text: "from phone"})
	_ = proto.Write(c2, proto.Message{T: proto.TypePing, Time: 1})
	if m, _ := r2.Read(); m.T != proto.TypePong {
		t.Fatalf("expected pong, got %+v", m)
	}
	if got, _ := s.Clipboard.Get(); got != "from phone" || !s.ClipboardSetByPhone("from phone") {
		t.Fatalf("clipboard %q", got)
	}
	// Computer -> phone clipboard.
	s.Broadcast(proto.Message{T: proto.TypeClip, Text: "from pc"})
	if m, _ := r2.Read(); m.T != proto.TypeClip || m.Text != "from pc" {
		t.Fatalf("expected clip, got %+v", m)
	}
}

func TestWrongPINAndUnknownToken(t *testing.T) {
	s, in, addr := startServer(t)
	c, r := dial(t, addr, "")
	wrong := "000000"
	if s.Store.PIN() == wrong {
		wrong = "111111"
	}
	_ = proto.Write(c, proto.Message{T: proto.TypePair, PIN: wrong})
	if m, _ := r.Read(); m.T != proto.TypeError {
		t.Fatalf("expected error, got %+v", m)
	}
	c2, r2 := dial(t, addr, "")
	_ = proto.Write(c2, proto.Message{T: proto.TypeHello, Token: "forged"})
	if m, _ := r2.Read(); m.T != proto.TypeError {
		t.Fatalf("expected error, got %+v", m)
	}
	// Input sent anyway must be ignored.
	_ = proto.Write(c2, proto.Message{T: proto.TypeText, Text: "evil"})
	time.Sleep(100 * time.Millisecond)
	if len(in.entries()) != 0 {
		t.Fatalf("unauthenticated input performed: %v", in.entries())
	}
}
