//go:build !windows

package tray

import (
	"log"
	"sync"
)

var (
	quit     = make(chan struct{})
	quitOnce sync.Once
)

// Run blocks until Quit is called. There is no tray icon outside Windows.
func Run(o Options) { <-quit }

// Quit makes Run return.
func Quit() { quitOnce.Do(func() { close(quit) }) }

// AlreadyRunning reports whether another bridge is running for this user (Windows only).
func AlreadyRunning() bool { return false }

// Alert reports a problem to the person at the computer (here: the log).
func Alert(title, msg string) { log.Printf("%s: %s", title, msg) }

// OpenURL opens a page in the browser (not done outside Windows).
func OpenURL(string) {}

// ApplyAutostart sets whether the bridge starts at sign-in (Windows only).
func ApplyAutostart(bool) error { return nil }
