package app.olauncher.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.databinding.FragmentWidgetPickerBinding
import app.olauncher.databinding.ItemSearchHeaderBinding
import app.olauncher.databinding.ItemWidgetProviderBinding
import app.olauncher.helper.dpToPx
import app.olauncher.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

/** Lists installed widget providers grouped by app; picking one starts the bind/configure flow. */
class WidgetPickerFragment : BaseFragment() {

    private var _binding: FragmentWidgetPickerBinding? = null
    private val binding get() = _binding!!
    private val previewCache = LruCache<String, Drawable>(40)

    private sealed class Row {
        data class Header(val label: String) : Row()
        data class Provider(val info: AppWidgetProviderInfo, val label: String) : Row()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWidgetPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.widgetList.layoutManager = LinearLayoutManager(requireContext())
        val basePadding = binding.widgetList.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.widgetList) { v, insets ->
            v.updatePadding(bottom = basePadding + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }

        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = withContext(Dispatchers.IO) { loadRows(appContext) }
            val b = _binding ?: return@launch
            b.widgetsEmpty.isVisible = rows.isEmpty()
            b.widgetList.adapter = Adapter(rows)
        }
    }

    private fun loadRows(context: Context): List<Row> {
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        val providers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            userManager.userProfiles.flatMap { manager.getInstalledProvidersForProfile(it) }
        } else manager.installedProviders

        val collator = Collator.getInstance()
        return providers
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .groupBy { it.provider.packageName to it.profile }
            .map { (key, infos) ->
                val appLabel = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(key.first, 0)).toString()
                } catch (_: Exception) {
                    key.first
                }
                appLabel to infos.map { it to it.loadLabel(pm) }.sortedWith { a, b -> collator.compare(a.second, b.second) }
            }
            .sortedWith { a, b -> collator.compare(a.first, b.first) }
            .flatMap { (appLabel, infos) ->
                listOf<Row>(Row.Header(appLabel)) + infos.map { Row.Provider(it.first, it.second) }
            }
    }

    private fun onProviderSelected(info: AppWidgetProviderInfo) {
        val activity = activity as? MainActivity ?: return
        findNavController().popBackStack(R.id.mainFragment, false)
        activity.addWidget(info)
    }

    private inner class Adapter(private val rows: List<Row>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1
        override fun getItemCount() = rows.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == 0) HeaderHolder(ItemSearchHeaderBinding.inflate(inflater, parent, false))
            else ProviderHolder(ItemWidgetProviderBinding.inflate(inflater, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).binding.headerTitle.text = row.label
                is Row.Provider -> (holder as ProviderHolder).bind(row)
            }
        }
    }

    private class HeaderHolder(val binding: ItemSearchHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class ProviderHolder(val binding: ItemWidgetProviderBinding) : RecyclerView.ViewHolder(binding.root) {
        private var previewJob: Job? = null

        fun bind(row: Row.Provider) {
            val info = row.info
            binding.widgetLabel.text = row.label
            binding.widgetSize.text = getString(R.string.widget_size_format, cells(info.minWidth), cells(info.minHeight))
            binding.root.setOnClickListener { onProviderSelected(info) }

            val key = info.provider.flattenToString() + info.profile
            previewJob?.cancel()
            val cached = previewCache.get(key)
            binding.widgetPreview.setImageDrawable(cached?.constantState?.newDrawable() ?: cached)
            if (cached != null) return
            val context = binding.root.context
            previewJob = viewLifecycleOwner.lifecycleScope.launch {
                val drawable = withContext(Dispatchers.IO) {
                    try {
                        info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                    } catch (_: Exception) {
                        null
                    }
                }
                drawable?.let { previewCache.put(key, it) }
                if (bindingAdapterPosition != RecyclerView.NO_POSITION) binding.widgetPreview.setImageDrawable(drawable)
            }
        }

        // Rough launcher grid cell count, for display only
        private fun cells(px: Int) = ((px + 30.dpToPx()) / 70.dpToPx()).coerceAtLeast(1)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
