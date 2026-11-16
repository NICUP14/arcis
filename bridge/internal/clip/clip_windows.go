//go:build windows

package clip

import (
	"errors"
	"syscall"
	"time"
	"unsafe"
)

// Windows clipboard through user32/kernel32 (no cgo).

var (
	user32             = syscall.NewLazyDLL("user32.dll")
	kernel32           = syscall.NewLazyDLL("kernel32.dll")
	procOpenClipboard  = user32.NewProc("OpenClipboard")
	procCloseClipboard = user32.NewProc("CloseClipboard")
	procEmptyClipboard = user32.NewProc("EmptyClipboard")
	procGetData        = user32.NewProc("GetClipboardData")
	procSetData        = user32.NewProc("SetClipboardData")
	procGlobalAlloc    = kernel32.NewProc("GlobalAlloc")
	procGlobalLock     = kernel32.NewProc("GlobalLock")
	procGlobalUnlock   = kernel32.NewProc("GlobalUnlock")
	procGlobalFree     = kernel32.NewProc("GlobalFree")
	procLstrlenW       = kernel32.NewProc("lstrlenW")
)

const (
	cfUnicodeText = 13
	gmemMoveable  = 0x0002
)

// ptr turns a memory address returned by a Windows API call (GlobalLock) into a pointer.
// The memory is owned by Windows, not the Go heap, so this is safe.
func ptr(addr uintptr) unsafe.Pointer { return *(*unsafe.Pointer)(unsafe.Pointer(&addr)) }

func open() error {
	// Another program may hold the clipboard for a moment: retry briefly.
	for i := 0; i < 10; i++ {
		if r, _, _ := procOpenClipboard.Call(0); r != 0 {
			return nil
		}
		time.Sleep(20 * time.Millisecond)
	}
	return errors.New("clipboard busy")
}

// Get returns the clipboard text ("" if it holds no text).
func Get() (string, error) {
	if err := open(); err != nil {
		return "", err
	}
	defer procCloseClipboard.Call()
	h, _, _ := procGetData.Call(cfUnicodeText)
	if h == 0 {
		return "", nil
	}
	p, _, _ := procGlobalLock.Call(h)
	if p == 0 {
		return "", errors.New("GlobalLock failed")
	}
	defer procGlobalUnlock.Call(h)
	n, _, _ := procLstrlenW.Call(p)
	return syscall.UTF16ToString(unsafe.Slice((*uint16)(ptr(p)), n)), nil
}

// Set replaces the clipboard with text.
func Set(text string) error {
	u, err := syscall.UTF16FromString(text)
	if err != nil {
		return err
	}
	size := uintptr(len(u) * 2)
	h, _, _ := procGlobalAlloc.Call(gmemMoveable, size)
	if h == 0 {
		return errors.New("GlobalAlloc failed")
	}
	p, _, _ := procGlobalLock.Call(h)
	if p == 0 {
		procGlobalFree.Call(h)
		return errors.New("GlobalLock failed")
	}
	copy(unsafe.Slice((*uint16)(ptr(p)), len(u)), u)
	procGlobalUnlock.Call(h)
	if err := open(); err != nil {
		procGlobalFree.Call(h)
		return err
	}
	defer procCloseClipboard.Call()
	procEmptyClipboard.Call()
	if r, _, _ := procSetData.Call(cfUnicodeText, h); r == 0 {
		procGlobalFree.Call(h)
		return errors.New("SetClipboardData failed")
	}
	return nil // the system owns h now
}
