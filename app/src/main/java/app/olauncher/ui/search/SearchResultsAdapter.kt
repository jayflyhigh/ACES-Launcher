package app.olauncher.ui.search

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.util.LruCache
import android.view.View
import app.olauncher.data.AppModel
import app.olauncher.data.AppSearchAppearance
import app.olauncher.data.Contact
import app.olauncher.data.SearchEngine
import app.olauncher.R
import app.olauncher.databinding.ItemSearchAppBinding
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
    fun onResultMenu(item: SearchItem, anchor: View)
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
        else -> TYPE_RESULT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderHolder(ItemSearchHeaderBinding.inflate(inflater, parent, false))
            else -> AppHolder(ItemSearchAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (item is SearchItem.Header) {
            (holder as HeaderHolder).binding.headerTitle.apply {
                text = item.title
                gravity = labelGravity
            }
            return
        }
        with(holder as AppHolder) {
            clearIcon()
            appStyle.apply(appAppearance ?: AppSearchAppearance(), tileCount, labelGravity)
            binding.itemTitle.alpha = 1f
            binding.itemType.isVisible = true
            binding.contactActions.isVisible = false
            binding.itemMenu.isVisible = item !is SearchItem.Permission && item !is SearchItem.ContactItem
            binding.root.setOnLongClickListener(null)
            binding.root.isLongClickable = false
            when (item) {
                is SearchItem.App -> {
                    binding.itemTitle.text = item.app.appLabel + if (item.app.isNew) " ✦" else ""
                    binding.itemType.setText(
                        if (item.app is AppModel.PinnedShortcut) R.string.search_type_shortcut else R.string.search_type_app,
                    )
                    loadIcon(item.app)
                    binding.root.setOnClickListener { actions.onAppClick(item.app) }
                    binding.root.setOnLongClickListener {
                        actions.onAppLongClick(item.app)
                        true
                    }
                }
                is SearchItem.ContactItem -> {
                    binding.itemTitle.text = item.contact.name
                    binding.itemType.isVisible = false
                    setIcon(R.drawable.ic_person)
                    binding.contactActions.isVisible = tileCount == 1
                    if (tileCount == 1) {
                        val density = binding.root.resources.displayMetrics.density
                        binding.root.setPadding((12 * density).toInt(), binding.root.paddingTop, 0, binding.root.paddingBottom)
                        binding.itemIcon.layoutParams = binding.itemIcon.layoutParams.apply {
                            width = (32 * density).toInt()
                            height = width
                            (this as? ViewGroup.MarginLayoutParams)?.marginEnd = (12 * density).toInt()
                        }
                    }
                    binding.contactCallAction.setOnClickListener { actions.onContactCall(item.contact) }
                    binding.contactTextAction.setOnClickListener { actions.onContactText(item.contact) }
                    binding.contactWhatsAppAction.setOnClickListener { actions.onContactWhatsApp(item.contact) }
                    binding.root.setOnClickListener {
                        if (tileCount == 2) actions.onContactTileClick(item.contact, binding.root)
                        else actions.onContactClick(item.contact)
                    }
                    binding.root.setOnLongClickListener {
                        actions.onContactLongClick(item.contact)
                        true
                    }
                }
                is SearchItem.Engine -> {
                    binding.itemTitle.text = item.engine.name
                    binding.itemType.text = binding.root.context.getString(R.string.search_type_engine)
                    setIcon(R.drawable.ic_search_web)
                    binding.root.isActivated = item.matched
                    binding.root.setOnClickListener { actions.onEngineClick(item.engine) }
                }
                is SearchItem.Permission -> {
                    binding.itemTitle.text = item.label
                    binding.itemType.isVisible = false
                    setIcon(R.drawable.ic_person)
                    binding.root.setOnClickListener { actions.onPermissionClick() }
                }
                is SearchItem.Header -> Unit
            }
            binding.itemMenu.contentDescription = binding.root.context.getString(
                R.string.search_result_actions, binding.itemTitle.text,
            )
            binding.itemMenu.setOnClickListener { actions.onResultMenu(item, it) }
        }
    }

    private class HeaderHolder(val binding: ItemSearchHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    private inner class AppHolder(val binding: ItemSearchAppBinding) : RecyclerView.ViewHolder(binding.root) {
        val appStyle by lazy { AppSearchTileStyle(binding) }
        var iconJob: Job? = null

        fun setIcon(resource: Int) {
            binding.itemIcon.isVisible = true
            binding.itemIcon.setImageResource(resource)
            binding.itemIcon.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        }

        fun clearIcon() {
            iconJob?.cancel()
            iconJob = null
            binding.itemIcon.tag = null
            binding.itemIcon.imageTintList = null
            binding.itemIcon.setImageDrawable(null)
            binding.itemIcon.isVisible = false
            binding.root.isActivated = false
        }

        fun loadIcon(app: AppModel) {
            iconJob?.cancel()
            val identity = when (app) {
                is AppModel.App -> app.activityClassName
                is AppModel.PinnedShortcut -> app.shortcutId
                is AppModel.PrivateSpaceHeader -> ""
            }
            val key = "${app.appPackage}|${app.user}|$identity"
            binding.itemIcon.tag = key
            binding.itemIcon.isVisible = true
            binding.itemIcon.setImageResource(R.drawable.ic_search_apps)
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
                        if (iconState != null)
                            binding.itemIcon.setImageDrawable(iconState.newDrawable(binding.root.resources).mutate())
                    }
                }
            }
        }
    }

    private fun loadIconState(context: Context, app: AppModel): Drawable.ConstantState? {
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            if (app is AppModel.PinnedShortcut && launcherApps.hasShortcutHostPermission()) {
                val query = LauncherApps.ShortcutQuery().apply {
                    setPackage(app.appPackage)
                    setShortcutIds(listOf(app.shortcutId))
                    setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                }
                val shortcut = launcherApps.getShortcuts(query, app.user)?.firstOrNull()
                val icon = shortcut?.let { launcherApps.getShortcutIconDrawable(it, context.resources.displayMetrics.densityDpi) }
                if (icon?.constantState != null) return icon.constantState
            }
            val activities = launcherApps.getActivityList(app.appPackage, app.user)
            val activity = activities.firstOrNull {
                app is AppModel.App && it.componentName.className == app.activityClassName
            } ?: activities.firstOrNull()
            activity?.getBadgedIcon(context.resources.displayMetrics.densityDpi)?.constantState
        } catch (error: SecurityException) {
            android.util.Log.w("SearchResults", "Cannot read result icon for ${app.appPackage}", error)
            null
        }
    }

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_RESULT = 1

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
