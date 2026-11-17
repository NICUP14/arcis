//go:build windows

package setup

import (
	"archive/zip"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"syscall"
	"time"

	"github.com/NICUP14/arcis/bridge/internal/mic"
)

const sunshineService = "SunshineService"

func sunshineDir() string {
	pf := os.Getenv("ProgramFiles")
	if pf == "" {
		pf = `C:\Program Files`
	}
	return filepath.Join(pf, "Sunshine")
}

// Detect checks Sunshine and VB-Cable.
func Detect() State {
	s := State{Supported: true}
	dir := sunshineDir()
	if _, err := os.Stat(filepath.Join(dir, "sunshine.exe")); err == nil {
		s.SunshineInstalled = true
		var st struct {
			Username string `json:"username"`
		}
		if b, err := os.ReadFile(filepath.Join(dir, "config", "sunshine_state.json")); err == nil {
			_ = json.Unmarshal(b, &st)
		}
		s.SunshineHasLogin = st.Username != ""
	}
	for _, d := range mic.DeviceNames() {
		if strings.Contains(strings.ToLower(d), "cable input") {
			s.MicReady = true
		}
	}
	return s
}

// InstallSunshine starts installing Sunshine with winget in its own window (progress
// and Windows' admin prompt show there) and returns at once.
func InstallSunshine() error {
	if _, err := exec.LookPath("winget"); err != nil {
		return errors.New("winget isn't available; download Sunshine from https://github.com/LizardByte/Sunshine/releases")
	}
	cmd := exec.Command("cmd", "/c", "start", "Installing Sunshine", "cmd", "/c",
		"winget install --id LizardByte.Sunshine -e --accept-source-agreements --accept-package-agreements & timeout /t 8")
	return cmd.Start()
}

// CreateSunshineLogin gives a new Sunshine (no login yet) a login for the bridge with a
// random password, then restarts Sunshine so it takes effect. Windows asks for admin
// rights once. Returns the login to store in the bridge.
func CreateSunshineLogin() (user, pass string, err error) {
	exe := filepath.Join(sunshineDir(), "sunshine.exe")
	if _, err := os.Stat(exe); err != nil {
		return "", "", errors.New("Sunshine isn't installed")
	}
	pass, err = randomPassword()
	if err != nil {
		return "", "", err
	}
	// A script run as admin: set the login, restart the service. Deleted right after.
	dir, err := os.MkdirTemp("", "arcis-setup")
	if err != nil {
		return "", "", err
	}
	defer os.RemoveAll(dir)
	script := filepath.Join(dir, "sunshine-login.cmd")
	body := fmt.Sprintf("@echo off\r\n\"%s\" --creds %s %s\r\nnet stop %s\r\nnet start %s\r\n",
		exe, SunshineUser, pass, sunshineService, sunshineService)
	if err := os.WriteFile(script, []byte(body), 0o600); err != nil {
		return "", "", err
	}
	if err := runElevated(script, "", true); err != nil {
		return "", "", err
	}
	return SunshineUser, pass, nil
}

// vbCablePacks are VB-Audio's driver packs, newest first.
var vbCablePacks = []string{
	"https://download.vb-audio.com/Download_CABLE/VBCABLE_Driver_Pack46.zip",
	"https://download.vb-audio.com/Download_CABLE/VBCABLE_Driver_Pack45.zip",
}

// InstallVBCable downloads VB-Audio's official driver pack and starts its installer
// (admin prompt, then its own "Install Driver" button). Returns once it started.
func InstallVBCable() error {
	dir, err := os.MkdirTemp("", "arcis-vbcable")
	if err != nil {
		return err
	}
	var zipPath string
	for _, u := range vbCablePacks {
		p := filepath.Join(dir, "pack.zip")
		if err := download(u, p); err == nil {
			zipPath = p
			break
		}
	}
	if zipPath == "" {
		return errors.New("couldn't download VB-Cable; get it from https://vb-audio.com/Cable/")
	}
	setupName := "VBCABLE_Setup_x64.exe"
	if runtime.GOARCH == "arm64" {
		setupName = "VBCABLE_Setup_ARM64.exe"
	}
	if err := unzip(zipPath, dir); err != nil {
		return err
	}
	setupExe := filepath.Join(dir, setupName)
	if _, err := os.Stat(setupExe); err != nil {
		return fmt.Errorf("the VB-Cable pack has no %s", setupName)
	}
	return runElevated(setupExe, "", false)
}

func download(url, path string) error {
	c := &http.Client{Timeout: 2 * time.Minute}
	resp, err := c.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("%s: %s", url, resp.Status)
	}
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	_, err = io.Copy(f, io.LimitReader(resp.Body, 64<<20))
	if cerr := f.Close(); err == nil {
		err = cerr
	}
	return err
}

// unzip extracts the archive's top-level files into dir (the pack has no folders).
func unzip(zipPath, dir string) error {
	r, err := zip.OpenReader(zipPath)
	if err != nil {
		return err
	}
	defer r.Close()
	for _, f := range r.File {
		name := filepath.Base(f.Name)
		if f.FileInfo().IsDir() || name != f.Name || name == "." || name == ".." {
			continue // only plain top-level files
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		out, err := os.Create(filepath.Join(dir, name))
		if err != nil {
			rc.Close()
			return err
		}
		_, err = io.Copy(out, io.LimitReader(rc, 64<<20))
		rc.Close()
		if cerr := out.Close(); err == nil {
			err = cerr
		}
		if err != nil {
			return err
		}
	}
	return nil
}

// runElevated starts file as administrator (Windows asks for consent), optionally
// waiting for it to finish. Fails if the user declines.
func runElevated(file, args string, wait bool) error {
	ps := fmt.Sprintf("Start-Process -FilePath '%s' -Verb RunAs -WindowStyle Hidden", strings.ReplaceAll(file, "'", "''"))
	if args != "" {
		ps += fmt.Sprintf(" -ArgumentList '%s'", strings.ReplaceAll(args, "'", "''"))
	}
	if wait {
		ps += " -Wait"
	}
	cmd := exec.Command("powershell", "-NoProfile", "-NonInteractive", "-Command", ps)
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true}
	if out, err := cmd.CombinedOutput(); err != nil {
		if strings.Contains(string(out), "canceled") || strings.Contains(string(out), "cancelled") {
			return errors.New("Windows' admin prompt was declined")
		}
		return fmt.Errorf("%v: %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}
