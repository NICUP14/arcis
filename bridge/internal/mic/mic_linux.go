//go:build linux

package mic

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
)

// Linux: a PulseAudio/PipeWire "pipe source" named "Arcis Mic"; apps pick it as their
// microphone. Needs pactl (pulseaudio-utils / pipewire-pulse).

type pipeSink struct {
	f      *os.File
	path   string
	module string
}

// Open creates the virtual microphone. device is ignored on Linux.
func Open(device string) (Sink, error) {
	if _, err := exec.LookPath("pactl"); err != nil {
		return nil, fmt.Errorf("pactl not found (install pulseaudio-utils or pipewire-pulse) for the phone microphone")
	}
	dir, err := os.MkdirTemp("", "arcis-mic")
	if err != nil {
		return nil, err
	}
	path := filepath.Join(dir, "mic.fifo")
	if err := syscall.Mkfifo(path, 0o600); err != nil {
		return nil, err
	}
	out, err := exec.Command("pactl", "load-module", "module-pipe-source",
		"source_name=arcis_mic", "file="+path, "format=s16le", "rate=16000", "channels=1",
		"source_properties=device.description=Arcis_Mic").Output()
	if err != nil {
		return nil, fmt.Errorf("pactl load-module module-pipe-source: %w", err)
	}
	// Opening the FIFO for writing blocks until the source opens it for reading (done by load-module).
	f, err := os.OpenFile(path, os.O_WRONLY, 0)
	if err != nil {
		return nil, err
	}
	return &pipeSink{f: f, path: path, module: strings.TrimSpace(string(out))}, nil
}

func (p *pipeSink) Name() string { return "Arcis Mic (PulseAudio/PipeWire source)" }

func (p *pipeSink) Write(pcm []byte) error {
	_, err := p.f.Write(pcm)
	return err
}

func (p *pipeSink) Close() error {
	p.f.Close()
	if p.module != "" {
		_ = exec.Command("pactl", "unload-module", p.module).Run()
	}
	_ = os.RemoveAll(filepath.Dir(p.path))
	return nil
}
