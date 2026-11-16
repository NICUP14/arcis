// Package proto defines the Arcis Bridge wire protocol: newline-delimited JSON
// messages over a TLS connection (see docs/protocol.md).
package proto

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
)

// DefaultPort is the TCP port the bridge listens on.
const DefaultPort = 41990

// StatusPort is the localhost-only HTTP status page (shows the pairing PIN).
const StatusPort = 41991

// ServiceType is the mDNS service the bridge announces.
const ServiceType = "_arcis._tcp"

// Version is the protocol version spoken by this bridge.
const Version = 1

// MaxLine bounds a single message so a peer can't make us buffer unbounded data.
const MaxLine = 64 * 1024

// Message types.
const (
	TypePair    = "pair"    // phone -> bridge: {pin, name}
	TypePaired  = "paired"  // bridge -> phone: {token, host, addresses}
	TypeHello   = "hello"   // phone -> bridge: {token, name}
	TypeWelcome = "welcome" // bridge -> phone: {host, os, version, addresses}
	TypeError   = "error"   // bridge -> phone: {msg}

	TypeMove   = "move"   // {dx, dy} relative pointer movement in pixels
	TypeButton = "button" // {button, down}
	TypeClick  = "click"  // {button}
	TypeScroll = "scroll" // {dx, dy} in wheel notches (fractions allowed); dy > 0 scrolls content down
	TypeKey    = "key"    // {key, mods}
	TypeText   = "text"   // {text}
	TypePing   = "ping"   // {t}
	TypePong   = "pong"   // {t}

	TypeClip  = "clip"  // both directions: {text} clipboard text
	TypeWake  = "wake"  // phone -> bridge: {mac} broadcast Wake-on-LAN (wake relay)
	TypeAudio = "audio" // phone -> bridge: {audio} 16 kHz mono s16le PCM for the virtual mic
)

// Mouse buttons.
const (
	ButtonLeft   = "left"
	ButtonRight  = "right"
	ButtonMiddle = "middle"
)

// Message is the union of all fields used by any message type.
type Message struct {
	T string `json:"t"`

	// Pairing / session.
	PIN     string `json:"pin,omitempty"`
	Name    string `json:"name,omitempty"`
	Token   string `json:"token,omitempty"`
	Host    string `json:"host,omitempty"`
	OS      string `json:"os,omitempty"`
	Version int    `json:"version,omitempty"`
	Msg     string `json:"msg,omitempty"`
	// Addresses the bridge can be reached at (LAN, Tailscale...), sent in paired/welcome.
	Addresses []string `json:"addresses,omitempty"`
	// MAC addresses of the computer's network cards (welcome), for Wake-on-LAN.
	MACs []string `json:"macs,omitempty"`
	MAC  string   `json:"mac,omitempty"`
	// Audio is PCM for the virtual microphone (base64 in JSON).
	Audio []byte `json:"audio,omitempty"`

	// Input.
	DX     float64  `json:"dx,omitempty"`
	DY     float64  `json:"dy,omitempty"`
	Button string   `json:"button,omitempty"`
	Down   bool     `json:"down,omitempty"`
	Key    string   `json:"key,omitempty"`
	Mods   []string `json:"mods,omitempty"`
	Text   string   `json:"text,omitempty"`
	Time   int64    `json:"time,omitempty"`
}

// Reader reads messages from a stream.
type Reader struct{ s *bufio.Scanner }

// NewReader wraps r.
func NewReader(r io.Reader) *Reader {
	s := bufio.NewScanner(r)
	s.Buffer(make([]byte, 4096), MaxLine)
	return &Reader{s: s}
}

// ErrClosed is returned when the peer closed the stream.
var ErrClosed = errors.New("connection closed")

// Read returns the next message.
func (r *Reader) Read() (Message, error) {
	for r.s.Scan() {
		line := r.s.Bytes()
		if len(line) == 0 {
			continue
		}
		var m Message
		if err := json.Unmarshal(line, &m); err != nil {
			return Message{}, fmt.Errorf("bad message: %w", err)
		}
		if m.T == "" {
			return Message{}, errors.New("bad message: missing type")
		}
		return m, nil
	}
	if err := r.s.Err(); err != nil {
		return Message{}, err
	}
	return Message{}, ErrClosed
}

// Write sends one message.
func Write(w io.Writer, m Message) error {
	b, err := json.Marshal(m)
	if err != nil {
		return err
	}
	b = append(b, '\n')
	_, err = w.Write(b)
	return err
}
