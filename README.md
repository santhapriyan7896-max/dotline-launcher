# Dotline

A Nothing-inspired Android home screen: black, white and red widget tiles, dot-matrix clock,
round monochrome app icons. Kotlin + Jetpack Compose. Android 8.0+ (tested on a vivo V2541, Android 15).

Dotline is an independent project. It is not made by or affiliated with Nothing Technology.

<!-- Screenshots: add a few images here, e.g. ![Home](docs/home.png) -->

## Privacy

Dotline has no accounts, ads, analytics or tracking. Everything stays on your phone, with one exception:
the **Weather** tile sends your rough location (rounded to about 1 km) to the free
[Open-Meteo](https://open-meteo.com) service. If you don't add the Weather tile, the app never goes online.

Why it asks for each permission:

| Permission | Used for |
|---|---|
| Contacts, call log, phone, make calls | The Phone app: keypad, recents, contacts, call screen |
| Read / send SMS | The Messages app |
| Calendar | The Next event tile |
| Rough location | The Weather tile |
| Notification access | The notification panel, music tile and red notification dots |
| Accessibility | Double-tap to lock and the Dot lock screen. It does not read what's on your screen |
| Alarms, vibrate, full-screen alerts, run at startup | The Clock app's alarms and timers |
| Set wallpaper | Only when you tap **SET AS LOCK SCREEN** |

Each permission is asked for only when you first use the feature that needs it.

## Using it

| Action | What happens |
|---|---|
| Swipe up (or tap the red dot) | App drawer slides up with your finger: 4-column grid, A–Z |
| Swipe down in the drawer | Closes it (from the top of the list, or on the search bar) |
| Swipe down on home | Nothing-style notification panel: Torch / Wi-Fi / Bluetooth / Silent, your notifications (tap to open, swipe sideways to dismiss, Clear all). "System" opens the phone's own panel |
| Double-tap empty space | Lock screen. First time: switch on "Dotline" under Settings → Accessibility |
| Type + Enter | Opens the top result |
| Long-press an app | Add to / remove from home, app info, uninstall |
| Clock tile | Alarms |
| Date tile | Calendar |
| Torch tile | Flashlight on/off |
| Wi-Fi tile | System Wi-Fi panel (Android doesn't let apps switch Wi-Fi directly) |
| Music tile | First tap: turn on "Notification access" for Dotline. Then shows what's playing with previous / play-pause / next |
| Long-press empty space | Menu: all apps, choose home app |
| Home button | Back to a clean home screen |

Home holds up to 8 favourite apps in rows of four.

## Home tiles and settings

Home tiles come in pages: swipe left and right between them. Long-press empty space →
**Launcher settings** to turn tiles on or off and reorder them, set dock icons per row (4 or 5),
12/24-hour clock, white symbols or colour icons, app names, and to see hidden apps.

New tiles each ask for one permission the first time you tap them:
Weather (rough location; data from the free Open-Meteo service), Next event (calendar)
and Photo (pick any photo).

**Lock screen:** Settings → Lock screen.
- **Dot lock screen** (switch): covers vivo's lock screen with a dot-matrix clock, date, battery,
  weather, music controls and notification app icons (no message content). Swipe up, then unlock with
  PIN / fingerprint as usual; unlocking by fingerprint closes it too. It never shows during calls.
  Needs Dotline switched on under Settings → Accessibility (same switch as double-tap to lock).
- **Dot design:** Dots, Ring or Text (your own word). Used as the Dot lock screen's background,
  or tap **SET AS LOCK SCREEN** to use it as a plain lock wallpaper instead (replaces your current one;
  the app tells you if your phone's theme blocks it).

**Notification dots:** a red dot on apps (dock and drawer) with notifications you can dismiss, following
each app's own badge setting. Phone and Messages show missed calls and new texts. Switch off in
Settings → Icons and apps.

Long-press any app → **Change icon** (automatic, colour, or one of ~60 white symbols) or **Hide app**.

## Clock, Calculator and charging

- **Clock** (in the drawer, or tap the big clock tile): alarms (repeat days, label, snooze; ring over the
  lock screen and survive restarts), timer, stopwatch with laps, and world clock.
- **Timer tile** on home: tap for a preset, tap again to pause, long-press to reset.
- **Calculator** (in the drawer): answer in dot-matrix digits as you type.
- **Charging animation:** plug in on home or the lock screen and a dot battery fills to your level.
  Switch off in Settings → Icons and apps.

Default home tiles: Clock, Date, Battery, Torch/Wi-Fi, Next event, Weather, Timer (one page).
Music and Photo can be switched back on in Settings → Home tiles.

## Messages app

"Messages" with the speech-bubble icon in the drawer: read your SMS conversations, search, reply,
start new texts, long-press a message to copy it, and tap **COPY 123456** on verification codes.
Google Messages stays the default SMS app, so it still receives texts, notifies you and handles
RCS and MMS. Unread dots clear when you read the message in Google Messages.

## Phone app

"Phone" in the app drawer: Keypad, Recents (repeated calls grouped, starred contacts on top)
and Contacts.

Tap **SET AS DEFAULT PHONE APP** in it to
also use its call screen (answer / decline, timer, mute, speaker, keypad, hold, SIM choice; works
over the lock screen). If the call screen ever fails, Android falls back to the phone's built-in one.
Switch back any time: Settings → Apps → Default apps → Phone app.

To make it your home screen, tap **SET AS HOME SCREEN →** on the launcher, or go to
Settings → Apps → Default apps → Home screen. Switch back the same way.

## Build and install

With the phone plugged in and USB debugging on:

```bash
gradlew.bat installRelease
```

Use the release build for daily use: the debug build (`installDebug`, or Run in Android Studio)
is several times slower and will feel laggy. After installing, this makes it fully pre-compiled:

```bash
adb shell cmd package compile -m speed -f io.github.santhapriyan7896_max.dotline
```

## Code map

```
app/src/main/java/com/applauncher/
  MainActivity.kt           Entry point; tells the UI when Home is pressed
  LauncherViewModel.kt      App list, favourites, launching
  data/AppRepository.kt     Reads installed apps and icons, builds white "mono" icons
  data/Search.kt            Search ranking (prefix, word start, initials, letters in order)
  data/Prefs.kt             Saved favourites
  SettingsActivity.kt       Launcher settings screen
  data/Prefs.kt, SettingsStore.kt  Saved settings (tiles, dock, icons, hidden apps)
  messages/                 Messages app: conversations, chat, sending
  phone/                    Phone app: dialer, call screen, call service, call notifications
  system/Weather.kt, NextEvent.kt  Data for the weather and next-event tiles
  ui/SymbolIcons.kt         White symbols for apps without a good icon
  ui/mono/HomeTiles.kt      Splits tiles into swipeable pages
  system/NotificationStore.kt  Notifications for the panel (via Notification access)
  system/Torch.kt           Flashlight
  system/MusicWatcher.kt    Now playing + controls (needs Notification access)
  ui/Status.kt              Time, date and battery
  ui/AppIcon.kt             Loads icons in the background
  ui/HomeRole.kt            "Make this my home screen" request
  ui/mono/                  The look: home tiles, drawer grid, round icons, dot-matrix clock, colours
```

## Contributing

Bug reports and pull requests are welcome. Please open an issue first for big changes.
Tell us your phone model and Android version when reporting a bug, since launchers behave
differently across phone makers.

## License

Copyright (C) 2026 santhapriyan7896-max

Dotline is free software: you can redistribute it and/or modify it under the terms of the
[GNU General Public License v3.0](LICENSE) or (at your option) any later version.
