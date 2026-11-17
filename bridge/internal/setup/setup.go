// Package setup checks and installs what the bridge relies on besides itself:
// Sunshine (picture and sound for Moonlight), a Sunshine login the bridge can use to
// pair Moonlight, and VB-Cable (the phone as a microphone). One-click installs exist
// on Windows only; other systems just report what they can.
package setup

import (
	"crypto/rand"
	"math/big"
)

// State is what is installed.
type State struct {
	// Supported: this system has the one-click actions below.
	Supported bool
	// SunshineInstalled: Sunshine is on this computer.
	SunshineInstalled bool
	// SunshineHasLogin: Sunshine's web UI already has a username/password.
	SunshineHasLogin bool
	// MicReady: the virtual microphone driver (VB-Cable) is installed.
	MicReady bool
}

// SunshineUser is the Sunshine login the bridge creates on a new Sunshine.
const SunshineUser = "arcis"

// randomPassword is a long random password (letters and digits only, so it survives
// command lines unquoted).
func randomPassword() (string, error) {
	const chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	b := make([]byte, 24)
	for i := range b {
		n, err := rand.Int(rand.Reader, big.NewInt(int64(len(chars))))
		if err != nil {
			return "", err
		}
		b[i] = chars[n.Int64()]
	}
	return string(b), nil
}
