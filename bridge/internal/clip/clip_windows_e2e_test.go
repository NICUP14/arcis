//go:build windows

package clip

import (
	"os"
	"testing"
)

// Real clipboard round trip. Opt-in (ARCIS_E2E=1); restores the previous text afterwards
// and skips if the clipboard holds something other than text (so nothing is lost).
func TestRealClipboardWindows(t *testing.T) {
	if os.Getenv("ARCIS_E2E") != "1" {
		t.Skip("set ARCIS_E2E=1 to use the real clipboard")
	}
	before, err := Get()
	if err != nil {
		t.Fatal(err)
	}
	if before == "" {
		t.Skip("clipboard holds no text; not touching it")
	}
	defer Set(before)
	const probe = "Arcis clipboard test ✓ ünïcödé"
	if err := Set(probe); err != nil {
		t.Fatal(err)
	}
	got, err := Get()
	if err != nil {
		t.Fatal(err)
	}
	if got != probe {
		t.Fatalf("got %q", got)
	}
}
