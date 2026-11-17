//go:build windows

package tray

import (
	"fmt"
	"os"
	"os/exec"
	"sync"
	"time"

	"fyne.io/systray"
	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
)

const (
	runKey   = `Software\Microsoft\Windows\CurrentVersion\Run`
	runValue = "ArcisBridge"
)

var quitOnce sync.Once

// Run shows the notification-area icon and blocks until Quit (or the menu's Quit).
func Run(o Options) {
	systray.Run(func() { onReady(o) }, func() {})
}

// Quit removes the icon and makes Run return.
func Quit() { quitOnce.Do(systray.Quit) }

func onReady(o Options) {
	systray.SetIcon(iconIdle)
	systray.SetTitle("Arcis Bridge")
	systray.SetTooltip("Arcis Bridge: waiting for a phone")

	state := systray.AddMenuItem("Waiting for a phone", "")
	state.Disable()
	systray.AddSeparator()
	pair := systray.AddMenuItem("Pair a phone…", "Show the pairing code")
	status := systray.AddMenuItem("Status page", "Paired phones and addresses")
	logItem := systray.AddMenuItem("Open log", "")
	if o.LogFile == "" {
		logItem.Hide()
	}
	systray.AddSeparator()
	auto := systray.AddMenuItemCheckbox("Start with Windows", "Start the bridge when you sign in",
		LoadSettings(o.ConfigDir).AutostartWanted())
	systray.AddSeparator()
	quitItem := systray.AddMenuItem("Quit", "Stop the bridge")

	go func() {
		last := ""
		for {
			connected, phone := false, ""
			if o.Status != nil {
				connected, phone = o.Status()
			}
			label := "Waiting for a phone"
			if connected {
				label = "Connected: " + phone
			}
			if label != last {
				last = label
				state.SetTitle(label)
				systray.SetTooltip("Arcis Bridge: " + label)
				if connected {
					systray.SetIcon(iconConnected)
				} else {
					systray.SetIcon(iconIdle)
				}
			}
			time.Sleep(time.Second)
		}
	}()

	go func() {
		for {
			select {
			case <-pair.ClickedCh:
				OpenURL(o.PairURL)
			case <-status.ClickedCh:
				OpenURL(o.StatusURL)
			case <-logItem.ClickedCh:
				_ = exec.Command("notepad.exe", o.LogFile).Start()
			case <-auto.ClickedCh:
				on := !auto.Checked()
				if err := ApplyAutostart(on); err != nil {
					Alert("Arcis Bridge", "Couldn't change starting with Windows: "+err.Error())
					continue
				}
				if on {
					auto.Check()
				} else {
					auto.Uncheck()
				}
				s := LoadSettings(o.ConfigDir)
				s.Autostart = &on
				_ = SaveSettings(o.ConfigDir, s)
			case <-quitItem.ClickedCh:
				if o.OnQuit != nil {
					o.OnQuit()
				}
				Quit()
				return
			}
		}
	}()
}

// instanceMutex is held for the life of the process (see AlreadyRunning).
var instanceMutex windows.Handle

// AlreadyRunning reports whether another bridge is running in this sign-in session.
// The first caller keeps a named mutex until it exits.
func AlreadyRunning() bool {
	name, _ := windows.UTF16PtrFromString(`Local\ArcisBridge`)
	h, err := windows.CreateMutex(nil, false, name)
	if err == windows.ERROR_ALREADY_EXISTS {
		if h != 0 {
			_ = windows.CloseHandle(h)
		}
		return true
	}
	instanceMutex = h
	return false
}

// Alert shows a message box (the bridge has no console window).
func Alert(title, msg string) {
	t, _ := windows.UTF16PtrFromString(title)
	m, _ := windows.UTF16PtrFromString(msg)
	_, _ = windows.MessageBox(0, m, t, windows.MB_OK|windows.MB_ICONWARNING)
}

// OpenURL opens a page in the default browser.
func OpenURL(url string) {
	if url != "" {
		_ = exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	}
}

// ApplyAutostart adds the bridge to (or removes it from) the user's sign-in programs.
func ApplyAutostart(on bool) error {
	k, _, err := registry.CreateKey(registry.CURRENT_USER, runKey, registry.SET_VALUE|registry.QUERY_VALUE)
	if err != nil {
		return err
	}
	defer k.Close()
	if !on {
		if err := k.DeleteValue(runValue); err != nil && err != registry.ErrNotExist {
			return err
		}
		return nil
	}
	exe, err := os.Executable()
	if err != nil {
		return err
	}
	return k.SetStringValue(runValue, fmt.Sprintf("%q", exe))
}
