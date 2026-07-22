package dev.gomoku.yixindroid.data.engine

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
import dev.gomoku.yixindroid.R
import dev.gomoku.yixindroid.core.model.ConnectionState
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the process (and thus the singleton
 * [EngineRepository] socket) alive during a session — the mobile equivalent of
 * engine.exe running as a persistent relay. It holds no connection itself; it
 * reflects the repository state in an ongoing notification and stops once the
 * connection is Disconnected.
 */
@AndroidEntryPoint
class EngineService : LifecycleService() {

    @Inject
    lateinit var repository: EngineRepository

    override fun onCreate() {
        super.onCreate()
        startAsForeground(repository.state.value)
        lifecycleScope.launch {
            repository.state.collectLatest { st ->
                if (st is ConnectionState.Disconnected) {
                    stopSelf()
                } else {
                    startAsForeground(st)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground(repository.state.value)
        return START_NOT_STICKY
    }

    private fun startAsForeground(state: ConnectionState) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val text = when (state) {
            ConnectionState.Connecting -> "연결 중…"
            ConnectionState.Handshaking -> "핸드셰이크…"
            ConnectionState.Ready -> "연결됨 · 대기"
            ConnectionState.Thinking -> "분석 중…"
            is ConnectionState.Error -> "오류: ${state.reason}"
            ConnectionState.Disconnected -> "연결 종료"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.engine_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "engine"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, EngineService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EngineService::class.java))
        }
    }
}
