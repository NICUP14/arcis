//go:build windows

package mic

import (
	"errors"
	"fmt"
	"strings"
	"sync"
	"syscall"
	"unsafe"
)

// Windows: play into the VB-Audio Virtual Cable ("CABLE Input"); apps then select
// "CABLE Output" as their microphone. Uses winmm waveOut (no cgo).

var (
	winmm                = syscall.NewLazyDLL("winmm.dll")
	procWaveOutGetNum    = winmm.NewProc("waveOutGetNumDevs")
	procWaveOutGetCaps   = winmm.NewProc("waveOutGetDevCapsW")
	procWaveOutOpen      = winmm.NewProc("waveOutOpen")
	procWaveOutClose     = winmm.NewProc("waveOutClose")
	procWaveOutPrepare   = winmm.NewProc("waveOutPrepareHeader")
	procWaveOutUnprepare = winmm.NewProc("waveOutUnprepareHeader")
	procWaveOutWrite     = winmm.NewProc("waveOutWrite")
	procWaveOutReset     = winmm.NewProc("waveOutReset")
)

const (
	waveMapper   = 0xFFFFFFFF
	whdrDone     = 0x00000001
	waveFormatPC = 1
)

type waveFormatEx struct {
	FormatTag      uint16
	Channels       uint16
	SamplesPerSec  uint32
	AvgBytesPerSec uint32
	BlockAlign     uint16
	BitsPerSample  uint16
	Size           uint16
}

type waveOutCaps struct {
	Mid           uint16
	Pid           uint16
	DriverVersion uint32
	Pname         [32]uint16
	Formats       uint32
	Channels      uint16
	Reserved      uint16
	Support       uint32
}

type waveHdr struct {
	Data          uintptr
	BufferLength  uint32
	BytesRecorded uint32
	User          uintptr
	Flags         uint32
	Loops         uint32
	Next          uintptr
	Reserved      uintptr
}

type buffer struct {
	hdr  waveHdr
	data []byte
}

type waveSink struct {
	mu      sync.Mutex
	handle  uintptr
	name    string
	pending []*buffer // kept alive until Windows marks them done
}

// DeviceNames lists the audio output devices.
func DeviceNames() []string {
	n, _, _ := procWaveOutGetNum.Call()
	var out []string
	for i := uintptr(0); i < n; i++ {
		var caps waveOutCaps
		if r, _, _ := procWaveOutGetCaps.Call(i, uintptr(unsafe.Pointer(&caps)), unsafe.Sizeof(caps)); r == 0 {
			out = append(out, syscall.UTF16ToString(caps.Pname[:]))
		}
	}
	return out
}

// Open opens the virtual microphone sink. device selects the output by name substring;
// "" picks the VB-Audio cable.
func Open(device string) (Sink, error) {
	want := device
	if want == "" {
		want = "CABLE Input"
	}
	id := -1
	var name string
	for i, n := range DeviceNames() {
		if strings.Contains(strings.ToLower(n), strings.ToLower(want)) {
			id, name = i, n
			break
		}
	}
	if id < 0 {
		return nil, fmt.Errorf("no audio device matching %q: install VB-Audio Virtual Cable (https://vb-audio.com/Cable/) for the phone microphone", want)
	}
	return openDevice(uintptr(id), name)
}

func openDevice(id uintptr, name string) (Sink, error) {
	f := waveFormatEx{
		FormatTag: waveFormatPC, Channels: 1, SamplesPerSec: SampleRate,
		AvgBytesPerSec: SampleRate * 2, BlockAlign: 2, BitsPerSample: 16,
	}
	var h uintptr
	if r, _, _ := procWaveOutOpen.Call(uintptr(unsafe.Pointer(&h)), id, uintptr(unsafe.Pointer(&f)), 0, 0, 0); r != 0 {
		return nil, fmt.Errorf("waveOutOpen(%s) failed: %d", name, r)
	}
	return &waveSink{handle: h, name: name}, nil
}

func (w *waveSink) Name() string { return w.name }

func (w *waveSink) Write(pcm []byte) error {
	if len(pcm) == 0 {
		return nil
	}
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.handle == 0 {
		return errors.New("closed")
	}
	w.reapLocked()
	if len(w.pending) > 50 { // ~1 s queued: drop rather than build latency
		return nil
	}
	b := &buffer{data: append([]byte(nil), pcm...)}
	b.hdr.Data = uintptr(unsafe.Pointer(&b.data[0]))
	b.hdr.BufferLength = uint32(len(b.data))
	hsz := unsafe.Sizeof(b.hdr)
	if r, _, _ := procWaveOutPrepare.Call(w.handle, uintptr(unsafe.Pointer(&b.hdr)), hsz); r != 0 {
		return fmt.Errorf("waveOutPrepareHeader: %d", r)
	}
	if r, _, _ := procWaveOutWrite.Call(w.handle, uintptr(unsafe.Pointer(&b.hdr)), hsz); r != 0 {
		procWaveOutUnprepare.Call(w.handle, uintptr(unsafe.Pointer(&b.hdr)), hsz)
		return fmt.Errorf("waveOutWrite: %d", r)
	}
	w.pending = append(w.pending, b)
	return nil
}

// reapLocked releases buffers Windows has finished playing.
func (w *waveSink) reapLocked() {
	keep := w.pending[:0]
	for _, b := range w.pending {
		if b.hdr.Flags&whdrDone != 0 {
			procWaveOutUnprepare.Call(w.handle, uintptr(unsafe.Pointer(&b.hdr)), unsafe.Sizeof(b.hdr))
			continue
		}
		keep = append(keep, b)
	}
	w.pending = keep
}

func (w *waveSink) Close() error {
	w.mu.Lock()
	defer w.mu.Unlock()
	if w.handle == 0 {
		return nil
	}
	procWaveOutReset.Call(w.handle)
	for _, b := range w.pending {
		procWaveOutUnprepare.Call(w.handle, uintptr(unsafe.Pointer(&b.hdr)), unsafe.Sizeof(b.hdr))
	}
	w.pending = nil
	procWaveOutClose.Call(w.handle)
	w.handle = 0
	return nil
}
