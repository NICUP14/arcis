// Package wake sends Wake-on-LAN magic packets and lists this computer's MAC addresses.
package wake

import (
	"errors"
	"fmt"
	"net"
	"strings"
)

// MagicPacket builds the 102-byte Wake-on-LAN payload for mac.
func MagicPacket(mac string) ([]byte, error) {
	hw, err := net.ParseMAC(strings.TrimSpace(mac))
	if err != nil {
		return nil, fmt.Errorf("bad MAC address %q: %w", mac, err)
	}
	if len(hw) != 6 {
		return nil, errors.New("MAC address must have 6 bytes")
	}
	p := make([]byte, 0, 102)
	for i := 0; i < 6; i++ {
		p = append(p, 0xFF)
	}
	for i := 0; i < 16; i++ {
		p = append(p, hw...)
	}
	return p, nil
}

// Send broadcasts the magic packet for mac on every IPv4 network of this computer
// (and to 255.255.255.255), on UDP ports 9 and 7. Used by the bridge's wake relay.
func Send(mac string) error {
	pkt, err := MagicPacket(mac)
	if err != nil {
		return err
	}
	targets := []string{"255.255.255.255"}
	for _, b := range broadcastAddresses() {
		targets = append(targets, b)
	}
	conn, err := net.ListenPacket("udp4", ":0")
	if err != nil {
		return err
	}
	defer conn.Close()
	var sent int
	for _, t := range targets {
		for _, port := range []int{9, 7} {
			addr := &net.UDPAddr{IP: net.ParseIP(t), Port: port}
			if _, err := conn.WriteTo(pkt, addr); err == nil {
				sent++
			}
		}
	}
	if sent == 0 {
		return errors.New("could not send the wake packet")
	}
	return nil
}

func broadcastAddresses() []string {
	var out []string
	ifaces, _ := net.Interfaces()
	for _, ifc := range ifaces {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagBroadcast == 0 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			ipn, ok := a.(*net.IPNet)
			if !ok || ipn.IP.To4() == nil {
				continue
			}
			ip := ipn.IP.To4()
			mask := ipn.Mask
			if len(mask) == 16 {
				mask = mask[12:]
			}
			b := make(net.IP, 4)
			for i := range ip {
				b[i] = ip[i] | ^mask[i]
			}
			out = append(out, b.String())
		}
	}
	return out
}

// MACs lists the MAC addresses of this computer's active network cards with IPv4.
func MACs() []string {
	var out []string
	ifaces, _ := net.Interfaces()
	for _, ifc := range ifaces {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 || len(ifc.HardwareAddr) != 6 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			if ipn, ok := a.(*net.IPNet); ok && ipn.IP.To4() != nil {
				out = append(out, ifc.HardwareAddr.String())
				break
			}
		}
	}
	return out
}
