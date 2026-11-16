package app.arcis.core.hid

/**
 * USB HID usage codes (keyboard page) and report builders for the combined
 * keyboard + mouse device Arcis presents over Bluetooth. US keyboard layout.
 */
object HidKeys {

    const val MOD_CTRL = 0x01
    const val MOD_SHIFT = 0x02
    const val MOD_ALT = 0x04
    const val MOD_GUI = 0x08 // Windows / Command key

    const val ENTER = 0x28
    const val ESCAPE = 0x29
    const val BACKSPACE = 0x2A
    const val TAB = 0x2B
    const val SPACE = 0x2C
    const val RIGHT = 0x4F
    const val LEFT = 0x50
    const val DOWN = 0x51
    const val UP = 0x52
    const val DELETE = 0x4C // forward delete
    const val MINUS = 0x2D
    const val EQUAL = 0x2E // "=" / "+" key

    const val KEYBOARD_REPORT_ID = 1
    const val MOUSE_REPORT_ID = 2

    /** A key press: HID usage plus modifier bits. */
    data class Stroke(val usage: Int, val modifiers: Int = 0)

    private val unshifted = "1234567890-=[]\\;'`,./"
    private val shifted = "!@#$%^&*()_+{}|:\"~<>?"
    private val symbolUsages = intArrayOf(
        0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, // 1..0
        0x2D, 0x2E, 0x2F, 0x30, 0x31, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, // - = [ ] \ ; ' ` , . /
    )

    /** The key stroke that types [c], or null if it isn't on a US keyboard. */
    /** Usage code of a letter key, A..Z. */
    fun letter(c: Char): Int = 0x04 + (c.uppercaseChar() - 'A')

    fun strokeFor(c: Char): Stroke? = when (c) {
        in 'a'..'z' -> Stroke(0x04 + (c - 'a'))
        in 'A'..'Z' -> Stroke(0x04 + (c - 'A'), MOD_SHIFT)
        ' ' -> Stroke(SPACE)
        '\n' -> Stroke(ENTER)
        '\t' -> Stroke(TAB)
        else -> {
            val i = unshifted.indexOf(c)
            if (i >= 0) {
                Stroke(symbolUsages[i])
            } else {
                // Shifted symbols map 1:1 onto the unshifted keys ("!" is Shift+"1", ...).
                val j = shifted.indexOf(c)
                if (j >= 0) Stroke(symbolUsages[j], MOD_SHIFT) else null
            }
        }
    }

    /** Keyboard input report (without report id): modifiers, reserved, 6 keys. */
    fun keyboardReport(modifiers: Int, usage: Int): ByteArray =
        byteArrayOf(modifiers.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0)

    val keyboardRelease: ByteArray get() = ByteArray(8)

    /**
     * Mouse input reports for a relative move/scroll: the device uses signed 8-bit fields,
     * so large values are split into several reports. Wheel: positive = scroll up.
     */
    fun mouseReports(buttons: Int, dx: Int, dy: Int, wheel: Int = 0): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        var rx = dx
        var ry = dy
        var rw = wheel
        do {
            val sx = rx.coerceIn(-127, 127)
            val sy = ry.coerceIn(-127, 127)
            val sw = rw.coerceIn(-127, 127)
            out += byteArrayOf(buttons.toByte(), sx.toByte(), sy.toByte(), sw.toByte())
            rx -= sx
            ry -= sy
            rw -= sw
        } while (rx != 0 || ry != 0 || rw != 0)
        return out
    }

    /** HID report descriptor: keyboard (report 1) + 3-button mouse with wheel (report 2). */
    val reportDescriptor: ByteArray = intArrayOf(
        // Keyboard
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, KEYBOARD_REPORT_ID,
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
        0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65, 0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
        0xC0,
        // Mouse
        0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, MOUSE_REPORT_ID,
        0x09, 0x01, 0xA1, 0x00,
        0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01, 0x95, 0x03, 0x75, 0x01, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x05, 0x81, 0x03,
        0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38, 0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
        0xC0, 0xC0,
    ).map { it.toByte() }.toByteArray()
}
