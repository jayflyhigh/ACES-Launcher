package app.olauncher.ui.search

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.data.Contact
import app.olauncher.data.ContactCommand
import app.olauncher.data.ContactCommandTarget
import app.olauncher.data.ContactCommandTargets
import app.olauncher.data.ContactPhoneNumber
import app.olauncher.data.Prefs
import app.olauncher.data.preferredContactNumber
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog
import app.olauncher.helper.showToast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Database access happens only after a command is chosen, never on a keystroke. */
internal class ContactCommandExecutor(
    private val context: Context,
    private val owner: LifecycleOwner,
) {
    private val prefs = Prefs(context)
    private var job: Job? = null
    private var dialog: OlDialog? = null

    fun cancel() {
        job?.cancel()
        job = null
        dialog?.dismiss()
        dialog = null
    }

    fun openApp(command: ContactCommand) {
        cancel()
        val opened = when (command) {
            ContactCommand.PHONE -> start(Intent(Intent.ACTION_DIAL))
            ContactCommand.MESSAGE -> {
                val packageName = Telephony.Sms.getDefaultSmsPackage(context)
                val intent = packageName?.let(context.packageManager::getLaunchIntentForPackage)
                (intent != null && start(intent)) ||
                    start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING))
            }
            ContactCommand.WHATSAPP -> listOf("com.whatsapp", "com.whatsapp.w4b").any { packageName ->
                context.packageManager.getLaunchIntentForPackage(packageName)?.let(::start) == true
            }
            ContactCommand.CONTACT -> start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CONTACTS))
        }
        if (!opened) context.showToast(
            if (command == ContactCommand.WHATSAPP) R.string.whatsapp_not_installed else R.string.unable_to_open_app,
        )
    }

    fun execute(contact: Contact, command: ContactCommand) {
        cancel()
        resolve(contact, command) { target ->
            if (command == ContactCommand.CONTACT) {
                open(Intent(Intent.ACTION_VIEW, target.uri), R.string.unable_to_open_contact)
            } else {
                val remembered = if (prefs.contactCommandSettings().rememberNumbers)
                    prefs.rememberedContactNumber(target.lookupKey, command) else null
                if (remembered != null && target.numbers.none { it.identity == remembered }) {
                    prefs.rememberContactNumber(target.lookupKey, command, null)
                }
                val number = preferredContactNumber(target.numbers, remembered)
                when {
                    target.numbers.isEmpty() -> context.showToast(R.string.no_phone_number)
                    number != null -> openNumber(command, target, number)
                    else -> chooseNumber(contact, command, target)
                }
            }
        }
    }

    private fun resolve(contact: Contact, command: ContactCommand, action: (ContactCommandTarget) -> Unit) {
        job = owner.lifecycleScope.launch {
            try {
                val target = withContext(Dispatchers.IO) {
                    ContactCommandTargets.load(context, contact, command != ContactCommand.CONTACT)
                }
                if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                if (target == null) context.showToast(R.string.contact_command_missing)
                else action(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                Log.w("ContactCommands", "Contact permission unavailable", e)
                context.showToast(R.string.contacts_permission_denied)
            } catch (e: RuntimeException) {
                Log.e("ContactCommands", "Unable to resolve contact command", e)
                context.showToast(R.string.contact_command_load_failed)
            }
        }
    }

    private fun chooseNumber(contact: Contact, command: ContactCommand, target: ContactCommandTarget) {
        var selected = false
        dialog = context.createDialog(
            title = R.string.contact_command_number,
            action = android.R.string.cancel,
            content = { container ->
                val rows = LinearLayout(container.context).apply { orientation = LinearLayout.VERTICAL }
                target.numbers.forEach { number ->
                    rows.addView(AppCompatTextView(container.context).apply {
                        setTextAppearance(R.style.TextSmall)
                        text = context.getString(R.string.contact_command_number_label, number.label, number.number)
                        val padding = (12 * resources.displayMetrics.density).toInt()
                        setPadding(padding, padding, padding, padding)
                        minHeight = (56 * resources.displayMetrics.density).toInt()
                        setBackgroundResource(R.drawable.bg_quick_bar)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = padding / 2 }
                        setOnClickListener {
                            if (selected) return@setOnClickListener
                            selected = true
                            dialog?.dismiss()
                            dialog = null
                            // A contact may have been edited while the number chooser was open.
                            resolve(contact, command) { fresh ->
                                val current = fresh.numbers.firstOrNull { it.identity == number.identity }
                                if (current == null) context.showToast(R.string.contact_command_number_changed)
                                else if (openNumber(command, fresh, current) &&
                                    prefs.contactCommandSettings().rememberNumbers
                                ) {
                                    prefs.rememberContactNumber(fresh.lookupKey, command, current.identity)
                                }
                            }
                        }
                    })
                }
                ScrollView(container.context).apply { addView(rows) }
            },
        ).also { it.showRespectingStatusBar() }
    }

    private fun openNumber(
        command: ContactCommand,
        target: ContactCommandTarget,
        number: ContactPhoneNumber,
    ): Boolean = when (command) {
        ContactCommand.PHONE -> open(
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.number, null)), R.string.unable_to_open_app
        )
        ContactCommand.MESSAGE -> open(
            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number.number, null)), R.string.unable_to_open_app
        )
        ContactCommand.WHATSAPP -> {
            val international = (number.normalizedNumber?.takeIf { it.isNotBlank() } ?: number.number).trim()
            val digits = when {
                international.startsWith("+") -> international.filter(Char::isDigit)
                international.startsWith("00") -> international.filter(Char::isDigit).drop(2)
                else -> ""
            }
            val uri = "https://api.whatsapp.com/send?phone=$digits".toUri()
            val opened = digits.length in 7..15 && !digits.startsWith("0") &&
                listOf("com.whatsapp", "com.whatsapp.w4b").any {
                start(Intent(Intent.ACTION_VIEW, uri).setPackage(it))
            }
            if (!opened) {
                var accepted = false
                dialog = context.createDialog(
                    title = R.string.whatsapp,
                    action = R.string.contact_command_contact,
                    message = R.string.contact_command_whatsapp_failed,
                    onAction = {
                        if (!accepted) {
                            accepted = true
                            open(Intent(Intent.ACTION_VIEW, target.uri), R.string.unable_to_open_contact)
                        }
                    },
                ).also { it.showRespectingStatusBar() }
            }
            opened
        }
        ContactCommand.CONTACT -> error("Contact details do not use a phone number")
    }

    private fun open(intent: Intent, error: Int): Boolean {
        val opened = start(intent)
        if (!opened) context.showToast(error)
        return opened
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w("ContactCommands", "No application can handle command", e)
        false
    } catch (e: SecurityException) {
        Log.w("ContactCommands", "Application refused command", e)
        false
    }
}
