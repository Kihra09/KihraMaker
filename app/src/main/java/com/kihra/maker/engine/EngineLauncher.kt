package com.kihra.maker.engine

import android.content.Context
import android.content.Intent
import com.kihra.maker.data.Game

object EngineLauncher {
    const val EXTRA_TREE_URI = "treeUri"
    const val EXTRA_TITLE = "title"
    const val EXTRA_SCALE = "scaleMode"
    const val EXTRA_FPS_CAP = "fpsCap"
    const val EXTRA_SHOW_FPS = "showFps"
    const val EXTRA_CONTROLS_OPACITY = "controlsOpacity"

    fun launch(context: Context, game: Game) {
        val intent = Intent(context, GameActivity::class.java)
            .putExtra(EXTRA_TREE_URI, game.treeUri)
            .putExtra(EXTRA_TITLE, game.title)
            .putExtra(EXTRA_SCALE, game.settings.scaleMode.name)
            .putExtra(EXTRA_FPS_CAP, game.settings.fpsCap)
            .putExtra(EXTRA_SHOW_FPS, game.settings.showFps)
            .putExtra(EXTRA_CONTROLS_OPACITY, game.settings.controlsOpacity)
        context.startActivity(intent)
    }
}
