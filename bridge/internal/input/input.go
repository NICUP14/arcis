// Package input performs pointer and keyboard input on the host OS.
package input

import (
	"errors"
	"strings"
)

// Injector performs input on this computer.
type Injector interface {
	// Move moves the pointer by (dx, dy) pixels.
	Move(dx, dy int) error
	// Button presses or releases a mouse button ("left", "right", "middle").
	Button(button string, down bool) error
	// Scroll scrolls by notches (fractions allowed); dy > 0 scrolls content down, dx > 0 right.
	Scroll(dx, dy float64) error
	// Key presses and releases a named key with modifiers ("ctrl", "shift", "alt", "meta").
	Key(key string, mods []string) error
	// Text types Unicode text.
	Text(text string) error
	// Close releases OS resources.
	Close() error
}

// ErrUnsupported is returned for keys or features not available on this OS.
var ErrUnsupported = errors.New("not supported on this system")

// Click presses and releases a button.
func Click(in Injector, button string) error {
	if err := in.Button(button, true); err != nil {
		return err
	}
	return in.Button(button, false)
}

// Named keys understood by every platform implementation (case-insensitive).
// Single letters "A".."Z" and digits "0".."9" are accepted too.
var namedKeys = []string{
	"Enter", "Backspace", "Escape", "Tab", "Space", "Delete",
	"Left", "Right", "Up", "Down", "Home", "End", "PageUp", "PageDown",
	"Meta",           // the Windows / Super / Command key on its own (Start menu)
	"Equal", "Minus", // with Ctrl/Cmd: zoom in / out
}

// NormalizeKey returns the canonical key name, or "" if unknown.
func NormalizeKey(k string) string {
	if len(k) == 1 {
		c := strings.ToUpper(k)[0]
		if (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') {
			return string(c)
		}
		return ""
	}
	for _, n := range namedKeys {
		if strings.EqualFold(n, k) {
			return n
		}
	}
	return ""
}

// HasMod reports whether mods contains m (case-insensitive).
func HasMod(mods []string, m string) bool {
	for _, x := range mods {
		if strings.EqualFold(x, m) {
			return true
		}
	}
	return false
}

// SplitScroll turns fractional notch input into whole notches, keeping the remainder.
type SplitScroll struct{ remX, remY float64 }

// Add accumulates and returns whole notches to send now.
func (s *SplitScroll) Add(dx, dy float64) (int, int) {
	s.remX += dx
	s.remY += dy
	x := int(s.remX)
	y := int(s.remY)
	s.remX -= float64(x)
	s.remY -= float64(y)
	return x, y
}
