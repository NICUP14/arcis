//go:build windows

package mic

import (
	"os"
	"testing"
	"time"
)

// Real waveOut test: lists output devices and plays 300 ms of silence on the default
// device (inaudible). Opt-in: ARCIS_E2E=1.
func TestRealWaveOutWindows(t *testing.T) {
	if os.Getenv("ARCIS_E2E") != "1" {
		t.Skip("set ARCIS_E2E=1 to use the real audio output")
	}
	t.Logf("audio outputs: %q", DeviceNames())
	sink, err := openDevice(waveMapper, "default")
	if err != nil {
		t.Fatal(err)
	}
	defer sink.Close()
	silence := make([]byte, 640) // 20 ms
	for i := 0; i < 15; i++ {
		if err := sink.Write(silence); err != nil {
			t.Fatal(err)
		}
	}
	time.Sleep(400 * time.Millisecond)
	if _, err := Open("no such device xyz"); err == nil {
		t.Fatal("expected an error for a missing device")
	}
}
