//go:build windows

package input

import (
	"os"
	"syscall"
	"testing"
	"time"
	"unsafe"
)

// Real SendInput test: moves the mouse pointer a little and back. Opt-in, because it
// touches the real pointer:  ARCIS_E2E=1 go test ./internal/input -run RealMove
func TestRealMoveWindows(t *testing.T) {
	if os.Getenv("ARCIS_E2E") != "1" {
		t.Skip("set ARCIS_E2E=1 to move the real pointer")
	}
	getCursorPos := syscall.NewLazyDLL("user32.dll").NewProc("GetCursorPos")
	pos := func() (int32, int32) {
		var p struct{ X, Y int32 }
		getCursorPos.Call(uintptr(unsafe.Pointer(&p)))
		return p.X, p.Y
	}
	inj, err := New()
	if err != nil {
		t.Fatal(err)
	}
	x0, y0 := pos()
	if err := inj.Move(40, 30); err != nil {
		t.Fatal(err)
	}
	time.Sleep(50 * time.Millisecond)
	x1, y1 := pos()
	if err := inj.Move(-40, -30); err != nil {
		t.Fatal(err)
	}
	t.Logf("pointer moved from (%d,%d) to (%d,%d)", x0, y0, x1, y1)
	// Windows pointer acceleration may scale relative moves; direction must be right.
	if x1 <= x0 || y1 <= y0 {
		t.Fatalf("pointer did not move right/down: (%d,%d) -> (%d,%d)", x0, y0, x1, y1)
	}
}
