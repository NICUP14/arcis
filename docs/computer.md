# Your PC on the glasses

See your Windows PC in the glasses and control it from your phone: browse, write, play,
watch. It takes about 10 minutes, once.

**How it works**: two free apps do the picture and sound, and Arcis adds the controls.

| On the PC | On the phone | Job |
|---|---|---|
| **Sunshine** | **Moonlight** | Streams the PC's screen and sound to the glasses |
| **Arcis Bridge** | **Arcis** | Your phone's trackpad, keyboard and voice control the PC |

Both PC programs run quietly in the background. The **Arcis Bridge** installer helps you
set up Sunshine too.

> Windows only for now. The Bridge also builds for Linux and macOS but hasn't been
> tested there yet ([details](bridge.md)).

## 1. Install the Arcis Bridge on the PC

1. On the PC, download **ArcisBridge-x.y.z.msi** from the
   [latest release](https://github.com/NICUP14/arcis/releases/latest) and open it.
2. **"Windows protected your PC"?** The installer isn't code-signed yet. Click
   **More info › Run anyway**.
3. Click through the installer and allow it to make changes. It also allows the Bridge
   through the Windows firewall.

The Bridge starts by itself, shows a **ring icon** next to the clock (grey: waiting,
green: phone connected) and opens its **Setup** page in your browser. It starts with
Windows from then on.

> On Windows 11 new icons start hidden under the **^** arrow next to the clock. Drag the
> Arcis icon out to keep it visible.

## 2. Follow the Setup page

The Setup page (also in the tray icon's menu › **Setup…**) is a checklist:

1. **Pair your phone.** Shows a QR code (next step).
2. **Sunshine.** Not installed? Click **Install Sunshine**. A window shows the
   progress; accept Windows' prompt.
3. **Pair Moonlight by itself.** On a fresh Sunshine, click **Create a Sunshine login
   for Arcis** (Windows asks for permission once, and Sunshine restarts). Already use
   Sunshine? Type the login you made for its web page instead. This lets the Bridge do
   Moonlight's pairing for you later.
4. **Phone as a microphone**: skip this for now. The Bridge side is ready, but the
   switch for it in the phone app comes in a later version.

## 3. Pair your phone

Keep the Setup page (or tray › **Pair a phone…**) open on the PC.

1. On the phone, install **Moonlight** from the
   [Play Store](https://play.google.com/store/apps/details?id=com.limelight) if you
   haven't.
2. Plug in your glasses. In Arcis Home pick **Add computer** (or your PC's name, if it
   already shows up) › **Scan code with the phone**.
3. Point the phone's camera at the QR code on the PC screen. Done: your PC appears under
   *Computers*.

No camera? The Setup page also shows a 6-digit PIN you can type instead. Your phone's
own camera app also works: scanning the code opens Arcis.

## 4. Open your PC

Click your PC's tile in Arcis Home. Arcis connects and opens your desktop in the glasses.

**The very first time**, Moonlight asks to pair with Sunshine and shows a 4-digit PIN.
You don't have to type it anywhere: Arcis reads it and the Bridge enters it in Sunshine
("Moonlight is paired with …" appears in the glasses). If that doesn't work, open
Sunshine's page on the PC (https://localhost:47990), go to the **PIN** tab and type it
there.

While your PC is on screen:

- The trackpad, clicks, scrolling, zoom and [drawn shortcuts](using-arcis.md#drawn-shortcuts)
  control the PC (Ctrl+C, Ctrl+V… as usual).
- The keyboard button types on the PC; hold the mic button to dictate.
- The PC shows its own mouse pointer.
- Swipe along the bottom edge to switch to phone apps and back, as with any app.

## Good to know

- **Sharper picture.** Moonlight streams at 720p at first. For 1080p glasses (most of
  them), open Moonlight › **Settings** › *Video resolution* › **1080p**.
- **Waking a sleeping PC.** Click its tile anyway: Arcis wakes it (Wake-on-LAN) and opens
  the desktop when it's ready (the tile shows *Waking…*). This needs Wake-on-LAN
  turned on in the PC's BIOS/UEFI and network adapter settings, and the PC on a cable or
  a Wi-Fi that supports it. Arcis learns how to wake a PC after connecting to it once.
- **Away from home** (work, travel, mobile data): see [Away from home](away-from-home.md).
- **Videos on the PC.** Tap **▶ Media off** on the phone to turn the trackpad into a
  remote for a video playing on the PC.
- **Samsung phones**: Game Booster treats Moonlight as a game and adds a "touch lock"
  button. If the phone stops reacting, drag the lock icon to unlock, or turn Game Booster
  off for Moonlight.
- **Moonlight can't find the PC by name.** Arcis opens Moonlight's entry with the PC's
  name. If Sunshine uses a different name (Sunshine › Configuration › *Sunshine Name*),
  use the same name as the PC.
- **More than one PC?** Install the Bridge on each one and pair each one.

Problems? See [Troubleshooting](troubleshooting.md#your-pc).
