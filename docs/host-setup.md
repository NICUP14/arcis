# Using a computer from Arcis

Picture and sound come from **Moonlight** (phone) + **Sunshine** or **Apollo**
(computer). Your trackpad, keyboard and dictation go through the **Arcis Bridge**.

1. **On the computer**
   - Install [Sunshine](https://github.com/LizardByte/Sunshine) or
     [Apollo](https://github.com/ClassicOldSong/Apollo) and set it up.
   - Run the [Arcis Bridge](bridge.md). Its **Setup** page opens by itself: it can
     install Sunshine, give the bridge a Sunshine login (so it pairs Moonlight for you)
     and install VB-Cable for the microphone.
2. **On the phone**
   - Install **Moonlight**.
   - In Arcis Home on the glasses, open **Computers** › **Add computer** (or the
     computer, if it was found) › **Scan code with the phone**, and scan the code from
     the bridge's tray › *Pair a phone…*. Or type the PIN shown there.
   - Open the computer's tile. The first time, Moonlight asks to pair and shows a PIN:
     Arcis reads it and the bridge enters it into Sunshine, nothing to type.
3. **Use it**: click the computer's tile. Arcis connects the bridge and starts (or
   resumes) the computer's **Desktop** stream in Moonlight on the glasses, skipping
   Moonlight's menus. While the stream is in front:
   - the trackpad, clicks, scrolling, pinch-to-zoom and drawn shortcuts control the
     computer (Ctrl, or Cmd on a Mac). Arcis Home → **Gestures** shows each stroke: keys
     (< Backspace, > Tab, ↵ Enter, ⊔ Space, D Delete, straight lines = arrows) and
     shortcuts (C copy, V paste, \ cut, Z undo, Z backwards redo, ∧ select all, S save,
     magnifier find, 7 Alt+Tab, W Windows key);
   - type with the keyboard button, or hold the mic button to dictate (both bottom right);
   - the computer shows its own pointer, so Arcis hides the glasses cursor.
   Switch away like any app (bottom edge); the bridge stays connected. In Moonlight's
   own menus (computer and app lists) the trackpad works them like any phone app.

   Moonlight finds the computer by name: if Sunshine/Apollo uses a different name than
   the computer (Sunshine → Configuration → Sunshine Name), Moonlight reports that it
   can't find it. Use the same name, or open Moonlight from the apps grid instead.

## Tips

- **Sharper picture**: Moonlight streams at 720p by default. For 1080p glasses such as
  the XREAL 1S, set Moonlight → Settings → Video resolution to 1080p.
- **Samsung phones**: Game Booster treats Moonlight as a game and adds a "touch lock"
  button to the navigation bar. If the phone stops reacting, drag the lock icon to
  unlock, or turn Game Booster off for Moonlight (Game Booster settings → Block during
  game / app list).
- **One tap from sleep**: opening a computer whose bridge doesn't answer sends
  Wake-on-LAN and keeps trying every 2 s for up to a minute (the tile shows *Waking…*);
  the stream starts as soon as the computer is up. If it doesn't wake: check it's plugged
  in and Wake-on-LAN is enabled (BIOS/UEFI and network adapter). Arcis learns a computer's
  network card once it has connected to it at home.
- **Status bar**: over apps on the glasses, the top-right corner shows the time, the
  phone's battery, the connected computer (green dot), the microphone while it's live
  (red dot) and media mode. It hides over a computer's full-screen stream (a bottom-edge
  swipe shows it for 3 s) and can be turned off in Arcis Settings.
- **Message banners**: messages and calls pop up at the top of the glasses for 4 s (app
  icon, sender, one line). While one is up, a two-finger tap opens it on the glasses and a
  swipe up dismisses it. Ongoing and silent notifications are skipped. Arcis Settings: on /
  off, and *Streaming* to show them only while a computer or a video is in front. Needs
  notification access; nothing is ever sent to the computer.
- **Media mode**: when the app in front on the glasses is playing a video (Netflix,
  YouTube…), the trackpad controls it without aiming: tap = play/pause, swipe left/right =
  seek (±10 s, more for longer swipes), right edge up/down = volume, long-press = pointer
  back. The phone shows **▶ Media** while it's on (tap it to turn it off or on). Needs
  notification access (phone setup checklist). For the computer's stream it's off by
  default: tap **▶ Media off** to send play/pause, arrow keys (seek) and volume keys to
  the computer.
- **Switching away from the computer**: Moonlight ends its stream whenever another app
  covers it (its own design; the computer's Desktop keeps running). Arcis brings it back:
  the switcher lists the computer as **MSI ⟳**, and switching to it, or pressing Back
  from the other app, restarts the stream in a second or two. If the glasses are
  unplugged and come back within 10 minutes, whatever was in front (an app or the
  computer) reopens by itself. Moonlight → Settings → *Picture-in-picture* may keep the
  stream alive in a small window instead (depends on the phone).
- **Pairing Moonlight**: Moonlight shows a 4-digit PIN. With the computer connected in
  Arcis and the Sunshine login saved in the bridge, it is entered for you ("Moonlight is
  paired with …" on the glasses). Otherwise enter it in Sunshine's web page
  (https://localhost:47990, PIN tab). Apollo may use a different login page; if the
  bridge can't sign in, enter the PIN by hand.
