# Using your computer away from home (Tailscale)

Arcis doesn't include a VPN. Put the phone and the computer on the same private
network with **[Tailscale](https://tailscale.com)** (free for personal use) — or
ZeroTier, WireGuard, or a forwarded port — and everything else is automatic.

1. Install Tailscale on the computer and on the phone, sign in to the same account,
   and turn it on.
2. **Controls (Arcis Bridge):** nothing to configure. When the phone pairs or
   connects, the bridge reports all its addresses, including its Tailscale one
   (`100.x.y.z`). Arcis tries the home network first (short timeout) and falls back
   to Tailscale on its own. The computer's tile then shows **Remote** and the
   latency. If a computer isn't reachable and the phone isn't on Tailscale, the tile
   says so.
3. **Picture (Moonlight):** in Moonlight, add the computer once more by its
   Tailscale IP or MagicDNS name (e.g. `desktop.tail1234.ts.net`). For mobile data,
   lower Moonlight's bitrate (e.g. 10–20 Mbps at 1080p60).

Tips

- To accept phones only over Tailscale, start the bridge with
  `-bind <tailscale-ip>`.
- Waking a sleeping computer from away needs an always-on device at home; see the
  bridge's wake relay ([bridge.md](bridge.md)).
