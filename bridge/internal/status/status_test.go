package status

import (
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
