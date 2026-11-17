# Troubleshooting

Find your problem below. Still stuck? Open an
[issue](https://github.com/NICUP14/arcis/issues) with your phone model, your glasses,
and what you did.

- [Installing and setup](#installing-and-setup)
- [Glasses and the phone](#glasses-and-the-phone)
- [Using apps](#using-apps)
- [Your PC](#your-pc)
- [Away from home](#away-from-home)

## Installing and setup

**"App not installed" or the install button does nothing.**
An older Arcis signed differently may be installed (for example a test build).
Uninstall Arcis first, then install the new APK.

**I can't turn on "Arcis controller": "Restricted setting".**
Android protects this for apps not from the Play Store. Open **Settings › Apps ›
Arcis**, tap **⋮** (top right) › **Allow restricted settings**, then try again. See
[Getting started](getting-started.md#turning-on-the-accessibility-service).

**Arcis controller turns itself off.**
Some phones stop accessibility services of apps they "optimize". In the setup checklist
tap **Allow** next to *No battery limits*. On Samsung also check **Settings › Battery ›
Background usage limits** and make sure Arcis isn't in *Sleeping apps*.

**The checklist doesn't tick an item I just turned on.**
Go back to Arcis; rows re-check when you return. If it still doesn't tick, turn the
setting off and on again.

## Glasses and the phone

**The glasses stay black.**
Your phone may not send video over USB-C (many budget phones can't). Test without
Arcis: if nothing appears in the glasses with any app, the phone can't drive them.
Also try another cable or plugging the glasses in directly (no hub).

**The glasses just show my phone's screen (mirroring), not Arcis Home.**
Open the Arcis app on the phone once with the glasses connected; it puts Arcis Home on
them. On Samsung, if **DeX** starts instead, turn off DeX's automatic start (Settings ›
Connected devices › Samsung DeX) and reconnect.

**The phone shows the trackpad but nothing happens in the glasses.**
The accessibility service is off or was stopped. Open Arcis on the phone: the checklist
shows it. Turning *Arcis controller* off and on again in Accessibility settings fixes
it.

**The phone stops reacting to touches while Moonlight is open (Samsung).**
Game Booster's "touch lock" is on. Drag the lock icon on the navigation bar to unlock,
or turn Game Booster off for Moonlight.

**The pointer is too fast or too slow.** *Settings › Pointer speed* in the glasses.

**The screen rotates or is the wrong way round.** Use the upright / sideways button in
the top corner of the phone; Arcis doesn't auto-rotate on purpose.

**The phone gets warm or the battery drains fast.** The glasses take power from the
phone. Use a USB-C hub or adapter that charges the phone while the glasses are
connected, and lower the phone's screen brightness: you don't need to see it.

## Using apps

**An app opens on the phone instead of in the glasses.**
A few apps refuse to open on a second screen. Nothing Arcis can do about those apps;
most work fine.

**A video shows black in the glasses (Netflix and others).**
Some streaming apps block their picture on external screens when the phone or cable
doesn't support copy protection (HDCP). Try another cable or adapter; some phones and
glasses don't support it at all.

**Typing doesn't go into the field.**
Click the text field in the glasses first (point and tap), then tap the keyboard
button. A few apps don't accept text from accessibility services.

**Media mode doesn't turn on for a video.**
It needs *Notification access* (setup checklist), and the app must report what it plays
to Android (most video and music apps do).

**Drawn shapes aren't recognized.**
Rest your finger until you feel the tick *before* drawing, and draw in one stroke at a
normal size. The Gestures tile in Arcis Home shows each shape and where to start.

**No message banners.**
Check *Notification access* in the setup checklist and *Settings › Message banners* in
the glasses. Only messages, calls and urgent notifications pop up; silent ones don't.

## Your PC

**My PC isn't listed / "Can't reach its Arcis Bridge".**
- Is the Bridge running? Look for the ring icon next to the clock (maybe under **^**).
  Start it from the Start menu: *Arcis Bridge*.
- Are the phone and PC on the same Wi-Fi? Guest networks and some office networks
  keep devices apart; use [Tailscale](away-from-home.md) there.
- Windows firewall: the installer allows the Bridge. If you ran the plain `.exe`,
  allow it when Windows asks.

**"Windows protected your PC" when installing the Bridge.**
The installer isn't code-signed yet: click **More info › Run anyway**.

**Moonlight says it can't find the PC, or asks to add it.**
- At home: is Sunshine running (tray icon, or https://localhost:47990 on the PC)?
- Away from home: add the PC's Tailscale address in Moonlight, see
  [Away from home](away-from-home.md).
- The PC's name in Sunshine must match its Windows name (Sunshine › Configuration ›
  *Sunshine Name*).

**Moonlight shows a PIN and nothing happens.**
The Bridge enters it only if it has a Sunshine login: open the Bridge's Setup page
(tray › Setup…) and check *Pair Moonlight by itself*. Or type the PIN yourself at
https://localhost:47990 › PIN.

**The PC's pointer moves, but clicks or typing don't work in some windows.**
Windows blocks input into windows that run as administrator (installers, Task Manager,
UAC prompts) from normal programs. That's a Windows safety rule.

**The stream stops when I switch to a phone app.**
That's Moonlight's behaviour; Arcis restarts the stream when you come back (switcher
entry *"PC ⟳"*, or Back). Moonlight › Settings › *Picture-in-picture* may keep it
running in a small window instead, depending on the phone.

**Waking the PC doesn't work.**
Turn on Wake-on-LAN in the PC's BIOS/UEFI and in Windows (Device Manager › your
network adapter › Power Management and Advanced tabs). It works best with a network
cable. Arcis needs to have connected to the PC once to learn its network card.
From away, see [waking from away](away-from-home.md#tips).

## Away from home

**Works at home, not away.**
Tailscale must be on, signed in to the same account, on both the phone and the PC.
The PC's tile should then say *Remote*. For Moonlight, add the PC's `100.x.y.z` address
once ([how](away-from-home.md)).
