// Package discovery announces the bridge on the local network (mDNS / DNS-SD),
// so phones find it without typing an address.
package discovery

import (
	"runtime"

	"github.com/grandcat/zeroconf"

	"github.com/NICUP14/arcis/bridge/internal/proto"
)

// Announcement is a running mDNS registration.
type Announcement struct{ server *zeroconf.Server }

// Announce publishes "<host>._arcis._tcp.local" on all interfaces.
// fingerprint lets a phone check it found the bridge it already paired with.
func Announce(host string, port int, fingerprint string) (*Announcement, error) {
	fp := fingerprint
	if len(fp) > 16 {
		fp = fp[:16]
	}
	txt := []string{"v=1", "os=" + runtime.GOOS, "fp=" + fp, "name=" + host}
	s, err := zeroconf.Register(host, proto.ServiceType, "local.", port, txt, nil)
	if err != nil {
		return nil, err
	}
	return &Announcement{server: s}, nil
}

// Stop withdraws the announcement.
func (a *Announcement) Stop() {
	if a != nil && a.server != nil {
		a.server.Shutdown()
	}
}
