package app.olauncher.ui.search

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppSearchAppearance
import app.olauncher.data.Prefs
import app.olauncher.data.SearchMode
import app.olauncher.databinding.FragmentAppSearchSettingsBinding
import app.olauncher.databinding.ItemSearchAppBinding
import app.olauncher.databinding.PopupSearchPageMenuBinding
import app.olauncher.databinding.ViewAppSearchColorPickerBinding
import app.olauncher.helper.getColorFromAttr
import app.olauncher.ui.BaseFragment
import java.util.Locale
import kotlin.math.roundToInt

class AppSearchSettingsFragment : BaseFragment() {
    private var _binding: FragmentAppSearchSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var appearance: AppSearchAppearance
    private lateinit var previewAdapter: PreviewAdapter
    private lateinit var tilePicker: AppSearchColorPicker
    private lateinit var backgroundPicker: AppSearchColorPicker
    private lateinit var popupPicker: AppSearchColorPicker
    private var columns = 1
    private var updatingControls = false
    private var previewPopup: PopupWindow? = null
    private var previewPopupBinding: PopupSearchPageMenuBinding? = null
    private val previewBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            previewPopup?.dismiss()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppSearchSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        columns = savedInstanceState?.getInt(EDITING_COLUMNS) ?: prefs.searchTileCount(SearchMode.APPS_ONLY)
        appearance = prefs.appSearchAppearance(columns)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, previewBackCallback)
        previewAdapter = PreviewAdapter()
        binding.previewResults.apply {
            layoutManager = AppSearchLayoutManager(requireContext(), columns)
            adapter = previewAdapter
            itemAnimator = null
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.appSearchSettingsRoot) { root, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            root.updatePadding(top = safe.top, bottom = safe.bottom, left = safe.left, right = safe.right)
            insets
        }
        binding.appSearchSettingsRoot.addOnLayoutChangeListener { root, _, _, _, _, _, _, _, _ ->
            val available = root.height - root.paddingTop - root.paddingBottom
            val height = minOf((250 * resources.displayMetrics.density).roundToInt(), (available * 0.45f).roundToInt())
            if (height > 0 && binding.preview.layoutParams.height != height)
                binding.preview.updateLayoutParams { this.height = height }
        }
        ViewCompat.requestApplyInsets(view)
        tilePicker = AppSearchColorPicker(binding.tileColorPicker) {
            update(appearance.copy(tileColor = it))
        }
        backgroundPicker = AppSearchColorPicker(binding.backgroundColorPicker) {
            update(appearance.copy(backgroundColor = it))
        }
        popupPicker = AppSearchColorPicker(binding.popupColorPicker) {
            update(appearance.copy(popupColor = it))
        }
        binding.decreaseLayout.setOnClickListener { if (columns == 2) chooseLayout(1) }
        binding.increaseLayout.setOnClickListener { if (columns == 1) chooseLayout(2) }
        binding.tileColor.setOnClickListener { togglePicker(binding.tileColorPicker.root) }
        binding.backgroundColor.setOnClickListener { togglePicker(binding.backgroundColorPicker.root) }
        binding.popupColor.setOnClickListener {
            togglePicker(binding.popupColorPicker.root)
            if (binding.popupColorPicker.root.isVisible && previewPopup == null) togglePreviewMenu()
        }
        binding.applyColorToMenu.setOnCheckedChangeListener { _, checked ->
            if (!updatingControls) update(appearance.copy(applyTileColorToMenu = checked))
        }
        binding.transparency.onUserProgress { update(appearance.copy(backgroundTransparency = it)) }
        binding.spacing.onUserProgress { update(appearance.copy(spacingDp = it)) }
        binding.alignment.setOnCheckedChangeListener { _, checkedId ->
            if (!updatingControls) {
                val alignment = when (checkedId) {
                    R.id.alignBottom -> AppSearchAppearance.Alignment.BOTTOM
                    R.id.alignLeft -> AppSearchAppearance.Alignment.LEFT
                    R.id.alignRight -> AppSearchAppearance.Alignment.RIGHT
                    else -> AppSearchAppearance.Alignment.TOP
                }
                update(appearance.copy(alignment = alignment))
            }
        }
        binding.previewMenu.setOnClickListener { togglePreviewMenu() }
        render()
        updatingControls = true
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        render()
    }

    private fun togglePicker(picker: View) {
        picker.isVisible = !picker.isVisible
    }

    private fun chooseLayout(count: Int) {
        columns = count
        appearance = prefs.appSearchAppearance(columns)
        previewPopup?.dismiss()
        val manager = binding.previewResults.layoutManager as AppSearchLayoutManager
        manager.spanCount = columns
        manager.scrollToPositionWithOffset(0, 0)
        render()
    }

    private fun update(value: AppSearchAppearance) {
        appearance = value
        prefs.setAppSearchAppearance(columns, value)
        render()
    }

    private fun render() {
        updatingControls = true
        val context = requireContext()
        binding.editingLayout.setText(if (columns == 1) R.string.one_tile_across else R.string.two_tiles_across)
        binding.decreaseLayout.isEnabled = columns == 2
        binding.increaseLayout.isEnabled = columns == 1
        binding.decreaseLayout.alpha = if (columns == 2) 1f else 0.45f
        binding.increaseLayout.alpha = if (columns == 1) 1f else 0.45f
        tilePicker.setColor(appearance.tileColor ?: context.getColorFromAttr(R.attr.primaryInverseColor))
        backgroundPicker.setColor(appearance.backgroundColor ?: context.getColorFromAttr(R.attr.primaryShadeDarkColor))
        popupPicker.setColor(appearance.popupColor ?: context.getColorFromAttr(R.attr.primaryInverseColor))
        binding.applyColorToMenu.isChecked = appearance.applyTileColorToMenu
        val background = appearance.background(context.getColorFromAttr(R.attr.primaryShadeDarkColor))
        val transparency = appearance.backgroundTransparency ?: (100 - Color.alpha(background) * 100 / 255f).roundToInt()
        binding.transparency.progress = transparency
        binding.transparencyValue.text = getString(R.string.app_search_transparency_value, transparency)
        binding.spacing.max = AppSearchAppearance.MAX_SPACING_DP
        binding.spacing.progress = appearance.spacingDp ?: 8
        binding.spacingValue.text = appearance.spacingDp?.let { getString(R.string.app_search_spacing_value, it) }
            ?: getString(R.string.app_search_default_spacing)
        binding.alignment.check(when (appearance.alignment) {
            AppSearchAppearance.Alignment.BOTTOM -> R.id.alignBottom
            AppSearchAppearance.Alignment.LEFT -> R.id.alignLeft
            AppSearchAppearance.Alignment.RIGHT -> R.id.alignRight
            else -> R.id.alignTop
        })
        updatingControls = false
        binding.preview.setBackgroundColor(background)
        val foreground = if (appearance.backgroundColor != null) contrastColor(background)
            else context.getColorFromAttr(R.attr.primaryColor)
        binding.previewTitle.setTextColor(foreground)
        binding.previewMenu.imageTintList = android.content.res.ColorStateList.valueOf(foreground)
        val manager = binding.previewResults.layoutManager as AppSearchLayoutManager
        val bottom = appearance.alignment == AppSearchAppearance.Alignment.BOTTOM
        if (manager.alignBottom != bottom) {
            manager.alignBottom = bottom
            manager.scrollToPositionWithOffset(0, 0)
        }
        previewAdapter.notifyItemRangeChanged(0, 2)
        binding.previewResults.requestLayout()
        previewPopupBinding?.let {
            previewPopup?.setBackgroundDrawable(appearance.applyMenu(it))
            previewPopup?.update()
        }
    }

    private fun togglePreviewMenu() {
        previewPopup?.let {
            it.dismiss()
            return
        }
        val menu = PopupSearchPageMenuBinding.inflate(layoutInflater)
        val density = resources.displayMetrics.density
        val popup = PopupWindow(
            menu.root,
            minOf((256 * density).roundToInt(), binding.root.width),
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false,
        )
        popup.setBackgroundDrawable(appearance.applyMenu(menu))
        // Keep this preview visible while touches and keyboard input reach the color controls.
        popup.isOutsideTouchable = false
        popup.inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        popup.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        popup.elevation = 8 * density
        menu.tileCount.setText(if (columns == 1) R.string.one_tile_across else R.string.two_tiles_across)
        menu.decreaseTileCount.isEnabled = columns == 2
        menu.increaseTileCount.isEnabled = columns == 1
        menu.decreaseTileCount.alpha = if (columns == 2) 1f else 0.45f
        menu.increaseTileCount.alpha = if (columns == 1) 1f else 0.45f
        // This is a color preview, not a second control for the actual search layout.
        menu.searchPageSettings.setOnClickListener { popup.dismiss() }
        menu.decreaseTileCount.setOnClickListener { chooseLayout(1) }
        menu.increaseTileCount.setOnClickListener { chooseLayout(2) }
        popup.setOnDismissListener {
            previewPopup = null
            previewPopupBinding = null
            previewBackCallback.isEnabled = false
        }
        previewPopup = popup
        previewPopupBinding = menu
        previewBackCallback.isEnabled = true
        popup.showAsDropDown(binding.previewMenu, 0, 0, Gravity.END)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(EDITING_COLUMNS, columns)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        previewPopup?.dismiss()
        binding.previewResults.adapter = null
        super.onDestroyView()
        _binding = null
    }

    private inner class PreviewAdapter : RecyclerView.Adapter<PreviewHolder>() {
        private val context = requireContext()
        private val packageManager = context.packageManager
        private val settings = Intent(Settings.ACTION_SETTINGS).resolveActivityInfo(packageManager, 0)
        private val samples = listOf(
            getString(R.string.app_name) to context.applicationInfo.loadIcon(packageManager),
            (settings?.loadLabel(packageManager)?.toString() ?: getString(R.string.app_search_sample_settings)) to
                (settings?.loadIcon(packageManager)
                    ?: requireNotNull(ContextCompat.getDrawable(context, android.R.drawable.ic_menu_preferences))),
        )

        override fun getItemCount() = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PreviewHolder(ItemSearchAppBinding.inflate(layoutInflater, parent, false))

        override fun onBindViewHolder(holder: PreviewHolder, position: Int) {
            val (label, icon) = samples[position]
            holder.bind(label, icon)
        }
    }

    private inner class PreviewHolder(private val row: ItemSearchAppBinding) : RecyclerView.ViewHolder(row.root) {
        private val style = AppSearchTileStyle(row)

        fun bind(label: String, icon: Drawable) {
            row.itemTitle.text = label
            row.itemIcon.setImageDrawable(icon)
            style.apply(appearance, columns, prefs.appLabelAlignment)
        }
    }

    private companion object {
        const val EDITING_COLUMNS = "editing_app_search_columns"
    }
}

private fun SeekBar.onUserProgress(onChange: (Int) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    })
}

private class AppSearchColorPicker(
    private val binding: ViewAppSearchColorPickerBinding,
    private val onChange: (Int) -> Unit,
) {
    private var color: Int? = null
    private var updating = false

    init {
        listOf(binding.hex, binding.red, binding.green, binding.blue).forEach { it.isSaveEnabled = false }
        listOf(binding.red, binding.green, binding.blue).forEach { slider ->
            slider.onUserProgress {
                val color = Color.rgb(binding.red.progress, binding.green.progress, binding.blue.progress)
                setColor(color)
                onChange(color)
            }
        }
        binding.hex.doAfterTextChanged { text ->
            if (!updating) {
                val hex = text.toString().removePrefix("#")
                if (hex.matches(Regex("[0-9a-fA-F]{6}"))) {
                    val value = hex.toInt(16) or Color.BLACK
                    color = value
                    updateChannels(value)
                    binding.hex.error = null
                    onChange(value)
                } else {
                    binding.hex.error = binding.root.context.getString(R.string.app_search_invalid_color)
                }
            }
        }
    }

    fun setColor(value: Int) {
        val opaque = value or Color.BLACK
        if (color == opaque) return
        color = opaque
        updating = true
        binding.hex.setText(String.format(Locale.ROOT, "#%06X", opaque and 0xffffff))
        binding.hex.error = null
        updateChannels(opaque)
        updating = false
    }

    private fun updateChannels(value: Int) {
        binding.red.progress = Color.red(value)
        binding.green.progress = Color.green(value)
        binding.blue.progress = Color.blue(value)
    }
}
