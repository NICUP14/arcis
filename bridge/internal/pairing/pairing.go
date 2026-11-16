// Package pairing keeps the pairing PIN and the list of paired phones.
package pairing

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// Device is a paired phone.
type Device struct {
	Name     string    `json:"name"`
	Token    string    `json:"token"`
	PairedAt time.Time `json:"paired_at"`
	LastSeen time.Time `json:"last_seen,omitempty"`
}

// ErrBadPIN is returned for a wrong PIN; after MaxAttempts wrong PINs a new PIN is generated.
var ErrBadPIN = errors.New("wrong PIN")

// MaxAttempts wrong PINs before the PIN changes.
const MaxAttempts = 3

// Store holds the current PIN and the paired devices (persisted as devices.json).
type Store struct {
	mu       sync.Mutex
	path     string
	pin      string
	attempts int
	devices  []Device
}

// Open loads devices.json from dir (missing file = no devices) and picks a fresh PIN.
func Open(dir string) (*Store, error) {
	s := &Store{path: filepath.Join(dir, "devices.json")}
	if b, err := os.ReadFile(s.path); err == nil {
		if err := json.Unmarshal(b, &s.devices); err != nil {
			return nil, fmt.Errorf("devices.json: %w", err)
		}
	} else if !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	s.newPIN()
	return s, nil
}

// PIN returns the current 6-digit pairing PIN.
func (s *Store) PIN() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.pin
}

// Devices returns a copy of the paired devices.
func (s *Store) Devices() []Device {
	s.mu.Lock()
	defer s.mu.Unlock()
	return append([]Device(nil), s.devices...)
}

// Pair checks pin and, if right, registers a phone and returns its secret token.
func (s *Store) Pair(pin, name string) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if subtle.ConstantTimeCompare([]byte(pin), []byte(s.pin)) != 1 {
		s.attempts++
		if s.attempts >= MaxAttempts {
			s.newPIN()
		}
		return "", ErrBadPIN
	}
	tok := make([]byte, 32)
	if _, err := rand.Read(tok); err != nil {
		return "", err
	}
	token := hex.EncodeToString(tok)
	if name == "" {
		name = "Phone"
	}
	s.devices = append(s.devices, Device{Name: name, Token: token, PairedAt: time.Now()})
	s.newPIN() // a PIN works once
	return token, s.saveLocked()
}

// Check returns the device with this token, or false.
func (s *Store) Check(token string) (Device, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	for i, d := range s.devices {
		if token != "" && subtle.ConstantTimeCompare([]byte(token), []byte(d.Token)) == 1 {
			s.devices[i].LastSeen = time.Now()
			_ = s.saveLocked()
			return s.devices[i], true
		}
	}
	return Device{}, false
}

// Forget removes a paired device by name.
func (s *Store) Forget(name string) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := s.devices[:0]
	for _, d := range s.devices {
		if d.Name != name {
			out = append(out, d)
		}
	}
	s.devices = out
	return s.saveLocked()
}

func (s *Store) newPIN() {
	n, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		n = big.NewInt(time.Now().UnixNano() % 1_000_000)
	}
	s.pin = fmt.Sprintf("%06d", n.Int64())
	s.attempts = 0
}

func (s *Store) saveLocked() error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return err
	}
	b, err := json.MarshalIndent(s.devices, "", "  ")
	if err != nil {
		return err
	}
	tmp := s.path + ".tmp"
	if err := os.WriteFile(tmp, b, 0o600); err != nil {
		return err
	}
	return os.Rename(tmp, s.path)
}
