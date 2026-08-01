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
import dev.gomoku.yixindroid.core.model.LinkHealth
import dev.gomoku.yixindroid.domain.repository.EngineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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
        startAsForeground(repository.state.value, repository.health.value)
        // A dropped socket now means "reconnecting", not "finished". Stopping
        // the service there would let the process be killed while it waits out
        // the backoff, and the session the user is trying to keep would be the
        // thing that ends it — so the service outlives the socket and only goes
        // away once nobody is trying to reconnect any more.
        lifecycleScope.launch {
            combine(repository.state, repository.health) { st, health -> st to health }
                .collectLatest { (st, health) ->
                    if (st is ConnectionState.Disconnected && !health.reconnecting) {
                        stopSelf()
                    } else {
                        startAsForeground(st, health)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground(repository.state.value, repository.health.value)
        return START_NOT_STICKY
    }

    private fun startAsForeground(state: ConnectionState, health: LinkHealth) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(state, health),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun buildNotification(state: ConnectionState, health: LinkHealth): Notification {
        val text = when {
            health.reconnecting && health.retryInSeconds > 0 ->
                "재연결 대기 ${health.retryInSeconds}초 (${health.attempt}회째)"
            health.reconnecting -> "재연결 중… (${health.attempt}회째)"
            state is ConnectionState.Connecting -> "연결 중…"
            state is ConnectionState.Handshaking -> "핸드셰이크…"
            state is ConnectionState.Ready -> "연결됨 · 대기"
            state is ConnectionState.Thinking -> "분석 중…"
            state is ConnectionState.Error -> "오류: ${state.reason}"
            else -> "연결 종료"
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
