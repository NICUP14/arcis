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

	logger := log.New(os.Stdout, "", log.LstdFlags)
	host, _ := os.Hostname()

	cert, err := certs.LoadOrCreate(*configDir)
	if err != nil {
		logger.Fatalf("certificate: %v", err)
	}
	store, err := pairing.Open(*configDir)
	if err != nil {
		logger.Fatalf("paired phones: %v", err)
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
		logger.Fatalf("listen on port %d: %v", *port, err)
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

	go func() {
		sig := make(chan os.Signal, 1)
		signal.Notify(sig, os.Interrupt, syscall.SIGTERM)
		<-sig
		ln.Close()
	}()
	if err := srv.Serve(ln); err != nil {
		logger.Fatal(err)
	}
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
