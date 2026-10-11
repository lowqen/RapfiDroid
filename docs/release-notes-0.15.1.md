## RapfiDroid 0.15.1

A fix release for 0.15.0.

### Fixes

* **The game no longer hangs on the engine's turn.** After going back to the
  start of a line, replacing the board or switching between Analysis and Play,
  the engine could be sent a single move on a board it no longer shared with the
  app. It refused the move ("Coord is not valid or empty") and never answered,
  so the first move on an empty board seemed not to go down. The next engine
  turn now sends the whole board, and a refused move is retried once with it.
* **A wider eval bar.** 20dp on phones (it was 8dp) with the leading side's
  number, 30dp on wide screens.
* **Settings laid out like RapfiWeb.** A search, a Common card (language,
  threads, dark mode, start the engine with the app, game data), one Advanced
  switch, then the desktop entries grouped in a card per category with just a
  name and a control per row. About and licences at the bottom.
