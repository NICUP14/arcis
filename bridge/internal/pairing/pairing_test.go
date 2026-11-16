package pairing

import (
	"errors"
	"testing"
)

func TestPairAndCheck(t *testing.T) {
	dir := t.TempDir()
	s, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	pin := s.PIN()
	if len(pin) != 6 {
		t.Fatalf("pin %q", pin)
	}
	token, err := s.Pair(pin, "Galaxy")
	if err != nil {
		t.Fatal(err)
	}
	if s.PIN() == pin {
		t.Fatal("PIN must change after a successful pairing")
	}
	if d, ok := s.Check(token); !ok || d.Name != "Galaxy" {
		t.Fatalf("check failed: %+v %v", d, ok)
	}
	if _, ok := s.Check("nope"); ok {
		t.Fatal("unknown token accepted")
	}
	// Persisted across restarts.
	s2, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := s2.Check(token); !ok {
		t.Fatal("token lost after reload")
	}
}

func TestWrongPINRotatesAfterMaxAttempts(t *testing.T) {
	s, _ := Open(t.TempDir())
	pin := s.PIN()
	wrong := "000000"
	if pin == wrong {
		wrong = "111111"
	}
	for i := 0; i < MaxAttempts; i++ {
		if _, err := s.Pair(wrong, "x"); !errors.Is(err, ErrBadPIN) {
			t.Fatalf("attempt %d: %v", i, err)
		}
	}
	if s.PIN() == pin {
		t.Fatal("PIN should rotate after too many wrong attempts")
	}
	if _, err := s.Pair(pin, "x"); err == nil {
		t.Fatal("old PIN must not work after rotation")
	}
}

func TestForget(t *testing.T) {
	s, _ := Open(t.TempDir())
	tok, _ := s.Pair(s.PIN(), "A")
	if err := s.Forget("A"); err != nil {
		t.Fatal(err)
	}
	if _, ok := s.Check(tok); ok {
		t.Fatal("forgotten device still accepted")
	}
}
