package tray

import (
	"bytes"
	"image/png"
	"testing"
)

func TestIconIsAValidIco(t *testing.T) {
	ico := iconConnected
	if len(ico) < 22 || !bytes.Equal(ico[:6], []byte{0, 0, 1, 0, 1, 0}) {
		t.Fatalf("bad ico header % x", ico[:6])
	}
	img, err := png.Decode(bytes.NewReader(ico[22:]))
	if err != nil {
		t.Fatal(err)
	}
	if b := img.Bounds(); b.Dx() != 32 || b.Dy() != 32 {
		t.Fatalf("size %v", b)
	}
	if _, _, _, a := img.At(16, 16).RGBA(); a == 0 {
		t.Fatal("centre dot is transparent")
	}
	if _, _, _, a := img.At(0, 0).RGBA(); a != 0 {
		t.Fatal("corner is not transparent")
	}
}

func TestSettingsRoundTripAndAutostartDefault(t *testing.T) {
	dir := t.TempDir()
	s := LoadSettings(dir)
	if !s.AutostartWanted() {
		t.Fatal("autostart should default to on")
	}
	off := false
	s.Autostart, s.Welcomed = &off, true
	if err := SaveSettings(dir, s); err != nil {
		t.Fatal(err)
	}
	got := LoadSettings(dir)
	if got.AutostartWanted() || !got.Welcomed {
		t.Fatalf("got %+v", got)
	}
}
