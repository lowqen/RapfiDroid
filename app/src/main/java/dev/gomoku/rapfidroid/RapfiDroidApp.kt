package dev.gomoku.rapfidroid

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dev.gomoku.rapfidroid.data.engine.EngineIdleWatcher
import dev.gomoku.rapfidroid.data.engine.EngineService
import dev.gomoku.rapfidroid.data.engine.MemoryGuard
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RapfiDroidApp : Application() {

    @Inject
    lateinit var engineIdleWatcher: EngineIdleWatcher

    @Inject
    lateinit var memoryGuard: MemoryGuard

    override fun onCreate() {
        super.onCreate()
        createEngineChannel()
        // Before anything connects: a low-memory kill of the last run lowers the table (T3).
        memoryGuard.checkLastExit()
        engineIdleWatcher.start()
    }

    private fun createEngineChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            EngineService.CHANNEL_ID,
            getString(R.string.engine_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.engine_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
