//go:build windows

package input

import (
	"fmt"
	"strings"
	"syscall"
	"unicode/utf16"
	"unsafe"
)

// Windows implementation: user32 SendInput (no cgo).

var (
	user32        = syscall.NewLazyDLL("user32.dll")
	procSendInput = user32.NewProc("SendInput")
)

const (
	inputMouse    = 0
	inputKeyboard = 1

	mouseeventfMove       = 0x0001
	mouseeventfLeftDown   = 0x0002
	mouseeventfLeftUp     = 0x0004
	mouseeventfRightDown  = 0x0008
	mouseeventfRightUp    = 0x0010
	mouseeventfMiddleDown = 0x0020
	mouseeventfMiddleUp   = 0x0040
	mouseeventfWheel      = 0x0800
	mouseeventfHWheel     = 0x1000

	keyeventfKeyUp   = 0x0002
	keyeventfUnicode = 0x0004

	wheelDelta = 120
)

// mouseInput / keybdInput mirror the 64-bit INPUT struct (40 bytes).
type mouseInput struct {
	Type      uint32
	_         uint32
	Dx        int32
	Dy        int32
	MouseData uint32
	Flags     uint32
	Time      uint32
	_         uint32
	ExtraInfo uintptr
}

type keybdInput struct {
	Type      uint32
	_         uint32
	Vk        uint16
	Scan      uint16
	Flags     uint32
	Time      uint32
	_         uint32
	ExtraInfo uintptr
	_         [8]byte
}

var vkByName = map[string]uint16{
	"Enter": 0x0D, "Backspace": 0x08, "Escape": 0x1B, "Tab": 0x09, "Space": 0x20, "Delete": 0x2E,
	"Left": 0x25, "Up": 0x26, "Right": 0x27, "Down": 0x28, "Home": 0x24, "End": 0x23,
	"PageUp": 0x21, "PageDown": 0x22,
	"Meta": 0x5B, "Equal": 0xBB, "Minus": 0xBD,
	"MediaPlayPause": 0xB3, "MediaNext": 0xB0, "MediaPrev": 0xB1, "VolumeUp": 0xAF, "VolumeDown": 0xAE,
}

var vkMods = []struct {
	name string
	vk   uint16
}{{"ctrl", 0x11}, {"shift", 0x10}, {"alt", 0x12}, {"meta", 0x5B}}

type windowsInjector struct{ scroll SplitScroll }

// New returns the injector for this OS.
func New() (Injector, error) {
	if err := procSendInput.Find(); err != nil {
		return nil, fmt.Errorf("SendInput unavailable: %w", err)
	}
	return &windowsInjector{}, nil
}

func sendMouse(in ...mouseInput) error {
	if len(in) == 0 {
		return nil
	}
	n, _, err := procSendInput.Call(uintptr(len(in)), uintptr(unsafe.Pointer(&in[0])), unsafe.Sizeof(in[0]))
	if int(n) != len(in) {
		return fmt.Errorf("SendInput: %v (is a higher-privilege window focused?)", err)
	}
	return nil
}

func sendKeys(in ...keybdInput) error {
	if len(in) == 0 {
		return nil
	}
	n, _, err := procSendInput.Call(uintptr(len(in)), uintptr(unsafe.Pointer(&in[0])), unsafe.Sizeof(in[0]))
	if int(n) != len(in) {
		return fmt.Errorf("SendInput: %v (is a higher-privilege window focused?)", err)
	}
	return nil
}

func (w *windowsInjector) Move(dx, dy int) error {
	if dx == 0 && dy == 0 {
		return nil
	}
	return sendMouse(mouseInput{Type: inputMouse, Dx: int32(dx), Dy: int32(dy), Flags: mouseeventfMove})
}

func (w *windowsInjector) Button(button string, down bool) error {
	var f uint32
	switch strings.ToLower(button) {
	case "left":
		f = map[bool]uint32{true: mouseeventfLeftDown, false: mouseeventfLeftUp}[down]
	case "right":
		f = map[bool]uint32{true: mouseeventfRightDown, false: mouseeventfRightUp}[down]
	case "middle":
		f = map[bool]uint32{true: mouseeventfMiddleDown, false: mouseeventfMiddleUp}[down]
	default:
		return fmt.Errorf("unknown button %q", button)
	}
	return sendMouse(mouseInput{Type: inputMouse, Flags: f})
}

func (w *windowsInjector) Scroll(dx, dy float64) error {
	// Windows wants wheel deltas in 1/120 notch; positive = scroll up, so invert dy.
	var in []mouseInput
	if dy != 0 {
		in = append(in, mouseInput{Type: inputMouse, Flags: mouseeventfWheel, MouseData: uint32(int32(-dy * wheelDelta))})
	}
	if dx != 0 {
		in = append(in, mouseInput{Type: inputMouse, Flags: mouseeventfHWheel, MouseData: uint32(int32(dx * wheelDelta))})
	}
	return sendMouse(in...)
}

func vkFor(key string) (uint16, bool) {
	k := NormalizeKey(key)
	if k == "" {
		return 0, false
	}
	if len(k) == 1 { // 'A'-'Z' and '0'-'9' share their ASCII codes with virtual keys
		return uint16(k[0]), true
	}
	vk, ok := vkByName[k]
	return vk, ok
}

func (w *windowsInjector) Key(key string, mods []string) error {
	vk, ok := vkFor(key)
	if !ok {
		return fmt.Errorf("key %q: %w", key, ErrUnsupported)
	}
	var seq []keybdInput
	for _, m := range vkMods {
		if HasMod(mods, m.name) {
			seq = append(seq, keybdInput{Type: inputKeyboard, Vk: m.vk})
		}
	}
	seq = append(seq,
		keybdInput{Type: inputKeyboard, Vk: vk},
		keybdInput{Type: inputKeyboard, Vk: vk, Flags: keyeventfKeyUp},
	)
	for i := len(vkMods) - 1; i >= 0; i-- {
		if HasMod(mods, vkMods[i].name) {
			seq = append(seq, keybdInput{Type: inputKeyboard, Vk: vkMods[i].vk, Flags: keyeventfKeyUp})
		}
	}
	return sendKeys(seq...)
}

func (w *windowsInjector) Text(text string) error {
	var seq []keybdInput
	for _, r := range text {
		if r == '\n' {
			seq = append(seq, keybdInput{Type: inputKeyboard, Vk: 0x0D}, keybdInput{Type: inputKeyboard, Vk: 0x0D, Flags: keyeventfKeyUp})
			continue
		}
		for _, u := range utf16.Encode([]rune{r}) {
			seq = append(seq,
				keybdInput{Type: inputKeyboard, Scan: u, Flags: keyeventfUnicode},
				keybdInput{Type: inputKeyboard, Scan: u, Flags: keyeventfUnicode | keyeventfKeyUp},
			)
		}
	}
	return sendKeys(seq...)
}

func (w *windowsInjector) Close() error { return nil }
