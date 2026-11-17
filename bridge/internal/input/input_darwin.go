//go:build darwin

package input

import (
	"fmt"
	"strings"
	"unicode/utf16"

	"github.com/ebitengine/purego"
)

// macOS implementation: CoreGraphics CGEvent calls through purego (no cgo).
// The bridge needs the Accessibility permission (System Settings > Privacy & Security).

type cgPoint struct{ X, Y float64 }

const (
	kCGHIDEventTap         = 0
	kCGScrollEventUnitLine = 1

	kCGEventLeftMouseDown  = 1
	kCGEventLeftMouseUp    = 2
	kCGEventRightMouseDown = 3
	kCGEventRightMouseUp   = 4
	kCGEventMouseMoved     = 5
	kCGEventLeftDragged    = 6
	kCGEventRightDragged   = 7
	kCGEventOtherDown      = 25
	kCGEventOtherUp        = 26

	kCGEventFlagShift   = 0x00020000
	kCGEventFlagControl = 0x00040000
	kCGEventFlagAlt     = 0x00080000
	kCGEventFlagCommand = 0x00100000
)

var (
	cgEventCreate                  func(source uintptr) uintptr
	cgEventGetLocation             func(event uintptr) cgPoint
	cgEventCreateMouseEvent        func(source uintptr, typ uint32, pos cgPoint, button uint32) uintptr
	cgEventCreateScrollWheelEvent2 func(source uintptr, units uint32, count uint32, w1, w2, w3 int32) uintptr
	cgEventCreateKeyboardEvent     func(source uintptr, key uint16, down bool) uintptr
	cgEventKeyboardSetUnicode      func(event uintptr, length uint64, chars *uint16)
	cgEventSetFlags                func(event uintptr, flags uint64)
	cgEventPost                    func(tap uint32, event uintptr)
	cfRelease                      func(obj uintptr)
)

var macKeyByName = map[string]uint16{
	"Enter": 36, "Backspace": 51, "Escape": 53, "Tab": 48, "Space": 49, "Delete": 117,
	"Left": 123, "Right": 124, "Down": 125, "Up": 126, "Home": 115, "End": 119,
	"PageUp": 116, "PageDown": 121,
	"Meta": 55, "Equal": 24, "Minus": 27,
	// Play/pause and next/previous are system events on macOS, not key codes: unsupported.
	"VolumeUp": 72, "VolumeDown": 73,
}

// ANSI key codes for A..Z and 0..9.
var macLetters = []uint16{0, 11, 8, 2, 14, 3, 5, 4, 34, 38, 40, 37, 46, 45, 31, 35, 12, 15, 1, 17, 32, 9, 13, 7, 16, 6}
var macDigits = []uint16{29, 18, 19, 20, 21, 23, 22, 26, 28, 25}

type macInjector struct {
	buttons map[string]bool
	scroll  SplitScroll
}

// New returns the injector for this OS.
func New() (Injector, error) {
	lib, err := purego.Dlopen("/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices", purego.RTLD_NOW|purego.RTLD_GLOBAL)
	if err != nil {
		return nil, fmt.Errorf("load CoreGraphics: %w", err)
	}
	cf, err := purego.Dlopen("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation", purego.RTLD_NOW|purego.RTLD_GLOBAL)
	if err != nil {
		return nil, fmt.Errorf("load CoreFoundation: %w", err)
	}
	purego.RegisterLibFunc(&cgEventCreate, lib, "CGEventCreate")
	purego.RegisterLibFunc(&cgEventGetLocation, lib, "CGEventGetLocation")
	purego.RegisterLibFunc(&cgEventCreateMouseEvent, lib, "CGEventCreateMouseEvent")
	purego.RegisterLibFunc(&cgEventCreateScrollWheelEvent2, lib, "CGEventCreateScrollWheelEvent2")
	purego.RegisterLibFunc(&cgEventCreateKeyboardEvent, lib, "CGEventCreateKeyboardEvent")
	purego.RegisterLibFunc(&cgEventKeyboardSetUnicode, lib, "CGEventKeyboardSetUnicodeString")
	purego.RegisterLibFunc(&cgEventSetFlags, lib, "CGEventSetFlags")
	purego.RegisterLibFunc(&cgEventPost, lib, "CGEventPost")
	purego.RegisterLibFunc(&cfRelease, cf, "CFRelease")
	return &macInjector{buttons: map[string]bool{}}, nil
}

func (m *macInjector) location() cgPoint {
	ev := cgEventCreate(0)
	defer cfRelease(ev)
	return cgEventGetLocation(ev)
}

func post(ev uintptr) {
	if ev == 0 {
		return
	}
	cgEventPost(kCGHIDEventTap, ev)
	cfRelease(ev)
}

func (m *macInjector) Move(dx, dy int) error {
	p := m.location()
	p.X += float64(dx)
	p.Y += float64(dy)
	typ := uint32(kCGEventMouseMoved)
	button := uint32(0)
	switch {
	case m.buttons["left"]:
		typ = kCGEventLeftDragged
	case m.buttons["right"]:
		typ, button = kCGEventRightDragged, 1
	}
	post(cgEventCreateMouseEvent(0, typ, p, button))
	return nil
}

func (m *macInjector) Button(button string, down bool) error {
	b := strings.ToLower(button)
	var typ, num uint32
	switch b {
	case "left":
		typ, num = map[bool]uint32{true: kCGEventLeftMouseDown, false: kCGEventLeftMouseUp}[down], 0
	case "right":
		typ, num = map[bool]uint32{true: kCGEventRightMouseDown, false: kCGEventRightMouseUp}[down], 1
	case "middle":
		typ, num = map[bool]uint32{true: kCGEventOtherDown, false: kCGEventOtherUp}[down], 2
	default:
		return fmt.Errorf("unknown button %q", button)
	}
	m.buttons[b] = down
	post(cgEventCreateMouseEvent(0, typ, m.location(), num))
	return nil
}

func (m *macInjector) Scroll(dx, dy float64) error {
	x, y := m.scroll.Add(dx, dy)
	if x == 0 && y == 0 {
		return nil
	}
	post(cgEventCreateScrollWheelEvent2(0, kCGScrollEventUnitLine, 2, int32(-y), int32(-x), 0))
	return nil
}

func macKeyFor(key string) (uint16, bool) {
	k := NormalizeKey(key)
	switch {
	case k == "":
		return 0, false
	case len(k) == 1 && k[0] >= 'A' && k[0] <= 'Z':
		return macLetters[k[0]-'A'], true
	case len(k) == 1:
		return macDigits[k[0]-'0'], true
	}
	c, ok := macKeyByName[k]
	return c, ok
}

func (m *macInjector) Key(key string, mods []string) error {
	code, ok := macKeyFor(key)
	if !ok {
		return fmt.Errorf("key %q: %w", key, ErrUnsupported)
	}
	var flags uint64
	if HasMod(mods, "shift") {
		flags |= kCGEventFlagShift
	}
	if HasMod(mods, "ctrl") {
		flags |= kCGEventFlagControl
	}
	if HasMod(mods, "alt") {
		flags |= kCGEventFlagAlt
	}
	if HasMod(mods, "meta") {
		flags |= kCGEventFlagCommand
	}
	for _, down := range []bool{true, false} {
		ev := cgEventCreateKeyboardEvent(0, code, down)
		if flags != 0 {
			cgEventSetFlags(ev, flags)
		}
		post(ev)
	}
	return nil
}

func (m *macInjector) Text(text string) error {
	for _, r := range text {
		if r == '\n' {
			_ = m.Key("Enter", nil)
			continue
		}
		units := utf16.Encode([]rune{r})
		for _, down := range []bool{true, false} {
			ev := cgEventCreateKeyboardEvent(0, 0, down)
			cgEventKeyboardSetUnicode(ev, uint64(len(units)), &units[0])
			post(ev)
		}
	}
	return nil
}

func (m *macInjector) Close() error { return nil }
