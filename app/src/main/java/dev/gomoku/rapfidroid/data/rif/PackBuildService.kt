package dev.gomoku.rapfidroid.data.rif

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.gomoku.rapfidroid.R
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.PackBuildState
import dev.gomoku.rapfidroid.data.engine.EngineService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process alive while [PackBuildManager] works, and shows how far it
 * has got.
 *
 * The build reads 150k games and walks them twice; the screen will go off long
 * before it finishes, and a background process that Android is free to kill
 * would lose everything. The notification is not decoration either — it is the
 * only progress a user sees once they leave the app.
 */
@AndroidEntryPoint
class PackBuildService : LifecycleService() {

    @Inject
    lateinit var manager: PackBuildManager

    override fun onCreate() {
        super.onCreate()
        startAsForeground(manager.state.value)
        lifecycleScope.launch {
            manager.state.collectLatest { state ->
                if (state is PackBuildState.Running) startAsForeground(state) else stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground(manager.state.value)
        return START_NOT_STICKY
    }

    private fun startAsForeground(state: PackBuildState) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            build(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun build(state: PackBuildState): Notification {
        val running = state as? PackBuildState.Running
        val builder = NotificationCompat.Builder(this, EngineService.CHANNEL_ID)
            .setContentTitle(tr("오프닝 데이터 만드는 중", "Building opening data"))
            .setContentText(running?.phase?.label ?: tr("준비 중…", "Starting…"))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val fraction = running?.fraction
        if (fraction != null) {
            builder.setProgress(100, (fraction * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    companion object {
        private const val NOTIF_ID = 1002

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PackBuildService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PackBuildService::class.java))
        }
    }
}
