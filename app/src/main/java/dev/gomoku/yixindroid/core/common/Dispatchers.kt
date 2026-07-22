package dev.gomoku.yixindroid.core.common

import javax.inject.Qualifier

/** General IO work (repository fan-out, DataStore). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * The **single-threaded** socket dispatcher. All reads and writes on one engine
 * connection run here so byte ordering to the server is deterministic.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class EngineDispatcher
