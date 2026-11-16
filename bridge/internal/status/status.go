// Package status serves a small local web page with the pairing PIN and state.
// It listens on localhost only, so the PIN is visible to people at this computer.
package status

import (
	"fmt"
	"html/template"
	"net"
	"net/http"
	"time"
)

// Info is what the page shows.
type Info struct {
	Host        string
	Version     string
	Port        int
	PIN         string
	Fingerprint string
	Addresses   []string
	Devices     []Device
	Connected   int
	LastPhone   string
	InputError  string
}

// Device is a paired phone.
type Device struct {
	Name     string
	LastSeen string
}

var page = template.Must(template.New("status").Parse(`<!doctype html>
<html><head><meta charset="utf-8"><meta http-equiv="refresh" content="5">
<title>Arcis Bridge</title>
<style>
body{font-family:system-ui,sans-serif;background:#0d0f14;color:#e8eaf0;max-width:720px;margin:40px auto;padding:0 20px}
h1{font-weight:500}.pin{font-size:56px;letter-spacing:10px;color:#6ea8ff;margin:8px 0}
.card{background:#161922;border:1px solid #262a33;border-radius:14px;padding:18px 22px;margin:16px 0}
.dim{color:#8a90a0}.warn{color:#ffb35a}code{color:#c9d1e0}
</style></head><body>
<h1>Arcis Bridge <span class="dim">{{.Version}}</span></h1>
{{if .InputError}}<div class="card warn">Input is not available: {{.InputError}}</div>{{end}}
<div class="card">
  <div class="dim">To pair a phone, enter this PIN in Arcis:</div>
  <div class="pin">{{.PIN}}</div>
  <div class="dim">Computer: <b>{{.Host}}</b> · port {{.Port}}</div>
  <div class="dim">Addresses: {{range .Addresses}}<code>{{.}}</code> {{end}}</div>
  <div class="dim">Fingerprint: <code>{{.Fingerprint}}</code></div>
</div>
<div class="card">
  <div>{{if .Connected}}Connected: <b>{{.LastPhone}}</b>{{else}}<span class="dim">No phone connected</span>{{end}}</div>
  <div class="dim" style="margin-top:8px">Paired phones:</div>
  {{range .Devices}}<div>{{.Name}} <span class="dim">{{.LastSeen}}</span></div>{{else}}<div class="dim">none yet</div>{{end}}
</div>
</body></html>`))

// Serve runs the page on 127.0.0.1:port until the listener fails.
func Serve(port int, info func() Info) error {
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		return err
	}
	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		_ = page.Execute(w, info())
	})
	srv := &http.Server{Handler: mux, ReadHeaderTimeout: 5 * time.Second}
	return srv.Serve(ln)
}

// LocalAddresses lists this computer's non-loopback IPv4 addresses (LAN, Tailscale, ...).
func LocalAddresses() []string {
	var out []string
	ifaces, _ := net.Interfaces()
	for _, ifc := range ifaces {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			if ipn, ok := a.(*net.IPNet); ok && ipn.IP.To4() != nil && !ipn.IP.IsLinkLocalUnicast() {
				out = append(out, ipn.IP.String())
			}
		}
	}
	return out
}
