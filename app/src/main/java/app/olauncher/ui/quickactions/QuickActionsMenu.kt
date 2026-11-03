package app.olauncher.ui.quickactions

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
import app.olauncher.data.Constants
import app.olauncher.data.SearchMode
import app.olauncher.databinding.LayoutQuickActionsBinding
import app.olauncher.helper.dpToPx
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.ui.HomeFragment
import kotlin.math.max

/** Bottom-anchored quick actions menu opened by swiping up on the home screen. */
class QuickActionsMenu(
    private val fragment: HomeFragment,
    private val binding: LayoutQuickActionsBinding,
    private val viewModel: MainViewModel,
) {
    private val context = fragment.requireContext()
    private val animationsEnabled = !(context.isSystemAnimationsDisabled() || context.isEinkDisplay())
    private val panel = binding.quickPanel
    private val scrim = binding.quickScrim
    private val baseMargin = 12.dpToPx()
    private val baseTopMargin = 24.dpToPx()

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

}
