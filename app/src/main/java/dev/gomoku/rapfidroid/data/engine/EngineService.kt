package dev.gomoku.rapfidroid.data.engine

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
import dev.gomoku.rapfidroid.R
import dev.gomoku.rapfidroid.core.model.ConnectionState
import dev.gomoku.rapfidroid.core.model.LinkHealth
import dev.gomoku.rapfidroid.domain.repository.EngineRepository
import dagger.hilt.android.AndroidEntryPoint
import dev.gomoku.rapfidroid.core.i18n.tr
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
        startAsForeground(repository.state.value, repository.health.value, repository.thermalThreads.value)
        // A dropped socket now means "reconnecting", not "finished". Stopping
        // the service there would let the process be killed while it waits out
        // the backoff, and the session the user is trying to keep would be the
        // thing that ends it — so the service outlives the socket and only goes
        // away once nobody is trying to reconnect any more.
        lifecycleScope.launch {
            combine(repository.state, repository.health, repository.thermalThreads, ::Triple)
                .collectLatest { (st, health, heat) ->
                    if (st is ConnectionState.Disconnected && !health.reconnecting) {
                        stopSelf()
                    } else {
                        startAsForeground(st, health, heat)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        startAsForeground(repository.state.value, repository.health.value, repository.thermalThreads.value)
        return START_NOT_STICKY
    }

    private fun startAsForeground(state: ConnectionState, health: LinkHealth, heat: Int?) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(state, health, heat),
            when {
                // No daily limit and no onTimeout — see the manifest comment (T2).
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                else -> 0
            },
        )
    }

    private fun buildNotification(state: ConnectionState, health: LinkHealth, heat: Int?): Notification {
        val status = when {
            health.reconnecting && health.retryInSeconds > 0 ->
                tr("재연결 대기 ${health.retryInSeconds}초 (${health.attempt}회째)", "Reconnecting in ${health.retryInSeconds}s (attempt ${health.attempt})", "${health.retryInSeconds} 秒后重新连接 (第 ${health.attempt} 次)")
            health.reconnecting -> tr("재연결 중… (${health.attempt}회째)", "Reconnecting… (attempt ${health.attempt})", "正在重新连接… (第 ${health.attempt} 次)")
            state is ConnectionState.Connecting -> tr("연결 중…", "Connecting…", "连接中…")
            state is ConnectionState.Handshaking -> tr("핸드셰이크…", "Handshake…", "握手中…")
            state is ConnectionState.Ready -> tr("연결됨 · 대기", "Connected · idle", "已连接 · 空闲")
            state is ConnectionState.Thinking -> tr("분석 중…", "Analysing…", "分析中…")
            state is ConnectionState.Error -> tr("오류: ${state.reason}", "Error: ${state.reason}", "错误: ${state.reason}")
            else -> tr("연결 종료", "Disconnected", "连接已结束")
        }
        // The thermal cap (T1) is said here because this is where a user looking at a
        // warm phone will look; the stored thread count is untouched.
        val text = if (heat == null) status else status + tr(" · 발열·절전으로 ${heat} 스레드", " · $heat threads (heat / saver)", " · 因发热/省电 $heat 线程")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.engine_notif_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
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
