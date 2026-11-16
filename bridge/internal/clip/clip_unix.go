//go:build !windows

package clip

import (
	"errors"
	"os"
	"os/exec"
	"runtime"
	"strings"
)

// Linux: wl-clipboard (Wayland) or xclip (X11). macOS: pbcopy / pbpaste.

func commands() (get, set []string, err error) {
	switch {
	case runtime.GOOS == "darwin":
		return []string{"pbpaste"}, []string{"pbcopy"}, nil
	case os.Getenv("WAYLAND_DISPLAY") != "":
		return []string{"wl-paste", "--no-newline"}, []string{"wl-copy"}, nil
	default:
		if _, err := exec.LookPath("xclip"); err == nil {
			return []string{"xclip", "-selection", "clipboard", "-o"}, []string{"xclip", "-selection", "clipboard", "-i"}, nil
		}
		return nil, nil, errors.New("install wl-clipboard (Wayland) or xclip (X11) for clipboard sync")
	}
}

// Get returns the clipboard text ("" if empty).
func Get() (string, error) {
	get, _, err := commands()
	if err != nil {
		return "", err
	}
	out, err := exec.Command(get[0], get[1:]...).Output()
	if err != nil {
		return "", nil // empty clipboard makes some tools exit non-zero
	}
	return string(out), nil
}

// Set replaces the clipboard with text.
func Set(text string) error {
	_, set, err := commands()
	if err != nil {
		return err
	}
	cmd := exec.Command(set[0], set[1:]...)
	cmd.Stdin = strings.NewReader(text)
	return cmd.Run()
}
