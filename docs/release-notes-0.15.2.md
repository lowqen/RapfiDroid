## RapfiDroid 0.15.2

A fix release for 0.15.

### Fixes

* **Analysis no longer stops by itself.** Rapfi ignores every command except
  stop while it is searching, and only leaves that state once the search has
  actually wound down. The app sent the new board right behind the stop, so
  every move made during an analysis was lost to the engine and the analysis
  seemed to halt. The app now waits for the engine to report its move before
  sending anything else — for analyses, balance searches and game turns alike.
* **Analyse on an empty board puts H8 down and keeps reading.** Rapfi answers
  the empty board instantly without a search; that answer is now played, as is
  any other instant answer, and the analysis goes on.
* **Taps are no longer missed while an analysis is running.**
* The game no longer stalls on the engine's turn right after switching from
  Analysis to Play.
