//go:build !windows && !linux && !darwin

package input

// New returns an error on systems without an input implementation.
func New() (Injector, error) { return nil, ErrUnsupported }
