# Arcis Bridge protocol (v1)

The phone connects to the bridge over **TLS on TCP port 41990** and exchanges
**newline-delimited JSON** messages (one object per line, max 64 KiB). Every
message has a type field `t`.

## Trust

- The bridge has a self-signed certificate (created on first run, stored in its
  config directory). Phones **pin its SHA-256 fingerprint** when pairing and refuse
  any other certificate afterwards.
- Pairing uses a **6-digit PIN** shown on the bridge's local status page
  (`http://127.0.0.1:41991`) and in its console. A PIN works once; three wrong
  PINs replace it.
- After pairing, the phone authenticates with a random 256-bit **token**.
- Over Tailscale the TLS connection is additionally inside WireGuard.

## Pairing (one-time)

```
phone  -> {"t":"pair","pin":"123456","name":"Galaxy S20 FE"}
bridge -> {"t":"paired","token":"<64 hex>","host":"DESKTOP-1"}     (connection closes)
bridge -> {"t":"error","msg":"Wrong PIN"}                          (on failure)
```

## Session

```
phone  -> {"t":"hello","token":"<64 hex>","name":"Galaxy S20 FE"}
bridge -> {"t":"welcome","host":"DESKTOP-1","os":"windows","version":1}
```

Then any number of input messages (no replies, except pong):

| Message | Fields | Meaning |
|---|---|---|
| `move` | `dx`, `dy` (float px) | relative pointer movement; fractions accumulate |
| `button` | `button` (`left`/`right`/`middle`), `down` | press / release |
| `click` | `button` | press + release |
| `scroll` | `dx`, `dy` (notches, fractions ok) | `dy > 0` shows content further down |
| `key` | `key`, `mods` (`ctrl`, `shift`, `alt`, `meta`) | named key: `Enter`, `Backspace`, `Escape`, `Tab`, `Space`, `Delete`, arrows, `Home`, `End`, `PageUp`, `PageDown`, `A`–`Z`, `0`–`9` |
| `text` | `text` | type Unicode text (Linux: US-layout characters only) |
| `ping` | `time` | bridge answers `{"t":"pong","time":...}` (latency) |

## Discovery

The bridge announces `<hostname>._arcis._tcp.local` via mDNS with TXT records
`v=1`, `os=<goos>`, `fp=<first 16 hex of the fingerprint>`, `name=<hostname>`.
Phones can also add a bridge by address (e.g. a Tailscale IP or MagicDNS name).
