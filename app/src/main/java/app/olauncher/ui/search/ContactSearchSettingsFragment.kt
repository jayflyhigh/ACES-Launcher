package app.olauncher.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.Constants
import app.olauncher.data.SearchMode
import app.olauncher.databinding.FragmentContactSearchSettingsBinding
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog
import app.olauncher.helper.showToast
import app.olauncher.ui.BaseFragment

class ContactSearchSettingsFragment : BaseFragment() {
    private var dialog: OlDialog? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentContactSearchSettingsBinding.inflate(inflater, container, false).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentContactSearchSettingsBinding.bind(view)
        val prefs = Prefs(requireContext())
        val settings = prefs.contactCommandSettings()
        binding.pageAppearance.setOnClickListener {
            findNavController().navigate(R.id.searchAppearanceFragment,
                Bundle().apply { putString(Constants.Key.SEARCH_MODE, SearchMode.CONTACTS_ONLY.name) })
        }
        binding.automaticCommands.isChecked = settings.automatic
        binding.rememberNumbers.isChecked = settings.rememberNumbers
        binding.showCommandIndicator.isChecked = settings.showIndicator
        binding.commandDelay.check(when (settings.delayMillis) {
            500 -> R.id.delayBalanced
            750 -> R.id.delayCautious
            else -> R.id.delayFast
        })
        binding.minimumTwo.text = resources.getQuantityString(R.plurals.contact_command_characters, 2, 2)
        binding.minimumThree.text = resources.getQuantityString(R.plurals.contact_command_characters, 3, 3)
        binding.minimumFour.text = resources.getQuantityString(R.plurals.contact_command_characters, 4, 4)
        binding.commandMinimum.check(when (settings.minimumCharacters) {
            3 -> R.id.minimumThree
            4 -> R.id.minimumFour
            else -> R.id.minimumTwo
        })

        binding.automaticCommands.setOnCheckedChangeListener { _, checked ->
            prefs.setContactCommandSettings(prefs.contactCommandSettings().copy(automatic = checked))
        }
        binding.rememberNumbers.setOnCheckedChangeListener { _, checked ->
            prefs.setContactCommandSettings(prefs.contactCommandSettings().copy(rememberNumbers = checked))
        }
        binding.showCommandIndicator.setOnCheckedChangeListener { _, checked ->
            prefs.setContactCommandSettings(prefs.contactCommandSettings().copy(showIndicator = checked))
        }
        binding.commandDelay.setOnCheckedChangeListener { _, id ->
            val delay = when (id) {
                R.id.delayBalanced -> 500
                R.id.delayCautious -> 750
                else -> 250
            }
            prefs.setContactCommandSettings(prefs.contactCommandSettings().copy(delayMillis = delay))
        }
        binding.commandMinimum.setOnCheckedChangeListener { _, id ->
            val minimum = when (id) {
                R.id.minimumThree -> 3
                R.id.minimumFour -> 4
                else -> 2
            }
            prefs.setContactCommandSettings(prefs.contactCommandSettings().copy(minimumCharacters = minimum))
        }
        binding.clearNumbers.setOnClickListener {
            dialog?.dismiss()
            dialog = requireContext().createDialog(
                title = R.string.contact_command_clear,
                action = R.string.contact_command_clear,
                message = R.string.contact_command_clear_confirm,
                onAction = {
                    prefs.clearRememberedContactNumbers()
                    context?.showToast(R.string.contact_command_cleared)
                },
            ).also { it.showRespectingStatusBar() }
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { root, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
    }
}
