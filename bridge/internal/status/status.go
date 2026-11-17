// Package status serves a small local web page with the pairing PIN and state.
// It listens on localhost only, so the PIN is visible to people at this computer.
package status

import (
	"encoding/base64"
	"fmt"
	"html/template"
	"net"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	qrcode "github.com/skip2/go-qrcode"
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
	// Sunshine describes the stored Sunshine login (for pairing Moonlight by itself);
	// SunshineOK when Sunshine accepts it.
	Sunshine   string
	SunshineOK bool
}

// Device is a paired phone.
type Device struct {
	Name     string
	LastSeen string
}

var page = template.Must(template.New("status").Parse(`<!doctype html>
<html><head><meta charset="utf-8">
<title>Arcis Bridge</title>
<script>setInterval(function(){var a=document.activeElement;if(!a||a.tagName!=="INPUT")location.reload()},5000)</script>
<style>
body{font-family:system-ui,sans-serif;background:#0d0f14;color:#e8eaf0;max-width:720px;margin:40px auto;padding:0 20px}
h1{font-weight:500}.pin{font-size:56px;letter-spacing:10px;color:#6ea8ff;margin:8px 0}
.card{background:#161922;border:1px solid #262a33;border-radius:14px;padding:18px 22px;margin:16px 0}
.dim{color:#8a90a0}.warn{color:#ffb35a}.ok{color:#4cd98a}code{color:#c9d1e0}a{color:#6ea8ff}
input{background:#0d0f14;color:#e8eaf0;border:1px solid #262a33;border-radius:8px;padding:8px 10px;margin:4px 8px 4px 0}
button{background:#1e3a6e;color:#e8eaf0;border:0;border-radius:8px;padding:9px 16px;cursor:pointer}
</style></head><body>
<h1>Arcis Bridge <span class="dim">{{.Version}}</span></h1>
{{if .InputError}}<div class="card warn">Input is not available: {{.InputError}}</div>{{end}}
<div class="card">
  <div class="dim">To pair a phone, <a href="/pair">scan the pairing code</a> or enter this PIN in Arcis:</div>
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
<div class="card">
  <div>Moonlight pairing: <span class="{{if .SunshineOK}}ok{{else}}warn{{end}}">{{.Sunshine}}</span></div>
  <div class="dim" style="margin:6px 0 10px">With your Sunshine login (the one for its web page) the bridge enters
  Moonlight's PIN into Sunshine for you. It is stored on this computer only.</div>
  <form method="post" action="/sunshine">
    <input name="user" placeholder="Sunshine username" autocomplete="off">
    <input name="pass" type="password" placeholder="Password" autocomplete="off">
    <button>Save</button>
  </form>
</div>
</body></html>`))

var pairPage = template.Must(template.New("pair").Parse(`<!doctype html>
<html><head><meta charset="utf-8"><meta http-equiv="refresh" content="10">
<title>Pair a phone · Arcis Bridge</title>
<style>
body{font-family:system-ui,sans-serif;background:#0d0f14;color:#e8eaf0;max-width:720px;margin:40px auto;padding:0 20px;text-align:center}
h1{font-weight:500}.dim{color:#8a90a0}a{color:#6ea8ff}
.qr{background:#fff;border-radius:18px;padding:18px;display:inline-block;margin:18px 0}
.pin{font-size:34px;letter-spacing:8px;color:#6ea8ff}
</style></head><body>
<h1>Pair a phone with {{.Host}}</h1>
<div class="dim">In Arcis on the glasses pick <b>Add computer</b> → <b>Scan code with the phone</b>,<br>
or point the phone's camera at the code.</div>
<div class="qr"><img src="{{.QR}}" width="300" height="300" alt="Pairing code"></div>
<div class="dim">No camera? Enter this PIN in Arcis instead:</div>
<div class="pin">{{.PIN}}</div>
<p class="dim"><a href="/">Status page</a></p>
</body></html>`))

// PairLink is what the pairing QR code holds: everything a phone needs to find this
// computer (all its addresses, e.g. LAN and Tailscale), check its identity and pair.
//
//	arcis://pair?a=192.168.1.5,100.64.1.2&fp=<sha256 hex>&h=<name>&p=41990&pin=123456
func PairLink(i Info) string {
	v := url.Values{}
	v.Set("h", i.Host)
	v.Set("p", strconv.Itoa(i.Port))
	v.Set("fp", i.Fingerprint)
	v.Set("pin", i.PIN)
	v.Set("a", strings.Join(i.Addresses, ","))
	return "arcis://pair?" + v.Encode()
}

// Serve runs the page on 127.0.0.1:port until the listener fails. setSunshine stores a
// Sunshine login from the page's form (nil: the form reports it can't).
func Serve(port int, info func() Info, setSunshine func(user, pass string) error) error {
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
	mux.HandleFunc("POST /sunshine", func(w http.ResponseWriter, r *http.Request) {
		if !sameOrigin(r, port) {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		if setSunshine == nil {
			http.Error(w, "not available", http.StatusNotImplemented)
			return
		}
		if err := setSunshine(strings.TrimSpace(r.PostFormValue("user")), r.PostFormValue("pass")); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		http.Redirect(w, r, "/", http.StatusSeeOther)
	})
	mux.HandleFunc("/pair", func(w http.ResponseWriter, r *http.Request) {
		i := info()
		png, err := qrcode.Encode(PairLink(i), qrcode.Medium, 600)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		_ = pairPage.Execute(w, struct {
			Host, PIN string
			QR        template.URL
		}{i.Host, i.PIN, template.URL("data:image/png;base64," + base64.StdEncoding.EncodeToString(png))})
	})
	srv := &http.Server{Handler: mux, ReadHeaderTimeout: 5 * time.Second}
	return srv.Serve(ln)
}

// sameOrigin accepts form posts only from this page itself: the Host header must be the
// local address (no DNS rebinding) and a browser's Origin, if sent, must match it
// (no other website posting into it).
func sameOrigin(r *http.Request, port int) bool {
	allowed := map[string]bool{
		fmt.Sprintf("127.0.0.1:%d", port): true,
		fmt.Sprintf("localhost:%d", port): true,
	}
	if !allowed[r.Host] {
		return false
	}
	if o := r.Header.Get("Origin"); o != "" {
		u, err := url.Parse(o)
		return err == nil && u.Scheme == "http" && allowed[u.Host]
	}
	return true
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
