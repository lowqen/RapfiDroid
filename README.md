<div align="center">

# RapfiDroid

**A gomoku and renju board with a real engine running on your phone.**
**No server. No account. No internet.**

[![Build](https://github.com/lowqen/RapfiDroid/actions/workflows/build.yml/badge.svg)](https://github.com/lowqen/RapfiDroid/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/lowqen/RapfiDroid?include_prereleases&sort=semver)](https://github.com/lowqen/RapfiDroid/releases/latest)
[![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSES.md)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3DDC84)](https://github.com/lowqen/RapfiDroid/releases/latest)

[Download](#download) · [Features](#features) · [How it works](#how-the-engine-runs-on-your-phone) · [Build](#building-from-source) · [한국어](README.ko.md)

</div>

## What this is

Strong gomoku engines are desktop programs. They expect a PC, a console, and
usually a second machine to talk to. RapfiDroid takes one of the strongest open
engines available, [Rapfi](https://github.com/dhbloo/rapfi), compiles it for
arm64 Android, and runs it as a real process inside the app. The board, the
analysis, the forbidden point marking and the position database all work in
airplane mode, on a phone, with nothing listening on a port anywhere.

The interface is a port of the desktop
[Yixin-Board](https://github.com/dhbloo/Yixin-Board) study environment, so the
full set of engine parameters and study tools came across rather than a reduced
mobile subset. All 67 desktop settings are present and they drive the engine the
same way they do on a PC.

## Download

Get the APK from the [latest release](https://github.com/lowqen/RapfiDroid/releases/latest).
Android will ask once for permission to install from an unknown source.

For update notifications without a store, install
[Obtainium](https://github.com/ImranR98/Obtainium) and add this repository URL.
New releases then arrive as a notification and update with one tap.

**Requirements**

* Android 8.0 (API 26) or newer
* An **arm64** device. Older armeabi and x86 devices cannot run the engine.
* About 90 MB of storage. The neural network weights ship inside the APK, so
  nothing is downloaded on first run.

The interface is available in English and Korean.

## Features

### Play and analyse

* Play against the engine at any strength, or let it analyse while you explore.
* Live principal variation, depth, node count, evaluation bar and win rate.
* Renju forbidden points marked as you play, with the double three, double four
  and overline rules applied by the engine itself rather than approximated.
* Freestyle, standard and renju rules, each with its own network.
* A board value overlay that colours every legal point by the engine's judgement.
* Board image export, position copy and paste in the desktop text format, undo,
  redo and branch navigation.

### Study

* **Game review.** Walk a finished game move by move with the engine marking
  where the evaluation actually turned.
* **Opening explorer.** 249 named openings across the first four moves, an
  eleven grade evaluation of Black's fifth move covering 1,889 shapes, and the
  list of transpositions that reduce to the same position.
* **Rankings.** The 26 classical openings with direct and indirect filters, plus
  fifth move shape frequency.
* **Position proving.** Ask the engine to prove a position rather than only
  evaluate it, with a depth budget you control.
* **Move sequence explorer** and an engine console for raw protocol commands.

### Keep what it learns

* The engine writes what it learns to a database file on your phone and picks it
  up again next time. The app sends the save command before shutting the engine
  down, so a session is never lost to a forced kill.
* Import and export game records, and the same `settings.txt` and
  `settings_dev.txt` files the desktop uses.

## How the engine runs on your phone

This is the part that is not obvious, so it is worth stating plainly.

Since Android 10 an app may only execute a file from the directory the package
manager extracted its native libraries into, and only if that file is named
`lib*.so`. So the engine is built as an ordinary position independent executable
named `libengine.so`, packaged with legacy jniLibs packaging so the package
manager really does extract it, and started as a **separate process** over stdin
and stdout using the same piskvork protocol the desktop speaks.

A separate process rather than JNI, for four reasons that all bite in practice:

1. Rapfi calls `exit` when it cannot allocate its transposition table. Inside the
   app process that is a crash with no stack trace.
2. The engine changes its working directory. A library that does this to its host
   breaks unrelated code.
3. A search must be cancellable without unwinding the app.
4. It keeps a clean boundary between GPL-3.0 code and this BSD-2-Clause app.

The build is verified rather than assumed. Rapfi's search result is independent
of the instruction set it was compiled for, because upstream compiles with
`-ffp-contract=off`, so the `BENCH` command must print the same hash `e240ab16`
on the phone as on an x86 host. The build script refuses to emit a binary that is
not aarch64, that depends on `libc++_shared`, or that lost any of those flags.
See [tools/build_engine_android.sh](tools/build_engine_android.sh).

## Real game statistics

The opening explorer and rankings show win rates from real tournament play once a
game database is present. That database is
[RenjuNet](https://www.renju.net/game/), and its licence permits offline personal
use while forbidding its contents, **or anything derived from them**, in any
website or online system.

So the data is not bundled, is not hosted, and is not fetched. Instead the app
carries the whole conversion pipeline and builds the packs on the device:

> Settings ▸ **Add game data** ▸ open renju.net ▸ pick the XML file you
> downloaded ▸ Build

You download the file yourself from the official source, your phone converts it,
and the result never leaves the device. That is the one path the licence allows,
and it is why there is no download button that does it for you.

The conversion is a full port of the desktop Python pipeline, including the
canonical position key, which is cross checked against four independent
implementations. The hot loop computes a canonical key for every prefix of every
game, roughly 3.3 million positions for a typical database, using an incremental
scanner that keeps eight symmetry orderings in sorted integer arrays and never
allocates a string until a position is actually kept.

## Server mode

The original architecture talked to Rapfi on a PC over the network, and that mode
is still here for the cases where a phone genuinely cannot compete: a very large
opening database, or an analysis you want to leave running for hours.

It is **off by default and hidden behind an advanced switch.** Nothing in this
app contacts the network unless you turn it on and enter an address yourself.

## Building from source

```bash
git clone https://github.com/lowqen/RapfiDroid.git
cd RapfiDroid
./gradlew testDebugUnitTest assembleDebug
```

Android Studio Ladybug or newer, JDK 17, compileSdk 35. The repository carries a
prebuilt `libengine.so` so a fresh clone builds and runs immediately. To rebuild
the engine yourself, [tools/build_engine_android.sh](tools/build_engine_android.sh)
does everything from installing the NDK to verifying the resulting binary, and
[app/src/main/jniLibs/ENGINE_VERSION.txt](app/src/main/jniLibs/ENGINE_VERSION.txt)
records exactly which engine commit the committed binary came from.

Unit tests run on every push. They cover the engine grammar, the position keys,
the pack readers and writers, the settings codec and the opening tables, and they
are the reason a refactor of the canonical key is safe to make at all.

### Layout

```
app/src/main/java/dev/gomoku/rapfidroid/
  core/         board model, position keys, settings table, design system
  data/         engine transports, stores, pack and database IO
  domain/       engine session, RIF parsing, aggregation, pack writing
  feature/      board, research, database, connection, settings, onboarding
tools/          engine cross compilation and verification
fdroid/         F-Droid metadata and build recipe
docs/           publishing, licence notes, release notes
```

## Releasing

Push a tag and CI does the rest, including two gates that fail the release rather
than let a bad APK out: one that refuses any build containing RenjuNet derived
data, and one that checks the engine is present and packaged so Android can
actually execute it. See [docs/publishing.md](docs/publishing.md).

## Licence

RapfiDroid's own source is **BSD-2-Clause**. The distributed APK also contains
the Rapfi engine, which is **GPL-3.0-or-later**, so the package as a whole is
conveyed under GPL-3.0-or-later.

The corresponding source for the engine in the released APK is
**https://github.com/lowqen/rapfi**, branch `android`. The only change from
upstream is two blocks in `Rapfi/CMakeLists.txt` for the Android build.

Full third party notices are in [LICENSES.md](LICENSES.md), and the GPL-3.0 and
BSD-2-Clause texts ship inside the app under Settings ▸ About ▸ Licences.

This app cannot be published on Google Play. That is a consequence of GPL-3.0
section 6 and section 10 against the Play distribution terms, not a choice.

## Credits

* **[dhbloo](https://github.com/dhbloo)** for [Rapfi](https://github.com/dhbloo/rapfi),
  the engine that makes this worth using, and for the
  [Yixin-Board](https://github.com/dhbloo/Yixin-Board) fork this interface follows.
* **Kai Sun** for the original
  [Yixin-Board](https://github.com/accreator/Yixin-Board), BSD-2-Clause,
  copyright 2009 to 2017.
* **[rapfi-networks](https://github.com/dhbloo/rapfi-networks)** for the neural
  network weights, CC0 1.0.
* **[RenjuNet](https://www.renju.net/)** for the game database that users build
  their own statistics from.

No permission was required from any of them, and none was sought. What their
licences ask for is notice and source, and both are given above.
