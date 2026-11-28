package com.kihra.maker.data

import java.io.File

/** Looks at a game's files before launch and explains, in plain words, what is likely to go wrong. */
object Compat {
    data class Note(val text: String, val blocking: Boolean)

    fun check(game: Game, rtpInstalled: Boolean): List<Note> {
        val out = mutableListOf<Note>()
        when (game.rgss) {
            Rgss.UNKNOWN -> out += Note("This doesn't look like a supported RPG Maker game, so it can't be started.", true)
            Rgss.EASYRPG -> out += Note(
                "This is an RPG Maker 2000/2003 game. Those need a different engine (EasyRPG) that isn't in the app yet.",
                true,
            )
            else -> {}
        }
        if (game.rgss.isWeb || game.rgss == Rgss.UNKNOWN || game.rgss == Rgss.EASYRPG) return out
        if (game.rtp.isNotBlank() && !rtpInstalled) {
            out += Note(
                "This game uses the ${game.rtp} RTP and it isn't installed. If it can't find its default graphics or sounds, import the RTP in Settings.",
                false,
            )
        }
        if (!game.fromZip) {
            val dir = File(game.path)
            val dlls = dir.listFiles { f -> f.isFile && f.name.endsWith(".dll", true) }?.map { it.name }.orEmpty()
                .filterNot { it.startsWith("RGSS", true) || it.equals("System.dll", true) }
            if (dlls.any { it.contains("steam", true) }) {
                out += Note("This game has Steam files. They can't run on Android; the app skips them, but Steam-only features won't work.", false)
            }
            val other = dlls.filterNot { it.contains("steam", true) }
            if (other.isNotEmpty()) {
                out += Note(
                    "This game ships Windows plugin files (${other.take(3).joinToString()}). Features that rely on them may not work. " +
                        "If it crashes, send me the log.",
                    false,
                )
            }
        }
        return out
    }
}
