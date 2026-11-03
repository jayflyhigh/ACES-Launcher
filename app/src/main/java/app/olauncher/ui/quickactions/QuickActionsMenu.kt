package app.olauncher.ui.quickactions

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.QuickSetting
import app.olauncher.data.SearchMode
import app.olauncher.databinding.LayoutQuickActionsBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showToast
import app.olauncher.ui.HomeFragment
import kotlin.math.max

/** Bottom-anchored quick actions menu opened by swiping up on the home screen. */
class QuickActionsMenu(
    private val fragment: HomeFragment,
    private val binding: LayoutQuickActionsBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
) {
    private val context = fragment.requireContext()
    private val animationsEnabled = !(context.isSystemAnimationsDisabled() || context.isEinkDisplay())
    private val panel = binding.quickPanel
    private val scrim = binding.quickScrim
    private val baseMargin = 12.dpToPx()
    private val baseTopMargin = 24.dpToPx()

    private val settingSlots = listOf(
        Triple(binding.quickSetting1, binding.quickSetting1Plus, binding.quickSetting1Symbol),
        Triple(binding.quickSetting2, binding.quickSetting2Plus, binding.quickSetting2Symbol),
        Triple(binding.quickSetting3, binding.quickSetting3Plus, binding.quickSetting3Symbol),
    )

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hide()
    }

    var isOpen = false
        private set

    init {
        fragment.requireActivity().onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner, backCallback)
        panel.scrollChild = binding.quickScroll
        panel.onDismissRequested = { hide() }
        panel.onDragProgress = { scrim.alpha = 1f - it.coerceIn(0f, 1f) }
        scrim.setOnClickListener { hide() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyInsets(insets)
            insets
        }
        initActions()
    }

    fun show() {
        if (isOpen) return
        isOpen = true
        backCallback.isEnabled = true
        refreshSettingTiles()
        ViewCompat.getRootWindowInsets(binding.root)?.let { applyInsets(it) }
        binding.quickScroll.scrollTo(0, 0)

        panel.animate().cancel()
        scrim.animate().cancel()
        scrim.isVisible = true
        panel.isVisible = true

        if (!animationsEnabled) {
            scrim.alpha = 1f
            panel.translationY = 0f
            return
        }
        scrim.alpha = 0f
        // Off-screen until measured, so the panel never flashes at its final position
        panel.translationY = binding.root.height.toFloat()
        panel.doOnPreDraw {
            if (!isOpen) return@doOnPreDraw
            panel.translationY = hiddenOffset()
            panel.animate().translationY(0f).setDuration(260)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
        scrim.animate().alpha(1f).setDuration(200).start()
    }

    fun hide(animated: Boolean = animationsEnabled) {
        if (!isOpen) return
        isOpen = false
        backCallback.isEnabled = false
        panel.animate().cancel()
        scrim.animate().cancel()
        if (!animated || !panel.isLaidOut) {
            finishHide()
            return
        }
        panel.animate().translationY(hiddenOffset()).setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { if (!isOpen) finishHide() }
            .start()
        scrim.animate().alpha(0f).setDuration(200).start()
    }

    private fun finishHide() {
        panel.isVisible = false
        scrim.isVisible = false
        panel.translationY = 0f
        scrim.alpha = 1f
    }

    private fun hiddenOffset(): Float {
        val lp = panel.layoutParams as ViewGroup.MarginLayoutParams
        return (panel.height + lp.bottomMargin).toFloat()
    }

    // The panel sits directly above the navigation bar, whether gesture or 3-button navigation
    private fun applyInsets(insets: WindowInsetsCompat) {
        val nav = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val lp = panel.layoutParams as ViewGroup.MarginLayoutParams
        val left = baseMargin + max(nav.left, cutout.left)
        val right = baseMargin + max(nav.right, cutout.right)
        val top = baseTopMargin + max(status.top, cutout.top)
        val bottom = baseMargin + max(nav.bottom, cutout.bottom)
        if (lp.leftMargin == left && lp.rightMargin == right && lp.topMargin == top && lp.bottomMargin == bottom) return
        lp.setMargins(left, top, right, bottom)
        panel.layoutParams = lp
    }

    private fun initActions() {
        binding.quickWidgets.setOnClickListener { navigate(R.id.action_mainFragment_to_widgetPickerFragment) }

        binding.quickImage.setOnClickListener {
            hide(false)
            openImageShortcut()
        }
        binding.quickImage.setOnLongClickListener {
            showImageShortcutMenu(it)
            true
        }

        settingSlots.forEachIndexed { index, (tile, _, _) ->
            val slot = index + 1
            tile.setOnClickListener {
                val setting = QuickSetting.from(prefs.getQuickSetting(slot))
                if (setting == null) showSettingPicker(slot, it) else openSetting(setting)
            }
            tile.setOnLongClickListener {
                if (QuickSetting.from(prefs.getQuickSetting(slot)) == null) showSettingPicker(slot, it)
                else showSettingOptions(slot, it)
                true
            }
        }

        binding.searchApps.setOnClickListener { openSearch(SearchMode.APPS_ONLY) }
        binding.searchContacts.setOnClickListener { openSearch(SearchMode.CONTACTS_ONLY) }
        binding.searchEngines.setOnClickListener { openSearch(SearchMode.ENGINES_ONLY) }
        binding.searchEngines.setOnLongClickListener {
            navigate(R.id.action_mainFragment_to_searchEnginesFragment)
            true
        }
        binding.searchAll.setOnClickListener { openSearch(SearchMode.ALL_SEARCH) }

        binding.homeSettings.setOnClickListener {
            navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        }
    }

    private fun navigate(actionId: Int, args: android.os.Bundle? = null) {
        hide(false)
        try {
            fragment.findNavController().navigate(actionId, args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun openSearch(mode: SearchMode) {
        if (mode.showsContacts) viewModel.loadContacts()
        navigate(R.id.action_mainFragment_to_universalSearchFragment, bundleOf(Constants.Key.SEARCH_MODE to mode.name))
    }

    // Image shortcut

    private fun openImageShortcut() {
        val packageName = prefs.quickImageAppPackage
        if (packageName.isNotBlank()) {
            if (isPackageInstalled(context, packageName, prefs.quickImageAppUser)) {
                viewModel.selectedApp(
                    AppModel.App(
                        appLabel = prefs.quickImageAppName,
                        key = null,
                        appPackage = packageName,
                        activityClassName = prefs.quickImageAppClassName.ifBlank { null },
                        isNew = false,
                        user = getUserHandleFromString(context, prefs.quickImageAppUser),
                    ),
                    Constants.FLAG_LAUNCH_APP
                )
                return
            }
            context.showToast(R.string.assigned_app_missing)
            prefs.clearQuickImageApp()
        }
        openGallery()
    }

    private fun openGallery() {
        val candidates = listOfNotNull(
            Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_GALLERY),
            context.packageManager.getLaunchIntentForPackage(GOOGLE_PHOTOS_PACKAGE),
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        context.showToast(R.string.gallery_not_found)
    }

    private fun showImageShortcutMenu(anchor: View) {
        val assigned = prefs.quickImageAppPackage.isNotBlank()
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(0, MENU_CHOOSE_APP, 0, R.string.choose_app)
                if (assigned) menu.add(0, MENU_RESTORE_GALLERY, 1, R.string.restore_gallery)
            }
        ) { item ->
            when (item.itemId) {
                MENU_CHOOSE_APP -> {
                    hide(false)
                    fragment.showAppList(Constants.FLAG_SET_QUICK_IMAGE_APP, includeHiddenApps = true)
                }

                MENU_RESTORE_GALLERY -> prefs.clearQuickImageApp()
            }
        }
    }

    // Settings shortcuts

    private fun availableSettings() = QuickSetting.entries.filter {
        it != QuickSetting.SCREEN_TIME || Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun showSettingPicker(slot: Int, anchor: View) {
        val settings = availableSettings()
        anchor.showPopupMenu(
            configure = { menu ->
                settings.forEachIndexed { i, setting ->
                    menu.add(0, i, i, "${setting.symbol}   ${context.getString(setting.label)}")
                }
            }
        ) { item ->
            prefs.setQuickSetting(slot, settings[item.itemId].name)
            refreshSettingTiles()
        }
    }

    private fun showSettingOptions(slot: Int, anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(0, MENU_CHANGE_SETTING, 0, R.string.change_setting)
                menu.add(0, MENU_CLEAR_SETTING, 1, R.string.clear_shortcut)
            }
        ) { item ->
            when (item.itemId) {
                MENU_CHANGE_SETTING -> showSettingPicker(slot, anchor)
                MENU_CLEAR_SETTING -> {
                    prefs.setQuickSetting(slot, null)
                    refreshSettingTiles()
                }
            }
        }
    }

    private fun openSetting(setting: QuickSetting) {
        if (setting.action == QuickSetting.Action.SEARCH_ENGINES)
            navigate(R.id.action_mainFragment_to_searchEnginesFragment)
        else
            navigate(
                R.id.action_mainFragment_to_settingsFragment,
                bundleOf(Constants.Key.SETTING_TARGET to setting.name)
            )
    }

    private fun refreshSettingTiles() {
        settingSlots.forEachIndexed { index, (tile, plus, symbol) ->
            val setting = QuickSetting.from(prefs.getQuickSetting(index + 1))
            plus.isVisible = setting == null
            symbol.isVisible = setting != null
            symbol.text = setting?.symbol
            tile.contentDescription = setting?.let { context.getString(it.label) }
                ?: context.getString(R.string.settings_shortcut)
        }
    }

    private companion object {
        const val GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos"
        const val MENU_CHOOSE_APP = 1
        const val MENU_RESTORE_GALLERY = 2
        const val MENU_CHANGE_SETTING = 3
        const val MENU_CLEAR_SETTING = 4
    }
}
