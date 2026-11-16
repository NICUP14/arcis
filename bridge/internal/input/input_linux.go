//go:build linux

package input

import (
	"encoding/binary"
	"fmt"
	"os"
	"strings"
	"time"
	"unsafe"

	"golang.org/x/sys/unix"
)

// Linux implementation: a virtual mouse + keyboard through /dev/uinput.
// Works on X11 and Wayland. The user needs write access to /dev/uinput
// (see docs/bridge.md for the udev rule).

const (
	evSyn = 0x00
	evKey = 0x01
	evRel = 0x02

	synReport = 0

	relX      = 0x00
	relY      = 0x01
	relHWheel = 0x06
	relWheel  = 0x08

	btnLeft   = 0x110
	btnRight  = 0x111
	btnMiddle = 0x112

	uiSetEvBit   = 0x40045564
	uiSetKeyBit  = 0x40045565
	uiSetRelBit  = 0x40045566
	uiDevSetup   = 0x405c5503
	uiDevCreate  = 0x5501
	uiDevDestroy = 0x5502

	keyLeftCtrl  = 29
	keyLeftShift = 42
	keyLeftAlt   = 56
	keyLeftMeta  = 125
)

var linuxKeyByName = map[string]uint16{
	"Enter": 28, "Backspace": 14, "Escape": 1, "Tab": 15, "Space": 57, "Delete": 111,
	"Left": 105, "Right": 106, "Up": 103, "Down": 108, "Home": 102, "End": 107,
	"PageUp": 104, "PageDown": 109,
}

// US layout: letters and digits.
var linuxLetters = []uint16{30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, 49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44}
var linuxDigits = []uint16{11, 2, 3, 4, 5, 6, 7, 8, 9, 10} // 0..9

// US layout symbols: rune -> (key, shift).
var linuxSymbols = map[rune][2]uint16{
	' ': {57, 0}, '-': {12, 0}, '=': {13, 0}, '[': {26, 0}, ']': {27, 0}, '\\': {43, 0}, ';': {39, 0},
	'\'': {40, 0}, '`': {41, 0}, ',': {51, 0}, '.': {52, 0}, '/': {53, 0}, '\n': {28, 0}, '\t': {15, 0},
	'!': {2, 1}, '@': {3, 1}, '#': {4, 1}, '$': {5, 1}, '%': {6, 1}, '^': {7, 1}, '&': {8, 1}, '*': {9, 1},
	'(': {10, 1}, ')': {11, 1}, '_': {12, 1}, '+': {13, 1}, '{': {26, 1}, '}': {27, 1}, '|': {43, 1},
	':': {39, 1}, '"': {40, 1}, '~': {41, 1}, '<': {51, 1}, '>': {52, 1}, '?': {53, 1},
}

type uinputSetup struct {
	ID struct {
		Bustype, Vendor, Product, Version uint16
	}
	Name         [80]byte
	FFEffectsMax uint32
}

type linuxInjector struct {
	f      *os.File
	scroll SplitScroll
}

// New returns the injector for this OS.
func New() (Injector, error) {
	f, err := os.OpenFile("/dev/uinput", os.O_WRONLY|unix.O_NONBLOCK, 0)
	if err != nil {
		return nil, fmt.Errorf("open /dev/uinput (add the udev rule from docs/bridge.md): %w", err)
	}
	fd := f.Fd()
	ioctl := func(req, arg uintptr) error {
		if _, _, e := unix.Syscall(unix.SYS_IOCTL, fd, req, arg); e != 0 {
			return e
		}
		return nil
	}
	for _, ev := range []uintptr{evKey, evRel, evSyn} {
		if err := ioctl(uiSetEvBit, ev); err != nil {
			f.Close()
			return nil, err
		}
	}
	for _, rel := range []uintptr{relX, relY, relWheel, relHWheel} {
		_ = ioctl(uiSetRelBit, rel)
	}
	for _, k := range []uintptr{btnLeft, btnRight, btnMiddle} {
		_ = ioctl(uiSetKeyBit, k)
	}
	for k := uintptr(1); k < 256; k++ { // all regular keyboard keys
		_ = ioctl(uiSetKeyBit, k)
	}
	var setup uinputSetup
	setup.ID.Bustype = 0x03 // BUS_USB
	setup.ID.Vendor = 0x1209
	setup.ID.Product = 0xA5C1
	copy(setup.Name[:], "Arcis Bridge")
	if err := ioctl(uiDevSetup, uintptr(unsafe.Pointer(&setup))); err != nil {
		f.Close()
		return nil, fmt.Errorf("UI_DEV_SETUP: %w", err)
	}
	if err := ioctl(uiDevCreate, 0); err != nil {
		f.Close()
		return nil, fmt.Errorf("UI_DEV_CREATE: %w", err)
	}
	time.Sleep(200 * time.Millisecond) // let the desktop pick up the new device
	return &linuxInjector{f: f}, nil
}

// emit writes one input_event (64-bit layout: timeval 16 bytes, type, code, value).
func (l *linuxInjector) emit(typ, code uint16, value int32) error {
	var buf [24]byte
	binary.LittleEndian.PutUint16(buf[16:], typ)
	binary.LittleEndian.PutUint16(buf[18:], code)
	binary.LittleEndian.PutUint32(buf[20:], uint32(value))
	_, err := l.f.Write(buf[:])
	return err
}

func (l *linuxInjector) sync() error { return l.emit(evSyn, synReport, 0) }

func (l *linuxInjector) Move(dx, dy int) error {
	if dx != 0 {
		if err := l.emit(evRel, relX, int32(dx)); err != nil {
			return err
		}
	}
	if dy != 0 {
		if err := l.emit(evRel, relY, int32(dy)); err != nil {
			return err
		}
	}
	return l.sync()
}

func (l *linuxInjector) Button(button string, down bool) error {
	code := map[string]uint16{"left": btnLeft, "right": btnRight, "middle": btnMiddle}[strings.ToLower(button)]
	if code == 0 {
		return fmt.Errorf("unknown button %q", button)
	}
	v := int32(0)
	if down {
		v = 1
	}
	if err := l.emit(evKey, code, v); err != nil {
		return err
	}
	return l.sync()
}

func (l *linuxInjector) Scroll(dx, dy float64) error {
	x, y := l.scroll.Add(dx, dy)
	if y != 0 {
		if err := l.emit(evRel, relWheel, int32(-y)); err != nil { // wheel > 0 = up
			return err
		}
	}
	if x != 0 {
		if err := l.emit(evRel, relHWheel, int32(x)); err != nil {
			return err
		}
	}
	return l.sync()
}

func (l *linuxInjector) tap(code uint16, shift bool) error {
	if shift {
		_ = l.emit(evKey, keyLeftShift, 1)
	}
	_ = l.emit(evKey, code, 1)
	_ = l.sync()
	_ = l.emit(evKey, code, 0)
	if shift {
		_ = l.emit(evKey, keyLeftShift, 0)
	}
	return l.sync()
}

func linuxKeyFor(key string) (uint16, bool) {
	k := NormalizeKey(key)
	switch {
	case k == "":
		return 0, false
	case len(k) == 1 && k[0] >= 'A' && k[0] <= 'Z':
		return linuxLetters[k[0]-'A'], true
	case len(k) == 1:
		return linuxDigits[k[0]-'0'], true
	}
	c, ok := linuxKeyByName[k]
	return c, ok
}

func (l *linuxInjector) Key(key string, mods []string) error {
	code, ok := linuxKeyFor(key)
	if !ok {
		return fmt.Errorf("key %q: %w", key, ErrUnsupported)
	}
	modKeys := []struct {
		name string
		code uint16
	}{{"ctrl", keyLeftCtrl}, {"shift", keyLeftShift}, {"alt", keyLeftAlt}, {"meta", keyLeftMeta}}
	for _, m := range modKeys {
		if HasMod(mods, m.name) {
			_ = l.emit(evKey, m.code, 1)
		}
	}
	err := l.tap(code, false)
	for _, m := range modKeys {
		if HasMod(mods, m.name) {
			_ = l.emit(evKey, m.code, 0)
		}
	}
	_ = l.sync()
	return err
}

// Text types US-layout characters; other characters are skipped (uinput has no Unicode input).
func (l *linuxInjector) Text(text string) error {
	var skipped int
	for _, r := range text {
		switch {
		case r >= 'a' && r <= 'z':
			_ = l.tap(linuxLetters[r-'a'], false)
		case r >= 'A' && r <= 'Z':
			_ = l.tap(linuxLetters[r-'A'], true)
		case r >= '0' && r <= '9':
			_ = l.tap(linuxDigits[r-'0'], false)
		default:
			if s, ok := linuxSymbols[r]; ok {
				_ = l.tap(s[0], s[1] == 1)
			} else {
				skipped++
			}
		}
	}
	if skipped > 0 {
		return fmt.Errorf("%d characters outside the US layout were skipped: %w", skipped, ErrUnsupported)
	}
	return nil
}

func (l *linuxInjector) Close() error {
	_, _, _ = unix.Syscall(unix.SYS_IOCTL, l.f.Fd(), uiDevDestroy, 0)
	return l.f.Close()
}
