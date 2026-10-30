# Release shrinking (docs/리팩토링-성능-위생-계획.md, Phase 7-1). R8 removes what
# nothing reaches and changes nothing it keeps: no optimisation (no inlining, no
# class merging, no rewritten method bodies) and no renaming, so the code that
# ships runs exactly as compiled and a stack trace reads without a mapping file.
# kotlinx.serialization and Hilt bring their own keep rules.
-dontoptimize
-dontobfuscate

# The engine's side of the app - process and socket transports, the protocol,
# the parser, the search aggregation - kept whole, not even trimmed of members
# that look unused: nothing about how the engine is driven may change.
-keep class dev.gomoku.zenyth.data.engine.** { *; }
-keep class dev.gomoku.zenyth.domain.engine.** { *; }

-dontwarn okio.**
