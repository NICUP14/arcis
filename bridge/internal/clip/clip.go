// Package clip reads and writes the computer's text clipboard.
package clip

import (
	"context"
	"time"
)

// Watch calls onChange with the clipboard text whenever it changes (polling), until ctx ends.
// skip reports whether a value was just set by us (to avoid echoing it back).
func Watch(ctx context.Context, interval time.Duration, skip func(string) bool, onChange func(string)) {
	last, _ := Get()
	t := time.NewTicker(interval)
	defer t.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-t.C:
			cur, err := Get()
			if err != nil || cur == last {
				continue
			}
			last = cur
			if cur != "" && !skip(cur) {
				onChange(cur)
			}
		}
	}
}
