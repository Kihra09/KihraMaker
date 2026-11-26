package com.kihra.maker.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Library + settings, stored as one JSON file in Android/data/kihra.kihramaker.app/files/. */
class GameStore(context: Context) {
    private val file: File = AppPaths.libraryFile(context)
    private var games: List<Game> = emptyList()
    private var rtp: String = ""

    fun load(): List<Game> {
        if (!file.exists()) return emptyList()
        try {
            val root = JSONObject(file.readText())
            rtp = root.optString("rtpPath", "")
            val arr = root.optJSONArray("games") ?: JSONArray()
            games = (0 until arr.length()).map { Game.fromJson(arr.getJSONObject(it)) }
                .filter { it.path.isNotBlank() }
        } catch (e: Exception) {
            // Never lose data silently: keep the unreadable file next to the new one.
            file.renameTo(File(file.parentFile, "library.json.broken"))
            games = emptyList()
        }
        return games
    }

    fun save(newGames: List<Game>) {
        games = newGames
        write()
    }

    /** Folder holding the RPG Maker RTP files (shared by all games). */
    var rtpPath: String
        get() = rtp
        set(value) {
            rtp = value
            write()
        }

    private fun write() {
        val arr = JSONArray()
        games.forEach { arr.put(it.toJson()) }
        val root = JSONObject().put("version", 1).put("rtpPath", rtp).put("games", arr)
        // Write to a temp file first so a crash can't leave a half-written library.
        val tmp = File(file.parentFile, "library.json.tmp")
        runCatching {
            tmp.writeText(root.toString(2))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }
}
