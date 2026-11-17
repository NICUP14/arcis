// Package tray is the bridge's desktop presence: a notification-area icon with a small
// menu on Windows, nothing on other systems (they keep running in a terminal).
package tray

import (
	"bytes"
	"encoding/binary"
	"encoding/json"
	"image"
	"image/color"
	"image/png"
	"math"
	"os"
	"path/filepath"
)

// Options configures the tray.
type Options struct {
	// Status reports whether a phone is connected, and its name.
	Status func() (connected bool, phone string)
	// StatusURL is the local status page (paired phones, addresses).
	StatusURL string
	// PairURL is the page with the pairing code.
	PairURL string
	// LogFile is opened by "Open log". May be empty.
	LogFile string
	// ConfigDir holds settings.json.
	ConfigDir string
	// OnQuit is called (once) when the user picks Quit, before Run returns.
	OnQuit func()
}

// Settings are the bridge's own preferences, kept in settings.json.
type Settings struct {
	// Autostart: start when the user signs in. nil = never chosen (default on).
	Autostart *bool `json:"autostart,omitempty"`
	// Welcomed is set once the first run has shown the pairing page.
	Welcomed bool `json:"welcomed,omitempty"`
}

func settingsPath(dir string) string { return filepath.Join(dir, "settings.json") }

// LoadSettings reads settings.json (missing = defaults).
func LoadSettings(dir string) Settings {
	var s Settings
	if b, err := os.ReadFile(settingsPath(dir)); err == nil {
		_ = json.Unmarshal(b, &s)
	}
	return s
}

// SaveSettings writes settings.json.
func SaveSettings(dir string, s Settings) error {
	b, err := json.MarshalIndent(s, "", "  ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	return os.WriteFile(settingsPath(dir), b, 0o600)
}

// AutostartWanted is the effective choice: on unless turned off.
func (s Settings) AutostartWanted() bool { return s.Autostart == nil || *s.Autostart }

// icon draws the tray icon (a ring around a dot) in c and returns it as a .ico file.
func icon(c color.RGBA) []byte {
	const n = 32
	img := image.NewRGBA(image.Rect(0, 0, n, n))
	for y := 0; y < n; y++ {
		for x := 0; x < n; x++ {
			d := math.Hypot(float64(x)-15.5, float64(y)-15.5)
			// Coverage of the dot (r 6.5) and the ring (9.5..15), with 1 px soft edges.
			a := math.Max(edge(6.5-d), math.Min(edge(d-9.5), edge(15-d)))
			if a > 0 {
				img.SetRGBA(x, y, color.RGBA{
					uint8(float64(c.R) * a), uint8(float64(c.G) * a), uint8(float64(c.B) * a), uint8(255 * a),
				})
			}
		}
	}
	var p bytes.Buffer
	_ = png.Encode(&p, img)
	// An .ico container holding one PNG image (supported since Windows Vista).
	var out bytes.Buffer
	_ = binary.Write(&out, binary.LittleEndian, []uint16{0, 1, 1}) // reserved, type icon, 1 image
	out.Write([]byte{n, n, 0, 0})                                  // width, height, palette, reserved
	_ = binary.Write(&out, binary.LittleEndian, []uint16{1, 32})   // planes, bits per pixel
	_ = binary.Write(&out, binary.LittleEndian, []uint32{uint32(p.Len()), 22})
	out.Write(p.Bytes())
	return out.Bytes()
}

// edge maps a signed distance inside a shape to coverage 0..1 (1 px antialiasing).
func edge(inside float64) float64 { return math.Max(0, math.Min(1, inside+0.5)) }

var (
	iconIdle      = icon(color.RGBA{0x8a, 0x90, 0xa0, 0xff})
	iconConnected = icon(color.RGBA{0x4c, 0xd9, 0x8a, 0xff})
)
