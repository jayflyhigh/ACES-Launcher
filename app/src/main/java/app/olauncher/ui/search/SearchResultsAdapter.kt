package app.olauncher.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.data.AppModel
import app.olauncher.data.Contact
import app.olauncher.data.SearchEngine
import app.olauncher.databinding.ItemSearchAppBinding
import app.olauncher.databinding.ItemSearchContactBinding
import app.olauncher.databinding.ItemSearchEngineBinding
import app.olauncher.databinding.ItemSearchHeaderBinding

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
    fun onContactCall(contact: Contact)
    fun onContactText(contact: Contact)
    fun onContactWhatsApp(contact: Contact)
    fun onEngineClick(engine: SearchEngine)
    fun onPermissionClick()
}

class SearchResultsAdapter(
    private val labelGravity: Int,
    private val actions: SearchActions,
) : ListAdapter<SearchItem, RecyclerView.ViewHolder>(DIFF) {

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

            is SearchItem.App -> (holder as AppHolder).binding.itemTitle.apply {
                text = buildString {
                    append(item.app.appLabel)
                    if (item.app.isNew) append(" ✦")
                }
                gravity = labelGravity
                alpha = 1f
                setOnClickListener { actions.onAppClick(item.app) }
                setOnLongClickListener {
                    actions.onAppLongClick(item.app)
                    true
                }
            }

            is SearchItem.Permission -> (holder as AppHolder).binding.itemTitle.apply {
                text = item.label
                gravity = labelGravity
                alpha = 0.6f
                setOnClickListener { actions.onPermissionClick() }
                setOnLongClickListener(null)
            }

            is SearchItem.ContactItem -> (holder as ContactHolder).binding.apply {
                val contact = item.contact
                val hasNumber = !contact.number.isNullOrBlank()
                contactName.text = contact.name
                contactMain.setOnClickListener { actions.onContactClick(contact) }
                contactCall.isVisible = hasNumber
                contactText.isVisible = hasNumber
                contactWhatsApp.isVisible = hasNumber
                contactCall.setOnClickListener { actions.onContactCall(contact) }
                contactText.setOnClickListener { actions.onContactText(contact) }
                contactWhatsApp.setOnClickListener { actions.onContactWhatsApp(contact) }
            }

            is SearchItem.Engine -> (holder as EngineHolder).binding.apply {
                engineName.text = item.engine.name
                engineAlias.text = item.engine.alias
                engineAlias.alpha = if (item.matched) 1f else 0.5f
                root.setOnClickListener { actions.onEngineClick(item.engine) }
            }
        }
    }

    private class HeaderHolder(val binding: ItemSearchHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    private class AppHolder(val binding: ItemSearchAppBinding) : RecyclerView.ViewHolder(binding.root)
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
