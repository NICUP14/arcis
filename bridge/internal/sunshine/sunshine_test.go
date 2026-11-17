package sunshine

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"
)

// fakeSunshine imitates the parts of Sunshine's API the bridge uses.
type fakeSunshine struct {
	mu        sync.Mutex
	pending   []pending
	legacy    bool // no GET /api/pin (older Sunshine)
	submitted map[string]string
	appearIn  time.Duration // the request shows up this long after start
	start     time.Time
}

func (f *fakeSunshine) handler() http.Handler {
	mux := http.NewServeMux()
	auth := func(w http.ResponseWriter, r *http.Request) bool {
		u, p, ok := r.BasicAuth()
		if !ok || u != "sunshine" || p != "secret" {
			w.WriteHeader(http.StatusUnauthorized)
			return false
		}
		return true
	}
	mux.HandleFunc("GET /api/config", func(w http.ResponseWriter, r *http.Request) {
		if auth(w, r) {
			_, _ = w.Write([]byte(`{"status":true}`))
		}
	})
	mux.HandleFunc("GET /api/pin", func(w http.ResponseWriter, r *http.Request) {
		if !auth(w, r) {
			return
		}
		if f.legacy {
			w.WriteHeader(http.StatusNotFound)
			return
		}
		f.mu.Lock()
		defer f.mu.Unlock()
		list := []pending{}
		if time.Since(f.start) >= f.appearIn && f.pending != nil {
			list = f.pending
		}
		_ = json.NewEncoder(w).Encode(map[string]any{"pairings": list})
	})
	mux.HandleFunc("POST /api/pin", func(w http.ResponseWriter, r *http.Request) {
		if !auth(w, r) {
			return
		}
		var body map[string]string
		_ = json.NewDecoder(r.Body).Decode(&body)
		f.mu.Lock()
		f.submitted = body
		f.mu.Unlock()
		ok := body["pin"] == "1234"
		_ = json.NewEncoder(w).Encode(map[string]any{"status": ok})
	})
	return mux
}

func newFake(t *testing.T, f *fakeSunshine) *Client {
	f.start = time.Now()
	srv := httptest.NewTLSServer(f.handler())
	t.Cleanup(srv.Close)
	c := New(srv.URL, "sunshine", "secret")
	c.HTTP = srv.Client()
	c.PendingWait = 2 * time.Second
	return c
}

func TestSubmitPINPicksTheRequestFromThePhone(t *testing.T) {
	f := &fakeSunshine{pending: []pending{
		{ID: "aaaa", Name: "laptop", Address: "192.168.1.9"},
		{ID: "bbbb", Name: "phone", Address: "100.64.7.8"},
	}}
	c := newFake(t, f)
	if err := c.SubmitPIN(context.Background(), "1234", "Galaxy", "100.64.7.8"); err != nil {
		t.Fatal(err)
	}
	if f.submitted["pairing_id"] != "bbbb" || f.submitted["name"] != "Galaxy" || f.submitted["pin"] != "1234" {
		t.Fatalf("submitted %v", f.submitted)
	}
}

func TestSubmitPINWaitsForTheRequest(t *testing.T) {
	f := &fakeSunshine{pending: []pending{{ID: "cccc", Address: "10.0.0.2"}}, appearIn: 700 * time.Millisecond}
	c := newFake(t, f)
	if err := c.SubmitPIN(context.Background(), "1234", "Galaxy", "10.0.0.5"); err != nil {
		t.Fatal(err)
	}
	if f.submitted["pairing_id"] != "cccc" {
		t.Fatalf("submitted %v", f.submitted)
	}
}

func TestSubmitPINErrors(t *testing.T) {
	c := newFake(t, &fakeSunshine{pending: []pending{{ID: "dddd"}}})
	if err := c.SubmitPIN(context.Background(), "9999", "Galaxy", ""); !errors.Is(err, ErrPINRefused) {
		t.Fatalf("wrong PIN: %v", err)
	}
	empty := newFake(t, &fakeSunshine{})
	empty.PendingWait = 300 * time.Millisecond
	if err := empty.SubmitPIN(context.Background(), "1234", "Galaxy", ""); !errors.Is(err, ErrNoRequest) {
		t.Fatalf("no request: %v", err)
	}
	bad := newFake(t, &fakeSunshine{})
	bad.Pass = "nope"
	if err := bad.Check(context.Background()); !errors.Is(err, ErrBadLogin) {
		t.Fatalf("bad login: %v", err)
	}
	none := New("https://127.0.0.1:1", "", "")
	if err := none.Check(context.Background()); !errors.Is(err, ErrNoLogin) {
		t.Fatalf("no login: %v", err)
	}
	down := New("https://127.0.0.1:1", "u", "p")
	if err := down.Check(context.Background()); !errors.Is(err, ErrNotRunning) {
		t.Fatalf("not running: %v", err)
	}
}

func TestLegacySunshineWithoutRequestList(t *testing.T) {
	f := &fakeSunshine{legacy: true}
	c := newFake(t, f)
	if err := c.SubmitPIN(context.Background(), "1234", "Galaxy", ""); err != nil {
		t.Fatal(err)
	}
	if _, ok := f.submitted["pairing_id"]; ok {
		t.Fatalf("legacy submit had a pairing_id: %v", f.submitted)
	}
}

func TestLoginRoundTrip(t *testing.T) {
	dir := t.TempDir()
	if l, err := LoadLogin(dir); err != nil || l.User != "" {
		t.Fatalf("empty: %+v %v", l, err)
	}
	if err := SaveLogin(dir, Login{User: "sunshine", Pass: "p@ss"}); err != nil {
		t.Fatal(err)
	}
	l, err := LoadLogin(dir)
	if err != nil || l.User != "sunshine" || l.Pass != "p@ss" {
		t.Fatalf("got %+v %v", l, err)
	}
}
