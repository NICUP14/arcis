# Away from home (Tailscale)

At home, your phone finds your PC on your Wi-Fi. Away from home (work, a café, mobile
data) it needs a private connection to it. The easiest is **[Tailscale](https://tailscale.com)**:
free for personal use, no router settings, about 5 minutes.

Tailscale creates a private network between *your* devices only. Arcis doesn't include
a VPN of its own and has no servers; everything goes straight from your phone to your
PC, encrypted.

## Set it up once

1. **On the PC**: install [Tailscale](https://tailscale.com/download), sign in.
2. **On the phone**: install Tailscale from the Play Store and sign in with the **same
   account**. Turn it on.
3. **Pair again (only if you paired before installing Tailscale).** Open the Bridge's
   *Pair a phone…* on the PC and scan the code again. The code includes the PC's
   Tailscale address, so Arcis can reach it from anywhere. (Or just connect once at
   home: Arcis learns the address by itself.)
4. **Tell Moonlight the address.** Moonlight keeps its own list of addresses, and
   other apps can't add to it. Do this once:
   1. Find your PC's Tailscale address: in the Tailscale app on the phone, under your
      PC's name (it starts with `100.`).
   2. In Moonlight, tap **+** (Add PC) and enter that address.

   Arcis helps here too: when you open your PC away from home and Moonlight can't find
   it, the glasses show the address to add, and it's already copied, so you only paste it.

That's it. From now on Arcis tries your home Wi-Fi first and switches to Tailscale by
itself. The PC's tile says **Remote** and the delay in milliseconds.

## Tips

- **Mobile data**: lower Moonlight's quality (Moonlight › Settings › *Video bitrate*,
  e.g. 10-20 Mbps) to save data and avoid stutter.
- **Waking the PC from away**: Wake-on-LAN only works inside your home network. To wake
  a PC from away, keep the Arcis Bridge running on another always-on device at home
  (a Raspberry Pi, a NAS, an old laptop): it passes the wake signal on. See
  [Bridge reference](bridge.md#clipboard-and-waking).
- **Other ways in.** ZeroTier, WireGuard or a forwarded port work too. Add the PC by
  that address in Arcis (*Add computer*) and in Moonlight.
- **Only over Tailscale.** To make the Bridge accept phones only through Tailscale, see
  the `-bind` option in the [Bridge reference](bridge.md).
