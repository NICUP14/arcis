package status

import (
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

func TestPairLinkCarriesEverythingToPair(t *testing.T) {
	link := PairLink(Info{
		Host: "My PC", Port: 41990, PIN: "012345", Fingerprint: "ab12",
		Addresses: []string{"192.168.1.5", "100.64.1.2"},
	})
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	if u.Scheme != "arcis" || u.Host != "pair" {
		t.Fatalf("link %q", link)
	}
	q := u.Query()
	want := map[string]string{
		"h": "My PC", "p": "41990", "pin": "012345", "fp": "ab12", "a": "192.168.1.5,100.64.1.2",
	}
	for k, v := range want {
		if q.Get(k) != v {
			t.Errorf("%s = %q, want %q", k, q.Get(k), v)
		}
	}
}

func TestFormPostsOnlyFromThePageItself(t *testing.T) {
	req := func(host, origin string) *http.Request {
		r := httptest.NewRequest(http.MethodPost, "http://"+host+"/sunshine", nil)
		r.Host = host
		if origin != "" {
			r.Header.Set("Origin", origin)
		}
		return r
	}
	cases := []struct {
		host, origin string
		ok           bool
	}{
		{"127.0.0.1:41991", "http://127.0.0.1:41991", true},
		{"localhost:41991", "", true},
		{"127.0.0.1:41991", "https://evil.example", false},
		{"evil.example:41991", "http://evil.example:41991", false}, // DNS rebinding
		{"127.0.0.1:41991", "null", false},
	}
	for _, c := range cases {
		if got := sameOrigin(req(c.host, c.origin), 41991); got != c.ok {
			t.Errorf("host %s origin %s: %v, want %v", c.host, c.origin, got, c.ok)
		}
	}
}

func TestSetupPageAndActions(t *testing.T) {
	ran := make(chan string, 1)
	h := handler(41991, Handlers{
		Info: func() Info {
			return Info{Host: "PC", Port: 41990, PIN: "123456", Fingerprint: "ab", Sunshine: "set your Sunshine login below"}
		},
		Setup: func() SetupInfo { return SetupInfo{Supported: true, SunshineInstalled: true, SunshineHasLogin: false} },
		Act:   func(a string) error { ran <- a; return nil },
	})
	get := httptest.NewRecorder()
	h.ServeHTTP(get, httptest.NewRequest(http.MethodGet, "http://127.0.0.1:41991/setup", nil))
	body := get.Body.String()
	for _, want := range []string{"Pair your phone", "data:image/png;base64,", "Create a Sunshine login for Arcis", "Install VB-Cable"} {
		if !strings.Contains(body, want) {
			t.Errorf("setup page lacks %q", want)
		}
	}

	post := func(origin string) int {
		r := httptest.NewRequest(http.MethodPost, "http://127.0.0.1:41991/setup/login", nil)
		r.Header.Set("Origin", origin)
		w := httptest.NewRecorder()
		h.ServeHTTP(w, r)
		return w.Code
	}
	if code := post("https://evil.example"); code != http.StatusForbidden {
		t.Fatalf("foreign post: %d", code)
	}
	if code := post("http://127.0.0.1:41991"); code != http.StatusSeeOther {
		t.Fatalf("own post: %d", code)
	}
	if a := <-ran; a != "login" {
		t.Fatalf("ran %q", a)
	}
}
