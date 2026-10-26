package ru.mytv.launcher

import android.content.Context

/** Сохранённые настройки: тема, цвет подсветки и список избранного. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    /** "light", "dark" или "auto". */
    var theme: String
        get() = sp.getString("theme", "auto") ?: "auto"
        set(value) {
            sp.edit().putString("theme", value).apply()
        }

    var accent: Long
        get() = sp.getLong("accent", Accents[0])
        set(value) {
            sp.edit().putLong("accent", value).apply()
        }

    /** null — избранное ещё ни разу не настраивалось. */
    var favorites: List<String>?
        get() {
            val raw = sp.getString("favs", null) ?: return null
            return raw.split(",").filter { it.isNotBlank() }
        }
        set(value) {
            sp.edit().putString("favs", value?.joinToString(",")).apply()
        }
}
