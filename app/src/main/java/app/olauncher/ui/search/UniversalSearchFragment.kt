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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.AppSearchAppearance
import app.olauncher.data.Constants
import app.olauncher.data.Contact
import app.olauncher.data.ContactCommand
import app.olauncher.data.ContactCommandQuery
import app.olauncher.data.ContactCommandSession
import app.olauncher.data.ContactsLoader
import app.olauncher.data.Prefs
import app.olauncher.data.SearchEngine
import app.olauncher.data.SearchEngines
import app.olauncher.data.SearchMode
import app.olauncher.databinding.FragmentUniversalSearchBinding
import app.olauncher.databinding.PopupContactActionsBinding
import app.olauncher.databinding.PopupSearchPageMenuBinding
import app.olauncher.helper.AppSearchMatcher
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
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
    private var searchTileCount = 1
    private var appAppearance: AppSearchAppearance? = null
    private var activePopupWindow: PopupWindow? = null
    private var commandSession = ContactCommandSession()
    private var commandJob: Job? = null
    private var commandExecutor: ContactCommandExecutor? = null
    private var commandSubmitQuery: String? = null
    private var filterGeneration = 0L
    private var restoringCommandState = false
    private var displayedCommand: ContactCommand? = null

    private val popupBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            activePopupWindow?.dismiss()
        }
    }

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
        displayedCommand = null
        if (mode == SearchMode.CONTACTS_ONLY) {
            restoringCommandState = savedInstanceState != null
            if (savedInstanceState != null) {
                commandSession = ContactCommandSession(
                    savedInstanceState.getString("contactCommandQuery").orEmpty(),
                    savedInstanceState.getBoolean("contactCommandConsumed"),
                )
            }
            commandExecutor = ContactCommandExecutor(requireContext(), viewLifecycleOwner)
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, popupBackCallback)

        initSearchView()
        initRecyclerView()
        initObservers()

        if (mode == SearchMode.CONTACTS_ONLY && savedInstanceState == null && !ContactsLoader.hasPermission(requireContext()))
            requestContactsPermission()
        rebuild()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        restoringCommandState = false
        if (mode == SearchMode.CONTACTS_ONLY) rebuild()
    }

    private fun initSearchView() {
        binding.modeTitle.apply {
            text = when (mode) {
                SearchMode.APPS_ONLY -> getString(R.string.apps)
                SearchMode.CONTACTS_ONLY -> getString(R.string.contacts)
                SearchMode.ENGINES_ONLY -> getString(R.string.search_engines)
                SearchMode.ALL_SEARCH -> ""
            }
            gravity = prefs.appLabelAlignment
            visibility = if (mode == SearchMode.ALL_SEARCH) View.GONE else View.VISIBLE
        }
        binding.search.queryHint = if (mode == SearchMode.ALL_SEARCH) " ___" else ""
        searchTextView = binding.search.findViewById(R.id.search_src_text)
        searchTextView?.apply {
            gravity = prefs.appLabelAlignment
            background = null
            backgroundTintList = null
            isCursorVisible = true
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
        binding.searchPageMenu.setOnClickListener { toggleSearchPageMenu() }
    }

    private fun initRecyclerView() {
        searchTileCount = prefs.searchTileCount(mode)
        adapter = SearchResultsAdapter(prefs.appLabelAlignment, this, searchTileCount)
        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), searchTileCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int =
                    when (adapter.currentList.getOrNull(position)) {
                        is SearchItem.Header, is SearchItem.Permission -> spanCount
                        else -> 1
                    }
            }
        }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        applyAppSearchAppearance()
        binding.recyclerView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            appAppearance?.applyResultsPadding(view, searchTileCount)
        }
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
            if (mode == SearchMode.CONTACTS_ONLY) {
                viewModel.contactsLoading.observe(viewLifecycleOwner) { rebuild() }
            }
        }
    }

    private fun toggleSearchPageMenu() {
        activePopupWindow?.let {
            it.dismiss()
            return
        }
        val popupBinding = PopupSearchPageMenuBinding.inflate(layoutInflater)
        updateTileCountControls(popupBinding)
        popupBinding.searchPageSettings.setOnClickListener {
            activePopupWindow?.dismiss()
            if (mode == SearchMode.APPS_ONLY) {
                findNavController().navigate(R.id.appSearchSettingsFragment)
            } else if (mode == SearchMode.ENGINES_ONLY) {
                findNavController().navigate(R.id.searchEnginesFragment)
            } else if (mode == SearchMode.CONTACTS_ONLY) {
                findNavController().navigate(R.id.contactSearchSettingsFragment)
            } else {
                findNavController().navigate(
                    R.id.settingsFragment,
                    Bundle().apply { putBoolean(Constants.Key.OPEN_ACES_SETTINGS, true) }
                )
            }
        }
        popupBinding.decreaseTileCount.setOnClickListener {
            if (searchTileCount > 1) updateSearchTileCount(searchTileCount - 1, popupBinding)
        }
        popupBinding.increaseTileCount.setOnClickListener {
            if (searchTileCount < 2) updateSearchTileCount(searchTileCount + 1, popupBinding)
        }
        val popup = createPopup(popupBinding.root, 256, R.drawable.bg_quick_panel)
        prefs.appSearchAppearance(searchTileCount).applyMenu(popupBinding)
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        showPopup(popup, binding.searchPageMenu, popupBinding.root, Gravity.END)
    }

    private fun updateTileCountControls(binding: PopupSearchPageMenuBinding) {
        binding.tileCount.setText(
            if (searchTileCount == 1) R.string.one_tile_across else R.string.two_tiles_across
        )
        binding.decreaseTileCount.isEnabled = searchTileCount > 1
        binding.decreaseTileCount.alpha = if (searchTileCount > 1) 1f else 0.45f
        binding.increaseTileCount.isEnabled = searchTileCount < 2
        binding.increaseTileCount.alpha = if (searchTileCount < 2) 1f else 0.45f
    }

    private fun updateSearchTileCount(count: Int, menuBinding: PopupSearchPageMenuBinding) {
        val newCount = count.coerceIn(1, 2)
        if (newCount == searchTileCount) return
        searchTileCount = newCount
        prefs.setSearchTileCount(mode, newCount)

        val layoutManager = binding.recyclerView.layoutManager as GridLayoutManager
        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePosition)
        val firstVisibleOffset = firstVisibleView?.top ?: 0
        layoutManager.spanCount = newCount
        adapter.setTileCount(newCount)
        applyAppSearchAppearance()
        prefs.appSearchAppearance(newCount).applyMenu(menuBinding)
        adapter.notifyItemRangeChanged(0, adapter.itemCount)
        if (firstVisiblePosition != RecyclerView.NO_POSITION)
            layoutManager.scrollToPositionWithOffset(firstVisiblePosition, firstVisibleOffset)
        updateTileCountControls(menuBinding)
    }

    private fun applyAppSearchAppearance() {
        val appearance = prefs.appSearchAppearance(searchTileCount)
        appAppearance = appearance
        binding.searchRoot.setBackgroundColor(
            appearance.background(requireContext().getColorFromAttr(R.attr.primaryShadeDarkColor))
        )
        val foreground = android.graphics.Color.WHITE
        binding.modeTitle.setTextColor(foreground)
        binding.searchPageMenu.imageTintList = android.content.res.ColorStateList.valueOf(foreground)
        searchTextView?.setTextColor(foreground)
        searchTextView?.setHintTextColor(foreground)
        adapter.setAppAppearance(appearance)
        appearance.applyResultsPadding(binding.recyclerView, searchTileCount)
    }

    private fun showContactActions(contact: Contact, anchor: View) {
        activePopupWindow?.dismiss()
        val actions = PopupContactActionsBinding.inflate(layoutInflater)
        val popup = createPopup(actions.root, 160, R.drawable.bg_search_result, heightDp = 56)
        // Let outside taps reach other contact tiles instead of only dismissing this popup.
        popup.isFocusable = false
        actions.contactCallAction.setOnClickListener {
            popup.dismiss()
            onContactCall(contact)
        }
        actions.contactTextAction.setOnClickListener {
            popup.dismiss()
            onContactText(contact)
        }
        actions.contactWhatsAppAction.setOnClickListener {
            popup.dismiss()
            onContactWhatsApp(contact)
        }
        val xOffset = (anchor.width - popup.width) / 2
        val yOffset = -((anchor.height + popup.height) / 2)
        showPopup(popup, anchor, actions.root, Gravity.LEFT, yOffset, xOffset)
    }

    private fun createPopup(content: View, widthDp: Int, background: Int, heightDp: Int? = null): PopupWindow {
        val density = resources.displayMetrics.density
        val maxWidth = (resources.displayMetrics.widthPixels - 24 * density).toInt().coerceAtLeast(1)
        return PopupWindow(
            content,
            minOf((widthDp * density).toInt(), maxWidth),
            heightDp?.let { (it * density).toInt() } ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            true,
        ).apply {
            setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), background))
            isOutsideTouchable = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            elevation = 8 * density
            setOnDismissListener {
                if (activePopupWindow === this) activePopupWindow = null
                popupBackCallback.isEnabled = activePopupWindow?.isShowing == true
            }
        }
    }

    private fun showPopup(
        popup: PopupWindow,
        anchor: View,
        content: View,
        gravity: Int,
        yOffset: Int = 0,
        xOffset: Int = 0,
    ) {
        activePopupWindow?.dismiss()
        activePopupWindow = popup
        popupBackCallback.isEnabled = true
        content.alpha = 0f
        content.translationY = -8 * resources.displayMetrics.density
        popup.showAsDropDown(anchor, xOffset, yOffset, gravity)
        content.animate().alpha(1f).translationY(0f).setDuration(120).start()
    }

    private fun currentQuery() = _binding?.search?.query?.toString().orEmpty()

    private fun rebuild() {
        if (_binding == null || !::adapter.isInitialized) return
        if (mode == SearchMode.CONTACTS_ONLY && restoringCommandState) return
        val query = currentQuery()
        val generation = ++filterGeneration
        if (mode == SearchMode.CONTACTS_ONLY) {
            commandJob?.cancel()
            if (commandSession.query != query) {
                commandExecutor?.cancel()
                commandSubmitQuery = null
            }
            commandSession.update(query)
            updateCommandIndicator()
        }
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
                if (_binding == null || query != currentQuery() || generation != filterGeneration) return@submitList
                if (mode == SearchMode.CONTACTS_ONLY) {
                    if (commandSubmitQuery == query) {
                        commandSubmitQuery = null
                        if (viewModel.contactsLoading.value != true) {
                            items.filterIsInstance<SearchItem.ContactItem>().singleOrNull()?.let {
                                executeContactCommand(it.contact, query)
                            }
                        }
                    } else scheduleContactCommand(query, items)
                    return@submitList
                }
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

    private fun updateCommandIndicator() {
        val parsed = ContactCommandQuery.parse(currentQuery())
            ?.takeIf { prefs.contactCommandSettings().showIndicator }
        if (parsed?.command == displayedCommand) return
        displayedCommand = parsed?.command
        val label = when (parsed?.command) {
            ContactCommand.PHONE -> R.string.contact_command_phone
            ContactCommand.MESSAGE -> R.string.contact_command_message
            ContactCommand.WHATSAPP -> R.string.whatsapp
            ContactCommand.CONTACT -> R.string.contact_command_contact
            null -> R.string.contacts
        }
        binding.modeTitle.setText(label)
        val icon = when (parsed?.command) {
            ContactCommand.PHONE -> R.drawable.ic_call
            ContactCommand.MESSAGE -> R.drawable.ic_message
            ContactCommand.WHATSAPP -> R.drawable.ic_whatsapp
            ContactCommand.CONTACT -> R.drawable.ic_person
            null -> null
        }
        val drawable = icon?.let { ContextCompat.getDrawable(requireContext(), it)?.mutate() }?.apply {
            val size = (18 * resources.displayMetrics.density).toInt()
            setBounds(0, 0, size, size)
            setTint(android.graphics.Color.WHITE)
        }
        binding.modeTitle.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
        binding.modeTitle.setCompoundDrawablesRelative(drawable, null, null, null)
    }

    private fun scheduleContactCommand(query: String, items: List<SearchItem>) {
        commandJob?.cancel()
        val matches = items.filterIsInstance<SearchItem.ContactItem>()
        val settings = prefs.contactCommandSettings()
        if (!commandSession.automaticCandidate(
                settings, matches.size, viewModel.contactsLoading.value == true,
                viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), isSearchComposing()
            ) || !ContactsLoader.hasPermission(requireContext())
        ) return
        val contact = matches.single().contact
        if (ContactCommandQuery.parse(query)?.command != ContactCommand.CONTACT && contact.number.isNullOrBlank()) return
        commandJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(settings.delayMillis.toLong())
            if (query == currentQuery() && commandSession.automaticCandidate(
                    prefs.contactCommandSettings(), matches.size, viewModel.contactsLoading.value == true,
                    viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), isSearchComposing()
                ) && ContactsLoader.hasPermission(requireContext())
            ) executeContactCommand(contact, query)
        }
    }

    private fun executeContactCommand(contact: Contact, query: String = currentQuery()) {
        commandJob?.cancel()
        val parsed = ContactCommandQuery.parse(query) ?: return
        if (query != currentQuery() || viewModel.contactsLoading.value == true ||
            !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        if (!ContactsLoader.hasPermission(requireContext())) {
            requireContext().showToast(R.string.contacts_permission_denied)
            return
        }
        if (!AppSearchMatcher(parsed.name).matches(contact.name)) return
        if (!commandSession.claim(query)) return
        activePopupWindow?.dismiss()
        commandExecutor?.execute(contact, parsed.command)
    }

    private fun cancelAutomaticCommand() {
        if (mode != SearchMode.CONTACTS_ONLY) return
        commandJob?.cancel()
        commandExecutor?.cancel()
        commandSubmitQuery = null
        if (ContactCommandQuery.parse(currentQuery()) != null) commandSession.consumed = true
    }

    private class Titles(val apps: String, val contacts: String, val search: String, val permission: String)

    private suspend fun buildItems(
        query: String,
        apps: List<AppModel>,
        contacts: List<Contact>,
        engines: List<SearchEngine>,
        contactsGranted: Boolean,
        titles: Titles,
    ): List<SearchItem> {
        val commandQuery = if (mode == SearchMode.CONTACTS_ONLY) ContactCommandQuery.parse(query) else null
        val q = commandQuery?.name ?: query.trim()
        val matcher = AppSearchMatcher(q)
        val coroutineContext = currentCoroutineContext()
        val showHeaders = mode == SearchMode.ALL_SEARCH
        val items = mutableListOf<SearchItem>()

        if (mode.showsApps) {
            val matches = if (q.isEmpty()) apps else apps.filter {
                coroutineContext.ensureActive()
                matcher.matches(it.appLabel)
            }
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
                val isNumberQuery = commandQuery == null && q.length >= 3 && q.all { it.isDigit() || it in "+-() " }
                val digits = q.filter { it.isDigit() }
                val matches = when {
                    q.isEmpty() -> contacts
                    isNumberQuery -> contacts.filter {
                        coroutineContext.ensureActive()
                        it.number?.filter(Char::isDigit)?.contains(digits) == true
                    }
                    else -> contacts.filter {
                        coroutineContext.ensureActive()
                        matcher.matches(it.name)
                    }
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
        if (mode == SearchMode.CONTACTS_ONLY && ContactCommandQuery.parse(text) != null) {
            commandJob?.cancel()
            commandSubmitQuery = text
            rebuild()
            return
        }
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
        if (mode == SearchMode.CONTACTS_ONLY && ContactCommandQuery.parse(currentQuery()) != null) {
            executeContactCommand(contact)
            return
        }
        openContactDetails(contact)
    }

    private fun openContactDetails(contact: Contact) {
        val uri = if (contact.lookupKey.isNotEmpty())
            ContactsContract.Contacts.getLookupUri(contact.id, contact.lookupKey)
        else ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contact.id)
        if (!startSafely(Intent(Intent.ACTION_VIEW, uri)))
            requireContext().showToast(R.string.unable_to_open_contact)
    }

    override fun onContactTileClick(contact: Contact, anchor: View) {
        if (mode == SearchMode.CONTACTS_ONLY && ContactCommandQuery.parse(currentQuery()) != null)
            executeContactCommand(contact)
        else showContactActions(contact, anchor)
    }

    override fun onContactLongClick(contact: Contact) {
        cancelAutomaticCommand()
        openContactDetails(contact)
    }

    override fun onContactCall(contact: Contact) {
        cancelAutomaticCommand()
        val number = contact.number ?: return requireContext().showToast(R.string.no_phone_number)
        if (!startSafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))))
            requireContext().showToast(R.string.unable_to_open_app)
    }

    override fun onContactText(contact: Contact) {
        cancelAutomaticCommand()
        val number = contact.number ?: return requireContext().showToast(R.string.no_phone_number)
        if (!startSafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))))
            requireContext().showToast(R.string.unable_to_open_app)
    }

    override fun onContactWhatsApp(contact: Contact) {
        cancelAutomaticCommand()
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
        searchTextView?.showKeyboard() ?: binding.search.showKeyboard()
    }

    override fun onResume() {
        super.onResume()
        if (mode == SearchMode.CONTACTS_ONLY) rebuild()
    }

    override fun onPause() {
        if (mode == SearchMode.CONTACTS_ONLY) {
            cancelAutomaticCommand()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("contactCommandQuery", commandSession.query)
        outState.putBoolean("contactCommandConsumed", commandSession.consumed)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        commandJob?.cancel()
        commandExecutor?.cancel()
        commandExecutor = null
        filterJob?.cancel()
        activePopupWindow?.dismiss()
        activePopupWindow = null
        popupBackCallback.isEnabled = false
        searchTextView = null
        if (::adapter.isInitialized) adapter.close()
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
