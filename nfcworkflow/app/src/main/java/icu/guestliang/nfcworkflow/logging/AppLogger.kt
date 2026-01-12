package icu.guestliang.nfcworkflow.logging

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import androidx.core.content.edit

object AppLogger {
    enum class Level(val priority: Int) { DEBUG(1), INFO(2), WARN(3), ERROR(4) }

    private const val SP_NAME = "app_logger"
    private const val KEY_MIN_LEVEL = "min_level"

    // 读取/设置最小输出级别（默认 INFO）
    fun getMinLevel(context: Context): Level {
        val name = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MIN_LEVEL, Level.INFO.name) ?: Level.INFO.name
        return runCatching { Level.valueOf(name) }.getOrElse { Level.INFO }
    }

    fun setMinLevel(context: Context, level: Level) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
            .edit { putString(KEY_MIN_LEVEL, level.name) }
    }

    private const val LOG_DIR = "logs"
    private val headerDateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun dayFile(context: Context): File {
        val dir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
        val name = "NFCWorkFlow_${dayFmt.format(Date())}.log"
        val f = File(dir, name)
        if (!f.exists()) {
            f.createNewFile()
            f.appendText("FILE: ${f.name}\n")
            f.appendText("CREATED_AT: ${headerDateFmt.format(Date())}\n")
        }
        return f
    }

    private fun write(context: Context, level: Level, tag: String? = null, msg: String) {
        val f = dayFile(context)
        val ts = headerDateFmt.format(Date())
        val tagPart = tag?.let { " [$it]" } ?: ""
        f.appendText("[$ts] [${level.name}]$tagPart $msg\n")
    }

    fun debug(context: Context, msg: String, tag: String? = null) = write(context, Level.DEBUG, tag, msg)
    fun info(context: Context, msg: String, tag: String? = null) = write(context, Level.INFO, tag, msg)
    fun warn(context: Context, msg: String, tag: String? = null) = write(context, Level.WARN, tag, msg)

    fun error(context: Context, throwable: Throwable, msg: String? = null, tag: String? = null) =
        write(context, Level.ERROR, tag, buildString {
            if (!msg.isNullOrBlank()) append(msg).append(": ")
            append(throwable.javaClass.simpleName).append(": ").append(throwable.message ?: "")
            append("\n").append(throwable.stackTraceToString())
        })

    fun getCurrentLogFile(context: Context): String {
        val f = dayFile(context)
        return f.readText()
    }
}
