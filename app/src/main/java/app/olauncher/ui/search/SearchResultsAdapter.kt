package app.olauncher.ui.search

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import app.olauncher.data.AppModel
import app.olauncher.data.AppSearchAppearance
import app.olauncher.data.Contact
import app.olauncher.data.SearchEngine
import app.olauncher.databinding.ItemSearchAppBinding
import app.olauncher.databinding.ItemSearchContactBinding
import app.olauncher.databinding.ItemSearchEngineBinding
import app.olauncher.databinding.ItemSearchHeaderBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class SearchItem {
    data class Header(val title: String) : SearchItem()
    data class App(val app: AppModel) : SearchItem()
    data class ContactItem(val contact: Contact) : SearchItem()
    data class Engine(val engine: SearchEngine, val matched: Boolean) : SearchItem()
    data class Permission(val label: String) : SearchItem()
}

interface SearchActions {
    fun onAppClick(app: AppModel)
    fun onAppLongClick(app: AppModel)
    fun onContactClick(contact: Contact)
    fun onContactTileClick(contact: Contact, anchor: View)
    fun onContactLongClick(contact: Contact)
    fun onContactCall(contact: Contact)
    fun onContactText(contact: Contact)
    fun onContactWhatsApp(contact: Contact)
    fun onEngineClick(engine: SearchEngine)
    fun onPermissionClick()
}

class SearchResultsAdapter(
    private val labelGravity: Int,
    private val actions: SearchActions,
    tileCount: Int,
) : ListAdapter<SearchItem, RecyclerView.ViewHolder>(DIFF) {

    private val iconScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val iconCache = LruCache<String, Drawable.ConstantState>(128)
    var tileCount: Int = tileCount
        private set
    private var appAppearance: AppSearchAppearance? = null

    fun setAppAppearance(appearance: AppSearchAppearance) {
        appAppearance = appearance
    }

    fun setTileCount(count: Int) {
        tileCount = count.coerceIn(1, 2)
    }

    fun close() = iconScope.cancel()

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is SearchItem.Header -> TYPE_HEADER
        is SearchItem.App, is SearchItem.Permission -> TYPE_APP
        is SearchItem.ContactItem -> TYPE_CONTACT
        is SearchItem.Engine -> TYPE_ENGINE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemSearchHeaderBinding.inflate(inflater, parent, false))
            TYPE_CONTACT -> ContactHolder(ItemSearchContactBinding.inflate(inflater, parent, false))
            TYPE_ENGINE -> EngineHolder(ItemSearchEngineBinding.inflate(inflater, parent, false))
            else -> AppHolder(ItemSearchAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is SearchItem.Header -> (holder as HeaderHolder).binding.headerTitle.apply {
                text = item.title
                gravity = labelGravity
            }

            is SearchItem.App -> (holder as AppHolder).apply {
                binding.itemTitle.apply {
                    text = buildString {
                        append(item.app.appLabel)
                        if (item.app.isNew) append(" ✦")
                    }
                    alpha = 1f
                }
                loadIcon(item.app)
                val appearance = appAppearance
                if (appearance != null) {
                    appStyle.apply(appearance, tileCount, labelGravity)
                } else {
                    binding.itemTitle.gravity = labelGravity
                    binding.itemTitle.maxLines = if (tileCount == 2) 2 else 1
                    val density = binding.root.resources.displayMetrics.density
                    val horizontalPadding = ((if (tileCount == 2) 12 else 24) * density).toInt()
                    binding.root.setPadding(horizontalPadding, 0, horizontalPadding, 0)
                    binding.itemIcon.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        marginEnd = ((if (tileCount == 2) 8 else 16) * density).toInt()
                    }
                }
                binding.root.setOnClickListener { actions.onAppClick(item.app) }
                binding.root.setOnLongClickListener {
                    actions.onAppLongClick(item.app)
                    true
                }
            }

            is SearchItem.Permission -> (holder as AppHolder).apply {
                binding.itemTitle.apply {
                    text = item.label
                    gravity = labelGravity
                    alpha = 0.6f
                    maxLines = 1
                }
                clearIcon()
                binding.root.setOnClickListener { actions.onPermissionClick() }
                binding.root.setOnLongClickListener(null)
            }

            is SearchItem.ContactItem -> (holder as ContactHolder).binding.apply {
                val contact = item.contact
                contactName.text = contact.name
                val tileLayout = tileCount == 2
                contactIcon.isVisible = !tileLayout
                contactName.apply {
                    gravity = if (tileLayout) android.view.Gravity.CENTER else labelGravity
                    maxLines = if (tileLayout) Int.MAX_VALUE else 1
                    ellipsize = if (tileLayout) null else android.text.TextUtils.TruncateAt.END
                    val sidePadding = ((if (tileLayout) 8 else 12) * root.resources.displayMetrics.density).toInt()
                    setPadding(sidePadding, paddingTop, sidePadding, paddingBottom)
                }
                contactCall.isVisible = !tileLayout && !contact.number.isNullOrBlank()
                contactText.isVisible = !tileLayout && !contact.number.isNullOrBlank()
                contactWhatsApp.isVisible = !tileLayout && !contact.number.isNullOrBlank()
                if (tileLayout) {
                    contactMain.background = null
                    // Clearing a listener makes a View clickable, even when the listener is null.
                    contactMain.setOnClickListener(null)
                    contactMain.isClickable = false
                    contactMain.isLongClickable = false
                    contactMain.isFocusable = false
                    root.setOnClickListener { actions.onContactTileClick(contact, root) }
                    root.setOnLongClickListener {
                        actions.onContactLongClick(contact)
                        true
                    }
                } else {
                    val selectableBackground = TypedValue()
                    if (root.context.theme.resolveAttribute(
                            android.R.attr.selectableItemBackground,
                            selectableBackground,
                            true,
                        )
                    ) {
                        contactMain.setBackgroundResource(selectableBackground.resourceId)
                    }
                    contactMain.isClickable = true
                    contactMain.setOnClickListener { actions.onContactClick(contact) }
                    root.setOnClickListener(null)
                    root.setOnLongClickListener(null)
                    root.isClickable = false
                    root.isLongClickable = false
                }
                contactCall.setOnClickListener { actions.onContactCall(contact) }
                contactText.setOnClickListener { actions.onContactText(contact) }
                contactWhatsApp.setOnClickListener { actions.onContactWhatsApp(contact) }
            }

            is SearchItem.Engine -> (holder as EngineHolder).binding.apply {
                engineName.text = item.engine.name
                engineName.maxLines = if (tileCount == 2) 2 else 1
                engineAlias.text = item.engine.alias
                engineAlias.alpha = if (item.matched) 1f else 0.5f
                root.setOnClickListener { actions.onEngineClick(item.engine) }
                val sidePadding = ((if (tileCount == 2) 12 else 24) * root.resources.displayMetrics.density).toInt()
                root.setPadding(sidePadding, 0, sidePadding, 0)
            }
        }
    }

    private class HeaderHolder(val binding: ItemSearchHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    private inner class AppHolder(val binding: ItemSearchAppBinding) : RecyclerView.ViewHolder(binding.root) {
        val appStyle by lazy { AppSearchTileStyle(binding) }
        var iconJob: Job? = null

        fun clearIcon() {
            iconJob?.cancel()
            iconJob = null
            binding.itemIcon.tag = null
            binding.itemIcon.setImageDrawable(null)
            binding.itemIcon.isVisible = false
        }

        fun loadIcon(app: AppModel) {
            iconJob?.cancel()
            val key = "${app.appPackage}|${app.user}"
            binding.itemIcon.tag = key
            binding.itemIcon.isVisible = true
            binding.itemIcon.setImageDrawable(null)
            val cached = iconCache.get(key)
            if (cached != null) {
                binding.itemIcon.setImageDrawable(cached.newDrawable(binding.root.resources).mutate())
                return
            }

            val context = binding.root.context.applicationContext
            iconJob = iconScope.launch {
                val iconState = loadIconState(context, app)
                if (iconState != null) iconCache.put(key, iconState)
                withContext(Dispatchers.Main) {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION && binding.itemIcon.tag == key) {
                        binding.itemIcon.setImageDrawable(iconState?.newDrawable(binding.root.resources)?.mutate())
                        binding.itemIcon.isVisible = iconState != null
                    }
                }
            }
        }
    }

    private fun loadIconState(context: Context, app: AppModel): Drawable.ConstantState? = try {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val activities = launcherApps.getActivityList(app.appPackage, app.user)
        val activity = activities.firstOrNull {
            app is AppModel.App && it.componentName.className == app.activityClassName
        } ?: activities.firstOrNull()
        activity?.getBadgedIcon(context.resources.displayMetrics.densityDpi)?.constantState
    } catch (_: Exception) {
        null
    }
    private class ContactHolder(val binding: ItemSearchContactBinding) : RecyclerView.ViewHolder(binding.root)
    private class EngineHolder(val binding: ItemSearchEngineBinding) : RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_APP = 1
        const val TYPE_CONTACT = 2
        const val TYPE_ENGINE = 3

        val DIFF = object : DiffUtil.ItemCallback<SearchItem>() {
            override fun areItemsTheSame(oldItem: SearchItem, newItem: SearchItem) = when {
                oldItem is SearchItem.Header && newItem is SearchItem.Header -> oldItem.title == newItem.title
                oldItem is SearchItem.App && newItem is SearchItem.App ->
                    oldItem.app.appPackage == newItem.app.appPackage && oldItem.app.user == newItem.app.user &&
                            oldItem.app.appLabel == newItem.app.appLabel
                oldItem is SearchItem.ContactItem && newItem is SearchItem.ContactItem -> oldItem.contact.id == newItem.contact.id
                oldItem is SearchItem.Engine && newItem is SearchItem.Engine -> oldItem.engine.id == newItem.engine.id
                oldItem is SearchItem.Permission && newItem is SearchItem.Permission -> true
                else -> false
            }

            override fun areContentsTheSame(oldItem: SearchItem, newItem: SearchItem) = oldItem == newItem
        }
    }
}
