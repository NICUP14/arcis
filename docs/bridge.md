# Arcis Bridge (host helper)

The Arcis Bridge runs on the computer you want to control. It receives the
trackpad, keyboard and dictation input from the Arcis app and performs it there.
Video and sound come from **Moonlight** on the phone and **Sunshine/Apollo** on the
computer; the bridge only handles controls.

It is a single standalone file per system — no installer, no runtime.

## Run

| System | Build | Notes |
|---|---|---|
| Windows | `GOOS=windows GOARCH=amd64 go build -ldflags -H=windowsgui -o arcis-bridge.exe ./cmd/arcis-bridge` | Allow it through the firewall when Windows asks (private networks). Run as administrator only if you need to control elevated windows (UAC prompts, admin apps). |
| Linux (**untested**) | `GOOS=linux GOARCH=amd64 go build -o arcis-bridge ./cmd/arcis-bridge` | Needs write access to `/dev/uinput`, see below. Works on X11 and Wayland. |
| macOS (**untested**) | `GOOS=darwin GOARCH=arm64 go build -o arcis-bridge ./cmd/arcis-bridge` | Grant **Accessibility** to the terminal / binary in System Settings › Privacy & Security. |

All builds work with `CGO_ENABLED=0`. Only the Windows bridge has been tested; the Linux
and macOS bridges build but have never been run on a real machine, so expect rough edges.

Start it and open **http://127.0.0.1:41991** on that computer: it shows the
pairing PIN, its addresses and the paired phones.

**On Windows** the bridge has no console window; it lives in the notification area
(by the clock; Windows 11 may first put it under **^**, drag it out to keep it visible).
The icon is grey while waiting and green while a phone is connected. Its menu:
*Pair a phone…*, *Status page*, *Open log*, *Start with Windows* (on by default) and
*Quit*. Starting it a second time just opens the status page. The first run opens the
pairing page by itself. The log is `bridge.log` in the config directory
(`%AppData%\arcis-bridge`). On Linux and macOS it keeps running in the terminal.

Options:

```
arcis-bridge -port 41990 -status-port 41991 -no-mdns -config <dir>
```

### Linux: allow /dev/uinput (untested)

```sh
echo 'KERNEL=="uinput", GROUP="input", MODE="0660", OPTIONS+="static_node=uinput"' | \
  sudo tee /etc/udev/rules.d/60-arcis-uinput.rules
sudo usermod -aG input "$USER"
sudo modprobe uinput && sudo udevadm control --reload && sudo udevadm trigger
# log out and back in
```

## Pair a phone

1. Start the bridge; note the PIN on the status page.
2. In Arcis on the phone, pick the computer (found automatically on the same
   network) and enter the PIN. Pairing is remembered on both sides.

## Clipboard and waking

- **Clipboard**: text copied on the computer is put on the phone's clipboard
  (paste it in any phone app). Drawing **C** (copy) in a phone app also puts that
  text on the computer's clipboard. Linux needs `wl-clipboard` (Wayland) or `xclip`.
  Turn off with `-no-clipboard`.
- **Wake relay**: right-click (long-press) a computer's tile to wake it. The phone
  sends Wake-on-LAN itself (works at home) and asks any connected bridge to send it
  on its own network too. Run a bridge on an always-on device at home (Raspberry Pi,
  NAS, old laptop) to wake computers from anywhere over Tailscale. The computer needs
  Wake-on-LAN enabled in its BIOS/UEFI and network adapter settings.

## Phone as a microphone (headset)

The phone microphone can be streamed to the computer, which offers it as a microphone
to Discord, Teams, Zoom, games... Sound from the computer already plays in the glasses
through Moonlight. *There is currently no control on the phone to start it (the volume
keys are plain volume again); the bridge side below is ready.*

| System | Setup |
|---|---|
| Windows | Install the free [VB-Audio Virtual Cable](https://vb-audio.com/Cable/). The bridge plays the phone into **CABLE Input**; choose **CABLE Output** as the microphone in your apps. Another device: `-mic-device "<name part>"`. |
| Linux (untested) | Needs `pactl` (PipeWire or PulseAudio). The bridge creates a source called **Arcis_Mic**; choose it in your apps. |
| macOS | Not supported yet. |

Audio is 16 kHz mono (speech quality), about 256 kbit/s. Turn off with `-no-mic`.

## Security

- TLS with certificate pinning and a per-phone secret token (see
  [protocol.md](protocol.md)); unpaired connections can't send input.
- The status page (with the PIN) listens on localhost only.
- Paired phones are stored in the config directory (`devices.json`); delete it to
  unpair everything.
