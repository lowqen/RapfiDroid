package dev.gomoku.zenyth.data.engine

import android.app.ActivityManager
import android.content.Context
import dev.gomoku.zenyth.core.model.DeviceSpec
import java.io.File

/**
 * The two facts the on-device engine's defaults are sized by (R3): how many CPU
 * cores the phone has and how much RAM the system reports. Either one missing
 * makes the whole answer `null`, and the profile falls back to its constants.
 */
object DeviceSpecReader {

    fun read(context: Context): DeviceSpec? {
        val cores = runCatching { DeviceSpec.countCpuList(File(CPU_POSSIBLE).readText()) }.getOrNull()
            ?: Runtime.getRuntime().availableProcessors().takeIf { it > 0 }
            ?: return null
        val ramMb = runCatching {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            info.totalMem / (1024 * 1024)
        }.getOrNull()?.takeIf { it > 0 } ?: return null
        return DeviceSpec(cores = cores, totalRamMb = ramMb)
    }

    private const val CPU_POSSIBLE = "/sys/devices/system/cpu/possible"
}
