package app.olauncher.ui.search

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.SearchEngine
import app.olauncher.data.SearchEngines
import app.olauncher.databinding.DialogSearchEngineEditBinding
import app.olauncher.databinding.FragmentSearchEnginesBinding
import app.olauncher.databinding.ItemSearchEngineSettingBinding
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog
import app.olauncher.ui.BaseFragment

/** Add, edit, delete and reorder the search engines shared by every search mode. */
class SearchEnginesFragment : BaseFragment() {

    private var _binding: FragmentSearchEnginesBinding? = null
    private val binding get() = _binding!!
    private val engines = mutableListOf<SearchEngine>()
    private lateinit var adapter: EnginesAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private var dialog: OlDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchEnginesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        engines.clear()
        engines.addAll(SearchEngines.get(requireContext()))

        adapter = EnginesAdapter()
        binding.enginesList.layoutManager = LinearLayoutManager(requireContext())
        binding.enginesList.adapter = adapter
        touchHelper = ItemTouchHelper(DragCallback())
        touchHelper.attachToRecyclerView(binding.enginesList)

        val basePadding = binding.enginesRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.enginesRoot) { v, insets ->
            v.updatePadding(bottom = basePadding + insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }

        binding.addEngine.setOnClickListener { showEditDialog(null) }
    }

    private fun persist() = SearchEngines.save(requireContext(), engines)

    private fun showEditDialog(existing: SearchEngine?) {
        var form: DialogSearchEngineEditBinding? = null
        val newDialog = requireContext().createDialog(
            title = if (existing == null) R.string.add_search_engine else R.string.edit_search_engine,
            action = R.string.save,
            content = { container ->
                DialogSearchEngineEditBinding.inflate(layoutInflater, container, false).also { form = it }.root
            },
            validate = { form?.let { validate(it, existing) } ?: false },
            onAction = {
                val f = form ?: return@createDialog
                val engine = SearchEngine(
                    id = existing?.id ?: SearchEngines.newId(),
                    name = f.editName.text.toString().trim(),
                    url = f.editUrl.text.toString().trim(),
                    alias = f.editAlias.text.toString().trim(),
                )
                val index = engines.indexOfFirst { it.id == engine.id }
                if (index >= 0) {
                    engines[index] = engine
                    adapter.notifyItemChanged(index)
                } else {
                    engines.add(engine)
                    adapter.notifyItemInserted(engines.size - 1)
                }
                persist()
            },
        )
        form?.apply {
            editName.setText(existing?.name)
            editUrl.setText(existing?.url ?: "https://")
            editAlias.setText(existing?.alias)
        }
        showDialog(newDialog)
    }

    private fun validate(form: DialogSearchEngineEditBinding, existing: SearchEngine?): Boolean {
        val name = form.editName.text.toString().trim()
        val url = form.editUrl.text.toString().trim()
        val alias = form.editAlias.text.toString().trim()
        val error = when {
            name.isEmpty() -> R.string.error_name_required
            !SearchEngines.isValidUrl(url) -> R.string.error_url_invalid
            !SearchEngines.isValidAlias(alias) -> R.string.error_alias_invalid
            SearchEngines.isAliasTaken(engines, alias, existing?.id) -> R.string.error_alias_duplicate
            else -> 0
        }
        form.editError.isVisible = error != 0
        if (error != 0) form.editError.setText(error)
        return error == 0
    }

    private fun confirmDelete(engine: SearchEngine) {
        showDialog(
            requireContext().createDialog(
                title = R.string.delete_search_engine,
                action = R.string.delete_engine,
                message = R.string.delete_search_engine_message,
                onAction = {
                    val index = engines.indexOfFirst { it.id == engine.id }
                    if (index >= 0) {
                        engines.removeAt(index)
                        adapter.notifyItemRemoved(index)
                        persist()
                    }
                },
            )
        )
    }

    private fun showDialog(newDialog: OlDialog) {
        dialog?.dismiss()
        dialog = newDialog
        newDialog.showRespectingStatusBar()
    }

    private inner class EnginesAdapter : RecyclerView.Adapter<EngineHolder>() {
        override fun getItemCount() = engines.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            EngineHolder(ItemSearchEngineSettingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: EngineHolder, position: Int) = holder.bind(engines[position])
    }

    private inner class EngineHolder(val binding: ItemSearchEngineSettingBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("ClickableViewAccessibility")
        fun bind(engine: SearchEngine) {
            binding.engineName.text = engine.name
            binding.engineUrl.text = engine.url
            binding.engineAlias.text = engine.alias
            binding.engineRow.setOnClickListener { showEditDialog(engine) }
            binding.engineDelete.setOnClickListener { confirmDelete(engine) }
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(this)
                false
            }
        }
    }

    private inner class DragCallback : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled() = false

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            engines.add(to, engines.removeAt(from))
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            persist()
        }
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
        _binding = null
    }
}
