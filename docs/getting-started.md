# Getting started

This guide takes you from download to using Arcis on your glasses in about 10 minutes.
No technical knowledge needed.

1. [Check your phone](#1-check-your-phone)
2. [Install the app](#2-install-the-app)
3. [The setup checklist](#3-the-setup-checklist)
4. [Plug in your glasses](#4-plug-in-your-glasses)
5. [First steps](#5-first-steps)
6. [Next: your PC (optional)](#6-next-your-pc-optional)

## 1. Check your phone

Arcis needs:

- **Android 11 or newer.** *Settings › About phone › Software information* shows the
  Android version.
- **Video output over USB-C.** Plug your glasses in *without* Arcis first. If you see
  your phone's screen (or anything at all) in the glasses, you're good.
  If they stay black, your phone can't send video over USB-C; Arcis can't change that.

> **Samsung phones**: if plugging in the glasses starts **Samsung DeX** (a desktop-like
> screen), your phone has video output, so the test passed. For Arcis, turn DeX's
> automatic start off: **Settings › Connected devices › Samsung DeX** › turn off
> *Auto start when HDMI is connected* (wording varies by phone), then reconnect the
> glasses.

## 2. Install the app

Arcis isn't on the Play Store (Google limits apps that use the accessibility features
Arcis needs), so you install it from the download page. This is called "sideloading"
and is safe for apps you trust.

1. On your phone, open the
   [latest release](https://github.com/NICUP14/arcis/releases/latest) and tap
   **Arcis-x.y.z.apk** under *Assets* to download it.
2. Open the downloaded file (from the notification or your *Downloads* / *My Files*
   app).
3. Android asks to allow installing from this source (your browser or file manager).
   Tap **Settings**, turn on **Allow from this source**, go back, and tap **Install**.
4. If Google Play Protect warns that the app is unknown, tap **More details › Install
   anyway**. It warns about every app that isn't from the Play Store.

To update Arcis later, download the new APK and install it the same way. Your settings
are kept.

## 3. The setup checklist

Open **Arcis**. The first time, it shows a short checklist. Each row says what it's for
and has a button that opens exactly the right settings screen. Rows tick themselves
when done.

| Item | Needed? | What it's for |
|---|---|---|
| **Accessibility service** | Required | Lets the phone act as a mouse for apps in the glasses: taps, scrolling, typing, Back. |
| **Moonlight** | Required for a PC | The free app that shows your PC's screen. Skip it if you only want phone apps. |
| **A computer with the Arcis Bridge** | Required for a PC | Pairs your PC (see [Your PC on the glasses](computer.md)). |
| **Notification access** | Optional | Message banners in the glasses, and video controls (play / pause / skip). |
| **Microphone** | Optional | Speak to type (hold the mic button). |
| **Bluetooth** | Optional | Use the phone as the mouse and keyboard of another device. |
| **No battery limits** | Optional, recommended | Stops Android from pausing Arcis while you watch with the phone's screen off. |

### Turning on the accessibility service

1. Tap **Open** next to *Accessibility service*.
2. Find **Arcis controller** (on Samsung: under *Installed apps*) and turn it on.
3. **"Restricted setting"?** Android 13 and newer block this for sideloaded apps
   until you allow it once:
   1. Open your phone's **Settings › Apps › Arcis**.
   2. Tap the **⋮** menu in the top-right corner › **Allow restricted settings** (confirm with
      your PIN or fingerprint).
   3. Go back to the accessibility screen and turn **Arcis controller** on.

Android shows a scary-sounding notice that the app can "observe your actions". That's
what any mouse/keyboard helper needs to click and type for you. Arcis doesn't read or
store what's on your screen, and has no internet servers to send anything to.

When the required items are ticked, tap **Done**. You can open the checklist again
later from **Settings › Phone setup** in the glasses.

## 4. Plug in your glasses

Connect your glasses to the phone's USB-C port. **Arcis Home** appears in the glasses:

- **Favourites**: apps you pinned (long-press, i.e. hold a tap, on any app to pin it)
- **Computers**: your PCs
- **Control another device**: use the phone as a Bluetooth mouse and keyboard
- **Arcis**: Settings and the Gestures card
- **Phone apps**: everything on your phone; type in *Search apps* to find one

At the same time, **your phone becomes a trackpad**: a black screen with a faint line
near the bottom and one along the right edge.

> If Arcis Home doesn't appear, open the Arcis app on the phone once with the glasses
> connected. See [Troubleshooting](troubleshooting.md) if the glasses still just copy
> the phone's screen.

## 5. First steps

Try these on the phone while looking at the glasses:

1. **Move the pointer**: slide one finger anywhere on the phone.
2. **Open an app**: point at it, then tap. Try YouTube.
3. **Scroll**: slide two fingers up or down, or one finger along the right edge.
4. **Go back**: tap with two fingers.
5. **Switch apps**: swipe left or right along the bottom of the phone.
6. **Go Home**: swipe up from the bottom of the phone.
7. **Type**: tap the keyboard button (bottom right) and type on the phone.
8. **Speak**: hold the mic button, speak, let go: your words are typed.

The **Gestures** tile in Arcis Home shows every gesture. [How to use
Arcis](using-arcis.md) explains everything, including the upright and sideways layouts,
drawn shortcuts and video controls.

> **Left-handed?** In the glasses go to **Settings › Hand › Left**. The scroll strip and
> the buttons move to the left side.

## 6. Next: your PC (optional)

To see and control your Windows PC in the glasses, follow
[Your PC on the glasses](computer.md). It takes about 10 minutes, once.
