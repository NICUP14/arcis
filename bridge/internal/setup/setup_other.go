//go:build !windows

package setup

import "errors"

var errUnsupported = errors.New("install it with your system's package manager")

// Detect reports what it can (nothing outside Windows).
func Detect() State { return State{} }

// InstallSunshine is Windows only.
func InstallSunshine() error { return errUnsupported }

// CreateSunshineLogin is Windows only.
func CreateSunshineLogin() (user, pass string, err error) { return "", "", errUnsupported }

// InstallVBCable is Windows only.
func InstallVBCable() error { return errUnsupported }
