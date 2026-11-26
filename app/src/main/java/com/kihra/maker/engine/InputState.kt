package com.kihra.maker.engine

import java.util.concurrent.atomic.AtomicInteger

/** RGSS3 Input buttons. Bit positions are shared with the native side. */
enum class Button(val bit: Int) {
    UP(0), DOWN(1), LEFT(2), RIGHT(3),
    A(4),   // Enter / Space
    B(5),   // Esc / X / Numpad0
    C(6),   // Enter / Space (confirm)
    X(7), Y(8), Z(9),
    L(10), R(11),
    SHIFT(12),
}

/**
 * Lock-free held-button bitmask. The UI thread writes, the engine thread reads
 * (through JNI later). One int means no allocation and no locking per frame.
 */
object InputState {
    private val mask = AtomicInteger(0)

    fun set(button: Button, down: Boolean) {
        val bit = 1 shl button.bit
        while (true) {
            val old = mask.get()
            val new = if (down) old or bit else old and bit.inv()
            if (mask.compareAndSet(old, new)) return
        }
    }

    fun clear() = mask.set(0)

    /** Called from native code. */
    @JvmStatic
    fun snapshot(): Int = mask.get()
}
