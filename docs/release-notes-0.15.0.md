## RapfiDroid 0.15.0

**The RapfiWeb design, back on the phone.** RapfiWeb started as a port of this
app; while it was built the interface was reworked around one idea — the board
first, everything else quieter — and a few features were added. This release
brings all of it back, without dropping anything the app already had.

### Highlights

* **简体中文 — Simplified Chinese.** Every screen, notice, setting and review
  report now speaks Chinese as well as Korean and English, with the desktop
  Yixin's own Chinese terms and the Chinese names of the 26 openings (寒星, 花月,
  浦月 …). It follows the phone's language, or pick it in Settings ▸ Common.

* **Calm, board-first screens.** One-line top bar with the engine's state in a
  word and a theme switch, white cards with hairlines, expert settings folded
  away with their current value in one line, short wording. Pretendard is the
  app font.
* **The engine starts with the app.** No trip to the engine tab first; a switch
  in Settings turns it off.
* **Analysis and Play tabs, each with its own board.** Lines explored in
  analysis no longer leak into the game record. Both boards are saved and come
  back after a restart, results included.
* **A reworked analysis card.** Best move first; the last result stays, dimmed,
  after a stop; analysis time and "play the best move on stop" are settings.
  Analysis shows **one candidate by default** (RapfiWeb's); more are a tap away.
* **Analyse on an empty board plays H8 and stops.** The engine answers the
  empty board at once without a search; that move is placed and the analysis
  ends there. A forced reply is handled the same way.
* **A game card from RapfiWeb.** Colours, time control with typed values, five
  strength levels by nodes, rules, and "Move now". The engine's first black
  move goes straight to the centre.
* **Board values as text only**, coloured blue to red, above the PV ghosts, and
  a vertical eval bar beside the board (20dp on phones with the leading side's
  number, 30dp on wide screens).
* **Position card and Game menu.** Opening name chain and fifth-move grade, the
  move's grade, database value and note; open and save .psq/.sav, copy a
  RapfiWeb link (`rapfiweb.pages.dev/board?p=…`), and links open in the app.
* **Database actions in the DB tab.** The board shows the record; editing the
  note, marking the best move, deleting a position and saving are in the DB
  tab, as in RapfiWeb. A long press on an empty point still writes a cell label.
* **Joseki tab with the official curriculum, shared with RapfiWeb.** Courses
  and lessons to browse or learn by playing, with hints and scores. The
  curriculum is the one RapfiWeb publishes: the app fetches it from
  rapfiweb.pages.dev (and keeps it for offline use), so new lessons appear
  without an app update. This static file is the app's only automatic network
  request and nothing is sent; Settings ▸ Update the joseki curriculum turns
  it off. Lesson links (`rapfiweb.pages.dev/joseki?lesson=…`)
  open in the app, and «링크 복사» copies the same address the web uses. An
  editor for your own joseki, found again from the board's position; files move
  between RapfiWeb and the app unchanged.
* **Game data in seconds.** The RenjuNet build now streams the file as bytes and
  counts positions with a 64-bit Zobrist hash and a radix sort, into one pack.
* **Opening explorer with a board.** Tap to play; graded next moves show their
  shape and played ones their game count.
* **Settings laid out like RapfiWeb.** A search, a Common card (language,
  threads, dark mode, start the engine with the app, game data), one Advanced
  switch, then the desktop entries grouped in a card per category.

### Engine reliability

* **Analysis no longer stops by itself.** Rapfi ignores every command except
  stop while it searches; the app now waits for the engine to finish before it
  sends the next board, for analyses, balance searches and game turns alike.
* **The game no longer hangs on the engine's turn** after going back in a line,
  replacing the board or switching tabs ("Coord is not valid or empty"). The
  next engine turn sends the whole board, and a refused move is retried once.
* Taps are no longer missed while an analysis is running.

### Under the hood

* Build toolchain and libraries brought up to date: Gradle 9.8, Android Gradle
  Plugin 9.4 with its built-in Kotlin, Kotlin 2.4, Compose BOM 2026.09
  (compileSdk 37), Hilt 2.60, coroutines 1.11, serialization 1.11, Navigation
  2.10, Okio 3.18 and others. Dependabot proposes updates every week.

### Upgrading

* The opening packs built by 0.14 are replaced by the new single pack file:
  build the game data again once (Settings ▸ Add game data). Your settings,
  database and game files are untouched.
* If the number of analysis candidates was still the old default (3), it
  becomes 1. A number you chose yourself is kept.
* This build replaces the 0.15.0–0.15.3 test builds published earlier the same
  day and installs over any of them.
