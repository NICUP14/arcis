//go:build !windows && !linux

package mic

import "errors"

// Open reports that the phone microphone isn't available on this system yet.
func Open(device string) (Sink, error) {
	return nil, errors.New("the phone microphone isn't supported on this system yet")
}
