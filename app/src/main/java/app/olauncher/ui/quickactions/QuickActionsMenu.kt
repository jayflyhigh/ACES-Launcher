package app.olauncher.ui.quickactions

import android.view.ViewGroup
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = max(900f, ViewConfiguration.get(context).scaledMinimumFlingVelocity * 4f)
    private var settleAnimation: SpringAnimation? = null
    private var homeVelocityTracker: VelocityTracker? = null
    private var homeDownX = 0f
    private var homeDownY = 0f
    private var homeDragging = false
    private var outsideVelocityTracker: VelocityTracker? = null
    private var outsideDownX = 0f
    private var outsideDownY = 0f
    private var outsideDragStartY = 0f
    private var outsideStartTranslation = 0f
    private var outsideDragging = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = hide()
    }

    var isOpen = false
        private set

    init {
        fragment.requireActivity().onBackPressedDispatcher.addCallback(fragment.viewLifecycleOwner, backCallback)
        panel.scrollChild = binding.quickScroll
        panel.maxDragDistance = hiddenOffset()
        panel.onDragStarted = { cancelSettle() }
        panel.onDragProgress = { scrim.alpha = it.coerceIn(0f, 1f) }
        panel.onDragReleased = { velocityY -> settleByPosition(velocityY) }
        scrim.setOnTouchListener { _, event -> onScrimTouch(event) }
        scrim.setOnClickListener { hide() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            applyInsets(insets)
            insets
        }
        initActions()
    }

    fun show() {
        if (isOpen && panel.translationY <= 0f) return
        val wasVisible = panel.isVisible
        ViewCompat.getRootWindowInsets(binding.root)?.let { applyInsets(it) }
        binding.quickScroll.scrollTo(0, 0)
        preparePanelForDrag()
        if (!wasVisible) panel.translationY = hiddenOffset()
        settleTo(true, 0f)
    }

    fun hide(animated: Boolean = animationsEnabled) {
        if (!panel.isVisible) return
        if (!animated || !panel.isLaidOut) {
            finishHide()
            return
        }
        settleTo(false, 0f)
    }

    private fun finishHide() {
        isOpen = false
        backCallback.isEnabled = false
        cancelSettle()
        panel.isVisible = false
        scrim.isVisible = false
        panel.translationY = 0f
        scrim.alpha = 1f
    }

    private fun hiddenOffset(): Float {
        val lp = panel.layoutParams as ViewGroup.MarginLayoutParams
        return (max(panel.height, panel.measuredHeight) + lp.bottomMargin).toFloat()
    }

    private fun preparePanelForDrag() {
        panel.isVisible = true
        scrim.isVisible = true
        val lp = panel.layoutParams as ViewGroup.MarginLayoutParams
        val availableWidth = (binding.root.width - lp.leftMargin - lp.rightMargin).coerceAtLeast(0)
        val availableHeight = (binding.root.height - lp.topMargin - lp.bottomMargin).coerceAtLeast(0)
        if (availableWidth > 0 && availableHeight > 0) {
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(availableHeight, View.MeasureSpec.AT_MOST),
            )
        }
        panel.maxDragDistance = hiddenOffset().coerceAtLeast(panel.measuredHeight.toFloat())
    }

    private fun cancelSettle() {
        settleAnimation?.cancel()
        settleAnimation = null
    }

    private fun settleByPosition(velocityY: Float) {
        val range = hiddenOffset().coerceAtLeast(1f)
        val openFraction = 1f - (panel.translationY / range).coerceIn(0f, 1f)
        val open = if (kotlin.math.abs(velocityY) >= minFlingVelocity) velocityY < 0f
        else openFraction >= 0.5f
        settleTo(open, velocityY)
    }

    private fun settleTo(open: Boolean, velocityY: Float) {
        preparePanelForDrag()
        cancelSettle()
        isOpen = open
        backCallback.isEnabled = open
        val target = if (open) 0f else hiddenOffset()
        if (!animationsEnabled) {
            panel.translationY = target
            updateScrimProgress()
            if (!open) finishHide()
            return
        }
        val spring = SpringAnimation(panel, DynamicAnimation.TRANSLATION_Y).apply {
            spring = SpringForce(target).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                stiffness = 600f
            }
            setStartVelocity(velocityY.coerceIn(-10000f, 10000f))
            addUpdateListener { _, _, _ -> updateScrimProgress() }
            addEndListener { _, canceled, _, _ ->
                if (!canceled && !open && !isOpen) finishHide()
            }
        }
        settleAnimation = spring
        updateScrimProgress()
        spring.start()
    }

    private fun updateScrimProgress() {
        val range = hiddenOffset().coerceAtLeast(1f)
        scrim.alpha = (1f - panel.translationY / range).coerceIn(0f, 1f)
    }

    fun onHomeTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                recycleHomeVelocityTracker()
                homeDownX = event.rawX
                homeDownY = event.rawY
                homeDragging = false
                homeVelocityTracker = VelocityTracker.obtain()
                addRawMovement(homeVelocityTracker, event)
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                addRawMovement(homeVelocityTracker, event)
                val dy = event.rawY - homeDownY
                if (!homeDragging && !panel.isVisible && dy < -touchSlop && kotlin.math.abs(dy) > kotlin.math.abs(event.rawX - homeDownX)) {
                    preparePanelForDrag()
                    binding.quickScroll.scrollTo(0, 0)
                    panel.translationY = hiddenOffset()
                    scrim.alpha = 0f
                    homeDragging = true
                    isOpen = true
                    backCallback.isEnabled = true
                    cancelSettle()
                }
                if (homeDragging) {
                    panel.translationY = (hiddenOffset() + dy).coerceIn(0f, hiddenOffset())
                    updateScrimProgress()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                addRawMovement(homeVelocityTracker, event)
                if (homeDragging) {
                    homeVelocityTracker?.computeCurrentVelocity(1000)
                    settleByPosition(if (event.actionMasked == MotionEvent.ACTION_UP) homeVelocityTracker?.yVelocity ?: 0f else 0f)
                }
                val handled = homeDragging
                homeDragging = false
                recycleHomeVelocityTracker()
                return handled
            }
        }
        return homeDragging
    }

    private fun onScrimTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                recycleOutsideVelocityTracker()
                outsideDownX = event.rawX
                outsideDownY = event.rawY
                outsideDragStartY = event.rawY
                outsideStartTranslation = panel.translationY
                outsideDragging = false
                outsideVelocityTracker = VelocityTracker.obtain()
                addRawMovement(outsideVelocityTracker, event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                addRawMovement(outsideVelocityTracker, event)
                val dy = event.rawY - outsideDownY
                if (!outsideDragging && kotlin.math.abs(dy) > touchSlop &&
                    kotlin.math.abs(dy) > kotlin.math.abs(event.rawX - outsideDownX) &&
                    if (isOpen) dy > 0f else dy < 0f
                ) {
                    preparePanelForDrag()
                    outsideStartTranslation = panel.translationY
                    outsideDragStartY = event.rawY
                    outsideDragging = true
                    cancelSettle()
                }
                if (outsideDragging) {
                    panel.translationY = (outsideStartTranslation + event.rawY - outsideDragStartY).coerceIn(0f, hiddenOffset())
                    updateScrimProgress()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                addRawMovement(outsideVelocityTracker, event)
                if (outsideDragging) {
                    outsideVelocityTracker?.computeCurrentVelocity(1000)
                    val velocity = if (event.actionMasked == MotionEvent.ACTION_UP) outsideVelocityTracker?.yVelocity ?: 0f else 0f
                    settleByPosition(velocity)
                } else if (event.actionMasked == MotionEvent.ACTION_UP) scrim.performClick()
                outsideDragging = false
                recycleOutsideVelocityTracker()
                return true
            }
        }
        return true
    }

    private fun addRawMovement(tracker: VelocityTracker?, event: MotionEvent) {
        if (tracker == null) return
        val copy = MotionEvent.obtain(event)
        copy.setLocation(event.rawX, event.rawY)
        tracker.addMovement(copy)
        copy.recycle()
    }

    private fun recycleHomeVelocityTracker() {
        homeVelocityTracker?.recycle()
        homeVelocityTracker = null
    }

    private fun recycleOutsideVelocityTracker() {
        outsideVelocityTracker?.recycle()
        outsideVelocityTracker = null
    }

    // The panel sits directly above the navigation bar, whether gesture or 3-button navigation
    private fun applyInsets(insets: WindowInsetsCompat) {
        val nav = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
        val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
        val lp = panel.layoutParams as ViewGroup.MarginLayoutParams
        val statusLp = binding.homeStatusPanel.layoutParams as ViewGroup.MarginLayoutParams
        val left = baseMargin + max(nav.left, cutout.left)
        val right = baseMargin + max(nav.right, cutout.right)
        val top = baseTopMargin + max(status.top, cutout.top)
        val bottom = baseMargin + max(nav.bottom, cutout.bottom)
        if (lp.leftMargin != left || lp.rightMargin != right || lp.topMargin != top || lp.bottomMargin != bottom) {
            lp.setMargins(left, top, right, bottom)
            panel.layoutParams = lp
        }
        if (statusLp.leftMargin != left || statusLp.rightMargin != right ||
            statusLp.topMargin != top || statusLp.bottomMargin != bottom
        ) {
            statusLp.setMargins(left, top, right, bottom)
            binding.homeStatusPanel.layoutParams = statusLp
        }
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
