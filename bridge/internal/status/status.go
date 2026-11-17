// Package status serves the bridge's small local web pages: status (paired phones,
// addresses), pairing (QR code + PIN) and setup (a checklist with one-click fixes).
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
	"sync"
	"time"

	qrcode "github.com/skip2/go-qrcode"
)

// Info is what the status page shows.
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

// SetupInfo is the setup checklist.
type SetupInfo struct {
	// Supported: one-click installs are available (Windows).
	Supported         bool
	Autostart         bool
	SunshineInstalled bool
	// SunshineHasLogin: Sunshine itself has a web login (else the bridge can create one).
	SunshineHasLogin bool
	MicReady         bool
}

// Handlers are the pages' data and actions.
type Handlers struct {
	Info func() Info
	// SetSunshine checks and stores a Sunshine login typed into the setup page. May be nil.
	SetSunshine func(user, pass string) error
	// Setup reports the checklist. May be nil (no setup page).
	Setup func() SetupInfo
	// Act runs a setup action: "sunshine" (install), "login" (create a Sunshine login
	// for the bridge) or "vbcable" (install). It may take a while (admin prompts).
	Act func(action string) error
}

const style = `<meta charset="utf-8"><style>
body{font-family:system-ui,sans-serif;background:#0d0f14;color:#e8eaf0;max-width:760px;margin:40px auto;padding:0 20px}
h1{font-weight:500}.pin{font-size:56px;letter-spacing:10px;color:#6ea8ff;margin:8px 0}
.card{background:#161922;border:1px solid #262a33;border-radius:14px;padding:18px 22px;margin:16px 0}
.dim{color:#8a90a0}.warn{color:#ffb35a}.ok{color:#4cd98a}code{color:#c9d1e0}a{color:#6ea8ff}
input{background:#0d0f14;color:#e8eaf0;border:1px solid #262a33;border-radius:8px;padding:8px 10px;margin:4px 8px 4px 0}
button{background:#1e3a6e;color:#e8eaf0;border:0;border-radius:8px;padding:9px 16px;cursor:pointer}
.row{display:flex;gap:14px;align-items:flex-start}.mark{font-size:22px;width:26px;text-align:center}
.qr{background:#fff;border-radius:18px;padding:14px;display:inline-block;margin:12px 0}
nav a{margin-right:16px}
</style>
<script>setInterval(function(){var a=document.activeElement;if(!a||a.tagName!=="INPUT")location.reload()},5000)</script>`

const nav = `<nav class="dim"><a href="/">Status</a><a href="/pair">Pair a phone</a><a href="/setup">Setup</a></nav>`

var page = template.Must(template.New("status").Parse(`<!doctype html>
<html><head>` + style + `<title>Arcis Bridge</title></head><body>
<h1>Arcis Bridge <span class="dim">{{.Version}}</span></h1>` + nav + `
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
  Moonlight pairing: <span class="{{if .SunshineOK}}ok{{else}}warn{{end}}">{{.Sunshine}}</span>
  {{if not .SunshineOK}} · <a href="/setup">Set up</a>{{end}}
</div>
</body></html>`))

var pairPage = template.Must(template.New("pair").Parse(`<!doctype html>
<html><head>` + style + `<title>Pair a phone · Arcis Bridge</title></head><body>
<h1>Pair a phone with {{.Host}}</h1>` + nav + `
<div style="text-align:center">
<p class="dim">In Arcis on the glasses pick <b>Add computer</b> → <b>Scan code with the phone</b>,<br>
or point the phone's camera at the code.</p>
<div class="qr"><img src="{{.QR}}" width="300" height="300" alt="Pairing code"></div>
<div class="dim">No camera? Enter this PIN in Arcis instead:</div>
<div class="pin" style="font-size:34px">{{.PIN}}</div>
</div>
</body></html>`))

var setupPage = template.Must(template.New("setup").Parse(`<!doctype html>
<html><head>` + style + `<title>Setup · Arcis Bridge</title></head><body>
<h1>Set up {{.Info.Host}}</h1>` + nav + `
{{if .Busy}}<div class="card">⏳ {{.Busy}}</div>{{end}}
{{if .Result}}<div class="card {{if .ResultOK}}ok{{else}}warn{{end}}">{{.Result}}</div>{{end}}

<div class="card row">
  <div class="mark {{if .Info.Devices}}ok{{else}}dim{{end}}">{{if .Info.Devices}}✓{{else}}1{{end}}</div>
  <div>
    <b>Pair your phone</b>
    {{if .Info.Devices}}<div class="dim">{{len .Info.Devices}} phone(s) paired. <a href="/pair">Pair another</a></div>
    {{else}}<div class="dim">In Arcis on the glasses: <b>Add computer</b> → <b>Scan code with the phone</b>.</div>
    <div class="qr"><img src="{{.QR}}" width="220" height="220" alt="Pairing code"></div>
    <div class="dim">or type the PIN <b>{{.Info.PIN}}</b></div>{{end}}
  </div>
</div>

<div class="card row">
  <div class="mark {{if .Setup.SunshineInstalled}}ok{{else}}dim{{end}}">{{if .Setup.SunshineInstalled}}✓{{else}}2{{end}}</div>
  <div>
    <b>Sunshine</b> <span class="dim">(sends the computer's picture and sound to Moonlight)</span>
    {{if .Setup.SunshineInstalled}}<div class="dim">Installed.</div>
    {{else if .Setup.Supported}}<div class="dim" style="margin:6px 0">Not installed.</div>
    <form method="post" action="/setup/sunshine"><button>Install Sunshine</button></form>
    {{else}}<div class="dim">Install Sunshine from <a href="https://github.com/LizardByte/Sunshine">github.com/LizardByte/Sunshine</a>.</div>{{end}}
  </div>
</div>

<div class="card row">
  <div class="mark {{if .Info.SunshineOK}}ok{{else}}dim{{end}}">{{if .Info.SunshineOK}}✓{{else}}3{{end}}</div>
  <div>
    <b>Pair Moonlight by itself</b> <span class="dim">(the bridge enters Moonlight's PIN into Sunshine)</span>
    <div class="{{if .Info.SunshineOK}}dim{{else}}warn{{end}}" style="margin:6px 0">{{.Info.Sunshine}}</div>
    {{if not .Info.SunshineOK}}
      {{if and .Setup.Supported .Setup.SunshineInstalled (not .Setup.SunshineHasLogin)}}
      <form method="post" action="/setup/login"><button>Create a Sunshine login for Arcis</button></form>
      <div class="dim">Windows asks for admin rights once; Sunshine restarts.</div>
      {{else if .Setup.SunshineInstalled}}
      <div class="dim">Enter the login you use for Sunshine's web page (https://localhost:47990):</div>
      <form method="post" action="/sunshine">
        <input name="user" placeholder="Sunshine username" autocomplete="off">
        <input name="pass" type="password" placeholder="Password" autocomplete="off">
        <button>Save</button>
      </form>
      {{end}}
    {{end}}
  </div>
</div>

<div class="card row">
  <div class="mark {{if .Setup.MicReady}}ok{{else}}dim{{end}}">{{if .Setup.MicReady}}✓{{else}}4{{end}}</div>
  <div>
    <b>Phone as a microphone</b> <span class="dim">(optional: Discord, Teams, games…)</span>
    {{if .Setup.MicReady}}<div class="dim">VB-Cable is installed. Pick <b>CABLE Output</b> as the microphone in your apps.</div>
    {{else if .Setup.Supported}}<div class="dim" style="margin:6px 0">Needs the free VB-Cable driver from VB-Audio.</div>
    <form method="post" action="/setup/vbcable"><button>Install VB-Cable</button></form>
    {{else}}<div class="dim">See the bridge docs for your system.</div>{{end}}
  </div>
</div>

{{if .Setup.Supported}}<div class="card row">
  <div class="mark {{if .Setup.Autostart}}ok{{else}}dim{{end}}">{{if .Setup.Autostart}}✓{{else}}·{{end}}</div>
  <div><b>Start with Windows</b>
  <div class="dim">{{if .Setup.Autostart}}On.{{else}}Off.{{end}} Change it in the tray menu (the Arcis icon by the clock).</div></div>
</div>{{end}}
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

func qrDataURL(i Info, size int) (template.URL, error) {
	png, err := qrcode.Encode(PairLink(i), qrcode.Medium, size)
	if err != nil {
		return "", err
	}
	return template.URL("data:image/png;base64," + base64.StdEncoding.EncodeToString(png)), nil
}

// actions tracks the one setup action that may run at a time.
type actions struct {
	mu       sync.Mutex
	busy     string
	result   string
	resultOK bool
}

var actionLabels = map[string]string{
	"sunshine": "Installing Sunshine (follow its window)…",
	"login":    "Creating a Sunshine login (accept Windows' admin prompt)…",
	"vbcable":  "Installing VB-Cable (accept the admin prompt, then click Install Driver)…",
}

var actionDone = map[string]string{
	"sunshine": "Sunshine's installer started. This page updates when it's done.",
	"login":    "Sunshine login created. Moonlight pairing is ready.",
	"vbcable":  "VB-Cable's installer started. This page updates when it's done (a restart may be needed).",
}

func (a *actions) start(name string, act func(string) error) bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	if a.busy != "" {
		return false
	}
	a.busy, a.result = actionLabels[name], ""
	go func() {
		err := act(name)
		a.mu.Lock()
		defer a.mu.Unlock()
		a.busy = ""
		if err != nil {
			a.result, a.resultOK = err.Error(), false
		} else {
			a.result, a.resultOK = actionDone[name], true
		}
	}()
	return true
}

func (a *actions) state() (busy, result string, ok bool) {
	a.mu.Lock()
	defer a.mu.Unlock()
	return a.busy, a.result, a.resultOK
}

// Serve runs the pages on 127.0.0.1:port until the listener fails.
func Serve(port int, h Handlers) error {
	ln, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
	if err != nil {
		return err
	}
	srv := &http.Server{Handler: handler(port, h), ReadHeaderTimeout: 5 * time.Second}
	return srv.Serve(ln)
}

// handler serves the pages for a server on 127.0.0.1:port.
func handler(port int, h Handlers) http.Handler {
	acts := &actions{}
	mux := http.NewServeMux()
	html := func(w http.ResponseWriter) {
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
	}
	mux.HandleFunc("GET /{$}", func(w http.ResponseWriter, r *http.Request) {
		html(w)
		_ = page.Execute(w, h.Info())
	})
	mux.HandleFunc("GET /pair", func(w http.ResponseWriter, r *http.Request) {
		i := h.Info()
		qr, err := qrDataURL(i, 600)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		html(w)
		_ = pairPage.Execute(w, struct {
			Host, PIN string
			QR        template.URL
		}{i.Host, i.PIN, qr})
	})
	mux.HandleFunc("GET /setup", func(w http.ResponseWriter, r *http.Request) {
		if h.Setup == nil {
			http.Redirect(w, r, "/", http.StatusSeeOther)
			return
		}
		i := h.Info()
		qr, err := qrDataURL(i, 440)
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		busy, result, ok := acts.state()
		html(w)
		_ = setupPage.Execute(w, struct {
			Info     Info
			Setup    SetupInfo
			QR       template.URL
			Busy     string
			Result   string
			ResultOK bool
		}{i, h.Setup(), qr, busy, result, ok})
	})
	mux.HandleFunc("POST /setup/{action}", func(w http.ResponseWriter, r *http.Request) {
		if !sameOrigin(r, port) {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		name := r.PathValue("action")
		if h.Act == nil || actionLabels[name] == "" {
			http.NotFound(w, r)
			return
		}
		acts.start(name, h.Act) // ignored while another action runs
		http.Redirect(w, r, "/setup", http.StatusSeeOther)
	})
	mux.HandleFunc("POST /sunshine", func(w http.ResponseWriter, r *http.Request) {
		if !sameOrigin(r, port) {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		if h.SetSunshine == nil {
			http.Error(w, "not available", http.StatusNotImplemented)
			return
		}
		if err := h.SetSunshine(strings.TrimSpace(r.PostFormValue("user")), r.PostFormValue("pass")); err != nil {
			acts.mu.Lock()
			acts.result, acts.resultOK = "Sunshine login: "+err.Error(), false
			acts.mu.Unlock()
		}
		http.Redirect(w, r, "/setup", http.StatusSeeOther)
	})
	return mux
}

// sameOrigin accepts form posts only from these pages: the Host header must be the
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
