package ru.mytv.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Одно установленное приложение. */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val icon: ImageBitmap,
)

object Apps {

    /** Что попадёт в «Избранное» при первом запуске, если такое приложение установлено. */
    private val defaultKeywords = listOf(
        "netflix", "кинопоиск", "kinopoisk", "youtube",
        "vk видео", "vk video", "okko", "wink",
    )

    /** Все приложения, которые можно запустить (и ТВ-шные, и обычные). */
    fun load(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val found = LinkedHashMap<String, AppInfo>()
        val iconPx = (96 * context.resources.displayMetrics.density).toInt().coerceIn(96, 256)

        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in query(pm, intent)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == context.packageName) continue
                if (found.containsKey(ai.packageName)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { ai.packageName }
                val icon = try {
                    ri.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap()
                } catch (e: Exception) {
                    continue
                }
                found[ai.packageName] = AppInfo(ai.packageName, ai.name, label, icon)
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    @Suppress("DEPRECATION")
    private fun query(pm: PackageManager, intent: Intent): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            pm.queryIntentActivities(intent, 0)
        }

    fun defaultFavorites(apps: List<AppInfo>): List<String> {
        val result = mutableListOf<String>()
        for (keyword in defaultKeywords) {
            val app = apps.firstOrNull { it.label.lowercase().contains(keyword) } ?: continue
            if (app.packageName !in result) result.add(app.packageName)
        }
        return result
    }

    fun launch(context: Context, app: AppInfo) {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).setClassName(app.packageName, app.activityName)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(context, intent)
    }

    fun openAppInfo(context: Context, packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        startSafely(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openSettings(context: Context, action: String) {
        startSafely(context, Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun startSafely(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть", Toast.LENGTH_SHORT).show()
        }
    }
}
