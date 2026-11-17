# Using a computer from Arcis

Picture and sound come from **Moonlight** (phone) + **Sunshine** or **Apollo**
(computer). Your trackpad, keyboard and dictation go through the **Arcis Bridge**.

1. **On the computer**
   - Install [Sunshine](https://github.com/LizardByte/Sunshine) or
     [Apollo](https://github.com/ClassicOldSong/Apollo) and set it up.
   - Run the [Arcis Bridge](bridge.md). On its status page (tray › Status page) enter
     your Sunshine login once, so it can pair Moonlight for you.
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
- **Pairing Moonlight**: Moonlight shows a 4-digit PIN. With the computer connected in
  Arcis and the Sunshine login saved in the bridge, it is entered for you ("Moonlight is
  paired with …" on the glasses). Otherwise enter it in Sunshine's web page
  (https://localhost:47990, PIN tab). Apollo may use a different login page; if the
  bridge can't sign in, enter the PIN by hand.
