package app.arcis.input

/**
 * A device that receives typing from the phone's own text field (the keyboard button):
 * Arcis can't type into it through accessibility, so text is relayed.
 */
enum class RemoteTarget(val label: String) {
    COMPUTER("your computer"),
    BLUETOOTH("the Bluetooth device"),
}
