package com.kihra.maker.data

import android.content.Context
import java.io.File
import java.nio.charset.Charset

/**
 * Looks for an RPG Maker game by reading Game.ini.
 *   RGSS301.dll -> VX Ace, RGSS202E.dll -> VX, RGSS102E.dll -> XP
 */
object GameScanner {

    sealed interface Result {
        data class Ok(val game: Game) : Result
        data class Fail(val reason: String) : Result
    }

    private data class IniInfo(val title: String, val rgss: Rgss, val rtp: String)

    /** Finds the folder holding Game.ini, looking inside sub-folders too (zips often nest). */
    fun findGameRoot(dir: File, maxDepth: Int = 3): File? {
        var level = listOf(dir)
        repeat(maxDepth + 1) {
            level.firstOrNull { findIni(it) != null }?.let { return it }
            level = level.flatMap { d -> d.listFiles { f -> f.isDirectory }?.toList().orEmpty() }
            if (level.isEmpty()) return null
        }
        return null
    }

    private fun findIni(dir: File): File? =
        dir.listFiles { f -> f.isFile && f.name.equals("Game.ini", ignoreCase = true) }?.firstOrNull()

    fun scan(dir: File, extracted: Boolean = false, extractRoot: String = ""): Result {
        if (!dir.isDirectory) return Result.Fail("That folder can't be opened.")
        val root = findGameRoot(dir)
            ?: return Result.Fail("No Game.ini found in that folder. Pick the folder that contains Game.ini.")
        val ini = findIni(root) ?: return Result.Fail("No Game.ini found.")
        val bytes = runCatching { ini.readBytes() }.getOrNull()
            ?: return Result.Fail("Couldn't read Game.ini.")
        val info = describe(bytes, root.name)

        return Result.Ok(
            Game(
                id = root.absolutePath,
                path = root.absolutePath,
                title = info.title,
                rgss = info.rgss,
                rtp = info.rtp,
                addedAt = System.currentTimeMillis(),
                extracted = extracted,
                extractRoot = extractRoot,
            )
        )
    }

    /** A game that stays inside its .zip. The engine's working folder is a save folder in app storage. */
    fun scanZip(context: Context, zip: File, analysis: ZipImporter.Analysis): Result {
        val bytes = analysis.iniBytes ?: return Result.Fail("No Game.ini at the top of the zip.")
        val info = describe(bytes, zip.nameWithoutExtension)
        val saveDir = AppPaths.saveDirFor(context, zip)
        return Result.Ok(
            Game(
                id = zip.absolutePath,
                path = saveDir.absolutePath,
                title = info.title,
                rgss = info.rgss,
                rtp = info.rtp,
                addedAt = System.currentTimeMillis(),
                zipPath = zip.absolutePath,
                zipCompressed = analysis.compressed,
            )
        )
    }

    private fun describe(iniBytes: ByteArray, fallbackTitle: String): IniInfo {
        val values = parseIni(decode(iniBytes))
        val library = values["library"].orEmpty().lowercase()
        val rgss = when {
            "rgss3" in library -> Rgss.VX_ACE
            "rgss2" in library -> Rgss.VX
            "rgss1" in library -> Rgss.XP
            else -> Rgss.UNKNOWN
        }
        val title = values["title"]?.takeIf { it.isNotBlank() } ?: fallbackTitle
        return IniInfo(title, rgss, values["rtp"].orEmpty())
    }

    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        if ('�' !in utf8) return utf8
        // Japanese games usually save Game.ini as Shift-JIS.
        val sjis = runCatching { String(bytes, Charset.forName("windows-31j")) }.getOrNull()
        return sjis ?: String(bytes, Charset.forName("windows-1252"))
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
