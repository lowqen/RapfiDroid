## RapfiDroid 0.16.0

**A faster on-device engine that looks after the phone.** The engine now uses
the fast instructions modern phones have, three speed patches carried over from
RapfiWeb, and all of the phone's cores by default. It finds exactly the same
moves with exactly the same evaluations as before, only sooner. In return it
gives cores back when the phone gets hot, and it shuts itself down when you
leave it idle in the background.

### Highlights

* **The engine is about a third faster, same answers.** On ARMv8.2 phones
  (most phones from 2018 on) the app runs a second engine build that uses the
  dot-product instructions, picked automatically. A crash on start falls back to
  the plain build and remembers that. Result-invariant speed patches and
  profile-guided optimisation come on top. On a Galaxy A16, one thread, same
  search, the same positions the app analyses:
  * opening and middle-game positions: **×1.46 – 1.50**;
  * long forced-win searches (VCT/VCF): ×1.17 – 1.20;
  * phones without dot-product: ×1.11.

  The search tree is identical (`BENCH e240ab16`), so analysis results and
  database entries match those of the PC engine and of earlier versions.
* **All cores and a table sized by memory, by default.** The out-of-box engine
  uses every core of the phone (up to 8) and a hash table of 64 / 128 / 256 /
  512 MB by RAM. On the Galaxy A16 that is **×1.74 the nodes per second** of
  0.15's default (3 threads, 128 MB). A thread count or table size you chose
  yourself is kept; the old defaults you never touched move to the new ones.
* **Heat and battery saver take threads back, then return them.** When the
  phone reports it is getting hot the engine drops to half its threads (at
  least 2), to one when it is very hot, and to 2 in battery saver. The engine
  chip at the top of every screen shows a thermometer and the threads in use
  (🌡 4), and so does the notification. Your setting is not changed: the threads
  come back when the phone cools. The new count applies from the next search,
  and changing the thread count no longer reloads the network weights.
* **The node counter keeps moving.** During a long multi-threaded search the
  engine now reports its totals twice a second, so the analysis no longer sits
  on one node count for seconds.
* **Memory is watched.** The engine holds its hash table (plus about 95 MB) from
  the moment it starts. If Android has to kill it for lack of memory, the next
  start uses the next smaller table and Settings says so; picking a hash size
  again lifts that. The database is saved when you leave the app with the
  engine idle, and Settings ▸ On-device engine shows its size, with a warning
  once it reaches 5 % of the phone's memory (it is held in memory as a whole).
* **An idle engine turns itself off.** With the app in the background for three
  minutes and the engine not searching, the engine closes cleanly (its database
  is saved) and the ongoing notification goes away. Come back to the app and it
  starts again within a second. An analysis left running in the background is
  never interrupted — on a Galaxy A16 a 15-minute background analysis kept all
  8 threads on the full cores throughout.

### Android 14 and 15

* The engine's foreground service is now a *special use* service on Android 14
  and later. Android 15 limits *data sync* services to six hours a day; a long
  analysis is no longer subject to that limit.
* Building the game data stays a data sync service. If Android 15's limit is
  reached during a build, the build is cancelled cleanly instead of the app
  being closed; start it again from Settings.

### Under the hood

* Gradle 9.8.2, DataStore 1.3, kotlinx-serialization 1.12, and
  `lifecycle-process` for the background timer.

### Upgrading

* Installs over 0.15.0 with your settings, database and game files intact.
* If you never changed the engine's threads or hash, they become your phone's
  new defaults. Settings ▸ Common ▸ Threads takes them back down.

### Licence and source

The engine is **Rapfi, GPL-3.0-or-later**. The corresponding source for the
engine in this APK is **https://github.com/lowqen/rapfi**, tag
`rapfidroid-v0.16.0` (branch `android`): upstream `3c94c2a9`, two blocks in
`Rapfi/CMakeLists.txt` for the Android build, and the result-invariant patches
(speed, no weight reload on a thread change, periodic node counters) as one
commit each; `android/` holds the build options and the profile the
dot-product build was optimised with.

The app itself is BSD-2-Clause and its interface is a port of Yixin-Board
(BSD-2-Clause, copyright 2009 to 2017 Kai Sun). The network weights and the
opening evaluation grades are CC0 1.0. Full notices:
[LICENSES.md](https://github.com/lowqen/RapfiDroid/blob/main/LICENSES.md), and
inside the app under Settings ▸ About ▸ Licences.
