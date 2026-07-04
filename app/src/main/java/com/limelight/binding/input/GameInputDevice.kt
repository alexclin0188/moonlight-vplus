package com.limelight.binding.input

/**
 * Generic Input Device
 */
interface GameInputDevice {
    /**
     * Device-specific menu option shown in the game menu.
     */
    data class MenuOption(
        val label: String,
        val persistent: Boolean,
        val action: () -> Unit,
        val identifier: String?,
        val enabled: Boolean = true
    )

    /**
     * @return list of device specific game menu options, e.g. configure a controller's mouse mode
     */
    fun getGameMenuOptions(): List<MenuOption>
}
