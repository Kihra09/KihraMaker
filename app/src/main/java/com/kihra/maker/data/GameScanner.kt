package com.kihra.maker.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.nio.charset.Charset

/**
 * Checks a picked folder for an RPG Maker game by reading Game.ini.
 *   RGSS301.dll -> VX Ace, RGSS202E.dll -> VX, RGSS102E.dll -> XP
 */
object GameScanner {

    sealed interface Result {
        data class Ok(val game: Game) : Result
        data class Fail(val reason: String) : Result
    }

    fun scan(context: Context, treeUri: Uri): Result {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return Result.Fail("Can't open that folder.")

        val ini = root.findFile("Game.ini")
            ?: return Result.Fail("No Game.ini found. Pick the folder that contains Game.ini.")

        val bytes = runCatching {
            context.contentResolver.openInputStream(ini.uri)?.use { it.readBytes() }
        }.getOrNull() ?: return Result.Fail("Couldn't read Game.ini.")

        val values = parseIni(decode(bytes))
        val library = values["library"].orEmpty().lowercase()
        val rgss = when {
            "rgss3" in library -> Rgss.VX_ACE
            "rgss2" in library -> Rgss.VX
            "rgss1" in library -> Rgss.XP
            else -> Rgss.UNKNOWN
        }

        val title = values["title"]?.takeIf { it.isNotBlank() } ?: root.name ?: "Untitled game"

        return Result.Ok(
            Game(
                id = treeUri.toString(),
                treeUri = treeUri.toString(),
                title = title,
                rgss = rgss,
                rtp = values["rtp"].orEmpty(),
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if ('�' in utf8) String(bytes, Charset.forName("windows-1252")) else utf8
    }

    private fun parseIni(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (line in text.lineSequence()) {
            val trimmed = line.trim().removePrefix("﻿")
            if (trimmed.isEmpty() || trimmed.startsWith("[") || trimmed.startsWith(";")) continue
            val i = trimmed.indexOf('=')
            if (i <= 0) continue
            val key = trimmed.substring(0, i).trim().lowercase()
            if (key !in out) out[key] = trimmed.substring(i + 1).trim()
        }
        return out
    }
}
