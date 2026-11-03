package app.olauncher.ui.search

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.Spannable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Contact
import app.olauncher.data.ContactsLoader
import app.olauncher.data.Prefs
import app.olauncher.data.SearchEngine
import app.olauncher.data.SearchEngines
import app.olauncher.data.SearchMode
import app.olauncher.databinding.FragmentUniversalSearchBinding
import app.olauncher.helper.appLabelMatches
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** One search page shared by the A, C, E and S shortcuts; [SearchMode] decides what it shows. */
class UniversalSearchFragment : BaseFragment(), SearchActions {

    private lateinit var prefs: Prefs
    private lateinit var mode: SearchMode
    private lateinit var adapter: SearchResultsAdapter
    private val viewModel: MainViewModel by activityViewModels()

    private var _binding: FragmentUniversalSearchBinding? = null
    private val binding get() = _binding!!

    private var apps: List<AppModel> = emptyList()
    private var privateApps: List<AppModel> = emptyList()
    private var privateSpaceLocked = true
    private var contacts: List<Contact> = emptyList()
    private var engines: List<SearchEngine> = emptyList()
    private var filterJob: Job? = null
    private var permissionRequested = false
    private var searchTextView: TextView? = null
    private var autoLaunchedQuery: String? = null

    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.loadContacts()
        else context?.showToast(R.string.contacts_permission_denied)
        rebuild()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUniversalSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        mode = SearchMode.from(arguments?.getString(Constants.Key.SEARCH_MODE))

        initSearchView()
        initRecyclerView()
        initObservers()

        if (mode == SearchMode.CONTACTS_ONLY && savedInstanceState == null && !ContactsLoader.hasPermission(requireContext()))
            requestContactsPermission()
        rebuild()
    }

    private fun initSearchView() {
        binding.search.queryHint = when (mode) {
            SearchMode.APPS_ONLY -> getString(R.string.apps)
            SearchMode.CONTACTS_ONLY -> getString(R.string.contacts)
            SearchMode.ENGINES_ONLY -> getString(R.string.search_engines)
            SearchMode.ALL_SEARCH -> " ___"
        }
        searchTextView = binding.search.findViewById(R.id.search_src_text)
        searchTextView?.apply {
            gravity = prefs.appLabelAlignment
            background = null
            backgroundTintList = null
        }
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                submit(query.orEmpty())
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                rebuild()
                return true
            }
        })
    }

    private fun initRecyclerView() {
        adapter = SearchResultsAdapter(prefs.appLabelAlignment, this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) binding.search.hideKeyboard()
            }
        })

        val baseBottomMargin = (binding.recyclerView.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerView) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).bottom
            val bottomMargin = baseBottomMargin + max(max(ime, nav), cutout)
            val params = v.layoutParams as ViewGroup.MarginLayoutParams
            if (params.bottomMargin != bottomMargin) {
                params.bottomMargin = bottomMargin
                v.layoutParams = params
            }
            insets
        }
    }

    private fun initObservers() {
        engines = SearchEngines.get(requireContext())
        if (mode.showsEngines)
            SearchEngines.engines.observe(viewLifecycleOwner) {
                engines = it
                rebuild()
            }
        if (mode.showsApps) {
            viewModel.appList.observe(viewLifecycleOwner) {
                apps = it.orEmpty().filter { app -> app !is AppModel.PrivateSpaceHeader && app.appPackage.isNotEmpty() }
                rebuild()
            }
            viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                privateSpaceLocked = it
                rebuild()
            }
            viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                privateApps = it.orEmpty()
                rebuild()
            }
        }
        if (mode.showsContacts) {
            viewModel.contacts.observe(viewLifecycleOwner) {
                contacts = it
                rebuild()
            }
            viewModel.loadContacts()
        }
    }

    private fun currentQuery() = _binding?.search?.query?.toString().orEmpty()

    private fun rebuild() {
        if (_binding == null) return
        val query = currentQuery()
        val appsSnapshot = if (privateSpaceLocked) apps else apps + privateApps
        val contactsSnapshot = contacts
        val enginesSnapshot = engines
        val contactsGranted = ContactsLoader.hasPermission(requireContext())
        val titles = Titles(
            apps = getString(R.string.apps),
            contacts = getString(R.string.contacts),
            search = getString(R.string.search),
            permission = getString(R.string.allow_contacts_access),
        )
        filterJob?.cancel()
        filterJob = viewLifecycleOwner.lifecycleScope.launch {
            val items = withContext(Dispatchers.Default) {
                buildItems(query, appsSnapshot, contactsSnapshot, enginesSnapshot, contactsGranted, titles)
            }
            adapter.submitList(items) {
                if (mode != SearchMode.APPS_ONLY || query.isBlank() || query.startsWith(" ") ||
                    query != currentQuery() || isSearchComposing() || query == autoLaunchedQuery
                ) return@submitList

                val soleResult = items.filterIsInstance<SearchItem.App>().singleOrNull() ?: return@submitList
                autoLaunchedQuery = query
                onAppClick(soleResult.app)
            }
        }
    }

    private fun isSearchComposing(): Boolean {
        val text = searchTextView?.text
        if (text !is Spannable) return false
        val start = BaseInputConnection.getComposingSpanStart(text)
        val end = BaseInputConnection.getComposingSpanEnd(text)
        return start >= 0 && end > start
    }

    private class Titles(val apps: String, val contacts: String, val search: String, val permission: String)

    private fun buildItems(
        query: String,
        apps: List<AppModel>,
        contacts: List<Contact>,
        engines: List<SearchEngine>,
        contactsGranted: Boolean,
        titles: Titles,
    ): List<SearchItem> {
        val q = query.trim()
        val showHeaders = mode == SearchMode.ALL_SEARCH
        val items = mutableListOf<SearchItem>()

        if (mode.showsApps) {
            val matches = if (q.isEmpty()) apps else apps.filter { appLabelMatches(it.appLabel, q) }
            if (matches.isNotEmpty()) {
                if (showHeaders) items.add(SearchItem.Header(titles.apps))
                matches.mapTo(items) { SearchItem.App(it) }
            }
        }

        if (mode.showsContacts) {
            if (!contactsGranted) {
                if (showHeaders) items.add(SearchItem.Header(titles.contacts))
                items.add(SearchItem.Permission(titles.permission))
            } else {
                val isNumberQuery = q.length >= 3 && q.all { it.isDigit() || it in "+-() " }
                val digits = q.filter { it.isDigit() }
                val matches = when {
                    q.isEmpty() -> contacts
                    isNumberQuery -> contacts.filter { it.number?.filter(Char::isDigit)?.contains(digits) == true }
                    else -> contacts.filter { appLabelMatches(it.name, q) }
                }
                if (matches.isNotEmpty()) {
                    if (showHeaders) items.add(SearchItem.Header(titles.contacts))
                    matches.mapTo(items) { SearchItem.ContactItem(it) }
                }
            }
        }

        if (mode.showsEngines && engines.isNotEmpty()) {
            val aliasEngineId = SearchEngines.parseAliasQuery(engines, query)?.first?.id
            if (showHeaders) items.add(SearchItem.Header(titles.search))
            engines.mapTo(items) { SearchItem.Engine(it, it.id == aliasEngineId) }
        }
        return items
    }

    private fun submit(text: String) {
        val query = text.trim()
        if (mode.showsEngines) {
            SearchEngines.parseAliasQuery(engines, text)?.let { (engine, rest) ->
                if (rest.isBlank()) requireContext().showToast(R.string.type_search_query)
                else openEngine(engine, rest)
                return
            }
        }
        if (mode == SearchMode.ENGINES_ONLY) {
            if (query.isEmpty()) requireContext().showToast(R.string.type_search_query)
            else engines.firstOrNull()?.let { openEngine(it, query) }
            return
        }
        when (val first = adapter.currentList.firstOrNull { it is SearchItem.App || it is SearchItem.ContactItem }) {
            is SearchItem.App -> onAppClick(first.app)
            is SearchItem.ContactItem -> onContactClick(first.contact)
            else -> if (mode == SearchMode.ALL_SEARCH && query.isNotEmpty())
                engines.firstOrNull()?.let { openEngine(it, query) }
        }
    }

    private fun openEngine(engine: SearchEngine, query: String) {
        if (SearchEngines.open(requireContext(), engine, query)) closeSearch()
    }

    private fun closeSearch() {
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    private fun startSafely(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun requestContactsPermission() {
        permissionRequested = true
        contactsPermission.launch(Manifest.permission.READ_CONTACTS)
    }

    // SearchActions

    override fun onAppClick(app: AppModel) {
        viewModel.selectedApp(app, Constants.FLAG_LAUNCH_APP)
        closeSearch()
    }

    override fun onAppLongClick(app: AppModel) {
        openAppInfo(requireContext(), app.user, app.appPackage)
    }

    override fun onContactClick(contact: Contact) {
        val uri = if (contact.lookupKey.isNotEmpty())
            ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
        else ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
        if (!startSafely(Intent(Intent.ACTION_VIEW, uri)))
            requireContext().showToast(R.string.unable_to_open_contact)
    }

    override fun onContactCall(contact: Contact) {
        val number = contact.number ?: return requireContext().showToast(R.string.no_phone_number)
        if (!startSafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))))
            requireContext().showToast(R.string.unable_to_open_app)
    }

    override fun onContactText(contact: Contact) {
        val number = contact.number ?: return requireContext().showToast(R.string.no_phone_number)
        if (!startSafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))))
            requireContext().showToast(R.string.unable_to_open_app)
    }

    override fun onContactWhatsApp(contact: Contact) {
        val digits = (contact.normalizedNumber ?: contact.number)?.filter { it.isDigit() }
        if (digits.isNullOrEmpty()) return requireContext().showToast(R.string.no_phone_number)
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$digits")
        val opened = WHATSAPP_PACKAGES.any { startSafely(Intent(Intent.ACTION_VIEW, uri).setPackage(it)) }
        if (!opened) requireContext().showToast(R.string.whatsapp_not_installed)
    }

    override fun onEngineClick(engine: SearchEngine) {
        val text = currentQuery()
        val query = (SearchEngines.parseAliasQuery(engines, text)?.second ?: text).trim()
        if (query.isEmpty()) {
            binding.search.setQuery("${engine.alias} ", false)
            // showKeyboard() toggles the IME, which would close an already open keyboard
            binding.search.findViewById<TextView>(R.id.search_src_text)?.let { field ->
                field.requestFocus()
                (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
            return
        }
        openEngine(engine, query)
    }

    override fun onPermissionClick() {
        if (permissionRequested && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS))
            openAppInfo(requireContext(), android.os.Process.myUserHandle(), requireContext().packageName)
        else
            requestContactsPermission()
    }

    override fun onStart() {
        super.onStart()
        binding.search.showKeyboard()
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        filterJob?.cancel()
        searchTextView = null
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
