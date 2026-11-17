// Command arcis-bridge runs on a computer and performs the input the Arcis phone app
// sends it (pointer, clicks, scrolling, keys, text). Video comes from Moonlight +
// Sunshine/Apollo; the bridge only handles controls.
package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"github.com/NICUP14/arcis/bridge/internal/certs"
	"github.com/NICUP14/arcis/bridge/internal/clip"
	"github.com/NICUP14/arcis/bridge/internal/discovery"
	"github.com/NICUP14/arcis/bridge/internal/input"
	"github.com/NICUP14/arcis/bridge/internal/mic"
	"github.com/NICUP14/arcis/bridge/internal/pairing"
	"github.com/NICUP14/arcis/bridge/internal/proto"
	"github.com/NICUP14/arcis/bridge/internal/server"
	"github.com/NICUP14/arcis/bridge/internal/status"
	"github.com/NICUP14/arcis/bridge/internal/tray"
	"github.com/NICUP14/arcis/bridge/internal/wake"
)

// Version is the bridge version, overridable at build time with -ldflags "-X main.Version=...".
var Version = "0.1.0"

func main() {
	showVersion := flag.Bool("version", false, "print version and exit")
	port := flag.Int("port", proto.DefaultPort, "TCP port for phones")
	bind := flag.String("bind", "", "listen only on this address (e.g. a Tailscale IP or 127.0.0.1); default all")
	statusPort := flag.Int("status-port", proto.StatusPort, "localhost port of the status page (0 = off)")
	noMDNS := flag.Bool("no-mdns", false, "don't announce on the local network")
	noClip := flag.Bool("no-clipboard", false, "don't share the clipboard with phones")
	noMic := flag.Bool("no-mic", false, "don't use the phone as a microphone")
	micDevice := flag.String("mic-device", "", "Windows: output device that feeds the virtual mic (default: VB-Audio \"CABLE Input\")")
	configDir := flag.String("config", defaultConfigDir(), "where the certificate and paired phones are stored")
	flag.Parse()
	if *showVersion {
		fmt.Println("arcis-bridge", Version)
		return
	}

	statusURL := fmt.Sprintf("http://127.0.0.1:%d/", *statusPort)
	pairURL := statusURL
	if tray.AlreadyRunning() {
		// Started again (Start Menu, sign-in, double-click): just show the running one.
		tray.OpenURL(statusURL)
		return
	}

	logFile := openLog(*configDir)
	logger := log.New(logWriter{logFile}, "", log.LstdFlags)
	fatal := func(format string, args ...any) {
		msg := fmt.Sprintf(format, args...)
		logger.Print(msg)
		tray.Alert("Arcis Bridge", msg)
		os.Exit(1)
	}
	host, _ := os.Hostname()

	cert, err := certs.LoadOrCreate(*configDir)
	if err != nil {
		fatal("certificate: %v", err)
	}
	store, err := pairing.Open(*configDir)
	if err != nil {
		fatal("paired phones: %v", err)
	}
	inj, inputErr := input.New()
	if inputErr != nil {
		logger.Printf("WARNING: input unavailable: %v", inputErr)
	} else {
		defer inj.Close()
	}

	srv := &server.Server{
		Store: store, Input: inj, Cert: cert, HostName: host, Log: logger,
		Addresses: status.LocalAddresses,
		MACs:      wake.MACs,
		Clipboard: clipboard{},
		Wake:      wake.Send,
	}
	if !*noMic {
		srv.OpenMic = func() (mic.Sink, error) { return mic.Open(*micDevice) }
		defer srv.CloseMic()
	}
	if !*noClip {
		ctx, stop := context.WithCancel(context.Background())
		defer stop()
		go clip.Watch(ctx, 700*time.Millisecond, srv.ClipboardSetByPhone, func(text string) {
			srv.Broadcast(proto.Message{T: proto.TypeClip, Text: text})
		})
	}
	ln, err := net.Listen("tcp", net.JoinHostPort(*bind, fmt.Sprint(*port)))
	if err != nil {
		fatal("Can't listen on port %d (is another Arcis Bridge already running?): %v", *port, err)
	}
	fp := certs.Fingerprint(cert)

	if !*noMDNS {
		if a, err := discovery.Announce(host, *port, fp); err != nil {
			logger.Printf("mDNS announce failed (phones can still add this computer by address): %v", err)
		} else {
			defer a.Stop()
		}
	}

	if *statusPort != 0 {
		go func() {
			err := status.Serve(*statusPort, func() status.Info {
				info := status.Info{
					Host: host, Version: Version, Port: *port, PIN: store.PIN(), Fingerprint: fp,
					Addresses: status.LocalAddresses(), Connected: srv.Connected(), LastPhone: srv.LastPhone(),
				}
				if inputErr != nil {
					info.InputError = inputErr.Error()
				}
				for _, d := range store.Devices() {
					seen := "never connected"
					if !d.LastSeen.IsZero() {
						seen = "last seen " + d.LastSeen.Format(time.DateTime)
					}
					info.Devices = append(info.Devices, status.Device{Name: d.Name, LastSeen: seen})
				}
				return info
			})
			if err != nil {
				logger.Printf("status page unavailable: %v", err)
			}
		}()
	}

	logger.Printf("Arcis Bridge %s on %s, port %d", Version, host, *port)
	logger.Printf("Pairing PIN: %s   (status page: http://127.0.0.1:%d)", store.PIN(), *statusPort)
	logger.Printf("Addresses: %v", status.LocalAddresses())

	// Start with Windows unless the user turned it off (re-written each start so it
	// follows the exe if it moved); show the pairing page on the very first run.
	settings := tray.LoadSettings(*configDir)
	if settings.AutostartWanted() {
		if err := tray.ApplyAutostart(true); err != nil {
			logger.Printf("start with Windows: %v", err)
		}
	}
	if !settings.Welcomed {
		settings.Welcomed = true
		_ = tray.SaveSettings(*configDir, settings)
		if *statusPort != 0 && len(store.Devices()) == 0 {
			tray.OpenURL(pairURL)
		}
	}

	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
		<-sig
		tray.Quit()
	}()
	go func() {
		if err := srv.Serve(ln); err != nil {
			logger.Printf("stopped: %v", err)
			tray.Alert("Arcis Bridge", "The bridge stopped: "+err.Error())
		}
		tray.Quit()
	}()
	trayOpts := tray.Options{
		Status:    func() (bool, string) { return srv.Connected() > 0, srv.LastPhone() },
		ConfigDir: *configDir,
		OnQuit:    func() { logger.Print("quit from the tray") },
	}
	if *statusPort != 0 {
		trayOpts.StatusURL, trayOpts.PairURL = statusURL, pairURL
	}
	if logFile != nil {
		trayOpts.LogFile = logFile.Name()
	}
	tray.Run(trayOpts) // blocks until Quit (menu, signal, or the server stopping)
	ln.Close()
}

// openLog opens <config>/bridge.log for appending, keeping one older copy once it
// passes about 1 MB. nil if it can't be opened (then only stdout is used).
func openLog(dir string) *os.File {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return nil
	}
	path := filepath.Join(dir, "bridge.log")
	if fi, err := os.Stat(path); err == nil && fi.Size() > 1<<20 {
		_ = os.Rename(path, path+".1")
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o600)
	if err != nil {
		return nil
	}
	return f
}

// logWriter writes to the log file and to stdout, ignoring a missing stdout (the
// Windows build has no console).
type logWriter struct{ f *os.File }

func (w logWriter) Write(p []byte) (int, error) {
	if w.f != nil {
		_, _ = w.f.Write(p)
	}
	_, _ = os.Stdout.Write(p)
	return len(p), nil
}

// clipboard adapts the clip package to server.Clipboard.
type clipboard struct{}

func (clipboard) Get() (string, error) { return clip.Get() }
func (clipboard) Set(t string) error   { return clip.Set(t) }

func defaultConfigDir() string {
	if d, err := os.UserConfigDir(); err == nil {
		return filepath.Join(d, "arcis-bridge")
	}
	return ".arcis-bridge"
}
