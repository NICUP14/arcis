package status

import (
	"net/http"
	"net/http/httptest"
	"net/url"
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
