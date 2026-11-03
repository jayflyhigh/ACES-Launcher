package app.olauncher.data

import androidx.annotation.IdRes
import androidx.annotation.StringRes
import app.olauncher.R

/** Launcher settings that can be pinned to the quick actions menu. */
enum class QuickSetting(
    @StringRes val label: Int,
    val symbol: String,
    @IdRes val targetId: Int,
    val action: Action,
) {
    ALL_SETTINGS(R.string.all_settings, "⚙\uFE0E", 0, Action.SECTION),
    SECTION_HOME(R.string.home_screen, "⌂", R.id.settingsHomeSection, Action.SECTION),
    HOME_APPS_NUM(R.string.apps_on_home_screen, "#", R.id.homeAppsNum, Action.CLICK),
    DATE_TIME(R.string.show_date_time, "◷", R.id.dateTime, Action.CLICK),
    ALIGNMENT(R.string.home_layout_alignment, "≡", R.id.alignment, Action.CLICK),
    SCREEN_TIME(R.string.screen_time, "◔", R.id.screenTimeOnOff, Action.CLICK),
    SECTION_APPEARANCE(R.string.appearance, "◐", R.id.settingsAppearanceSection, Action.SECTION),
    AUTO_KEYBOARD(R.string.auto_show_keyboard, "⌨\uFE0E", R.id.autoShowKeyboard, Action.HIGHLIGHT),
    DAILY_WALLPAPER(R.string.daily_wallpaper_update, "▨", R.id.dailyWallpaper, Action.HIGHLIGHT),
    STATUS_BAR(R.string.notification_bar, "▭", R.id.statusBar, Action.HIGHLIGHT),
    THEME(R.string.theme_mode, "◑", R.id.appThemeText, Action.CLICK),
    BOLD_FONT(R.string.bold_font, "B", R.id.boldFont, Action.HIGHLIGHT),
    TEXT_SIZE(R.string.text_size, "Aa", R.id.textSizeValue, Action.CLICK),
    SECTION_GESTURES(R.string.gestures, "↕", R.id.settingsGesturesSection, Action.SECTION),
    SWIPE_LEFT(R.string.swipe_left_app, "←", R.id.swipeLeftApp, Action.CLICK),
    SWIPE_RIGHT(R.string.swipe_right_app, "→", R.id.swipeRightApp, Action.CLICK),
    DOUBLE_TAP_LOCK(R.string.double_tap_to_lock_screen, "⊙", R.id.toggleLock, Action.HIGHLIGHT),
    HIDDEN_APPS(R.string.hidden_apps, "◌", R.id.olauncherHiddenApps, Action.CLICK),
    DEFAULT_LAUNCHER(R.string.set_as_default_launcher, "✓\uFE0E", R.id.setLauncher, Action.CLICK),
    SEARCH_ENGINES(R.string.search_engines, "⌕", 0, Action.SEARCH_ENGINES);

    enum class Action {
        /** Scroll to the section header */
        SECTION,

        /** Scroll to the row and open its picker/dialog */
        CLICK,

        /** Scroll to an on/off row and highlight it without toggling */
        HIGHLIGHT,

        /** Open the dedicated search engine settings page */
        SEARCH_ENGINES,
    }

    companion object {
        fun from(name: String?): QuickSetting? = entries.firstOrNull { it.name == name }
    }
}
