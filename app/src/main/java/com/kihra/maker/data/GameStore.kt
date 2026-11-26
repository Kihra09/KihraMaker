package com.kihra.maker.data

import android.content.Context
import org.json.JSONArray

class GameStore(context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    fun load(): List<Game> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Game.fromJson(arr.getJSONObject(it)) }
                .filter { it.path.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    fun save(games: List<Game>) {
        val arr = JSONArray()
        games.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    /** Folder holding the RPG Maker RTP files (shared by all games). */
    var rtpPath: String
        get() = prefs.getString(RTP_KEY, "").orEmpty()
        set(value) { prefs.edit().putString(RTP_KEY, value).apply() }

    private companion object {
        const val KEY = "games"
        const val RTP_KEY = "rtpPath"
    }
}
