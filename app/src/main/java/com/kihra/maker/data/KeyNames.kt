package com.kihra.maker.data

import android.view.KeyEvent

/** Keyboard keys a controller button can be mapped to, and the controller buttons themselves. */
object KeyNames {
    val keys: List<Pair<String, Int>> = buildList {
        for (i in 0 until 26) add(('A' + i).toString() to KeyEvent.KEYCODE_A + i)
        for (i in 0 until 10) add(i.toString() to KeyEvent.KEYCODE_0 + i)
        add("Enter" to KeyEvent.KEYCODE_ENTER)
        add("Space" to KeyEvent.KEYCODE_SPACE)
        add("Esc" to KeyEvent.KEYCODE_ESCAPE)
        add("Tab" to KeyEvent.KEYCODE_TAB)
        add("Shift" to KeyEvent.KEYCODE_SHIFT_LEFT)
        add("Ctrl" to KeyEvent.KEYCODE_CTRL_LEFT)
        add("Alt" to KeyEvent.KEYCODE_ALT_LEFT)
        add("Up" to KeyEvent.KEYCODE_DPAD_UP)
        add("Down" to KeyEvent.KEYCODE_DPAD_DOWN)
        add("Left" to KeyEvent.KEYCODE_DPAD_LEFT)
        add("Right" to KeyEvent.KEYCODE_DPAD_RIGHT)
        for (i in 0 until 12) add("F${i + 1}" to KeyEvent.KEYCODE_F1 + i)
    }

    /** Physical controller buttons that can be remapped. */
    val padButtons: List<Pair<String, Int>> = listOf(
        "A" to KeyEvent.KEYCODE_BUTTON_A,
        "B" to KeyEvent.KEYCODE_BUTTON_B,
        "X" to KeyEvent.KEYCODE_BUTTON_X,
        "Y" to KeyEvent.KEYCODE_BUTTON_Y,
        "L1" to KeyEvent.KEYCODE_BUTTON_L1,
        "R1" to KeyEvent.KEYCODE_BUTTON_R1,
        "L2" to KeyEvent.KEYCODE_BUTTON_L2,
        "R2" to KeyEvent.KEYCODE_BUTTON_R2,
        "Start" to KeyEvent.KEYCODE_BUTTON_START,
        "Select" to KeyEvent.KEYCODE_BUTTON_SELECT,
    )

    fun parse(map: String): Map<Int, Int> =
        map.split(',').mapNotNull {
            val p = it.split(':')
            val a = p.getOrNull(0)?.toIntOrNull()
            val b = p.getOrNull(1)?.toIntOrNull()
            if (a != null && b != null) a to b else null
        }.toMap()

    fun format(map: Map<Int, Int>): String = map.entries.joinToString(",") { "${it.key}:${it.value}" }
}
