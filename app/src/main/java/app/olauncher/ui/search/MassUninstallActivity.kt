package app.olauncher.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import app.olauncher.R
import java.util.ArrayDeque

class MassUninstallActivity : Activity() {
    private val uninstallQueue = ArrayDeque<String>()
    private var currentPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            intent.getStringArrayListExtra(EXTRA_PACKAGES).orEmpty().forEach(uninstallQueue::addLast)
        } else {
            savedInstanceState.getStringArrayList(STATE_QUEUE).orEmpty().forEach(uninstallQueue::addLast)
            currentPackage = savedInstanceState.getString(STATE_CURRENT_PACKAGE)
        }
        if (currentPackage == null) launchNextUninstall()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(STATE_QUEUE, ArrayList(uninstallQueue))
        outState.putString(STATE_CURRENT_PACKAGE, currentPackage)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_UNINSTALL) return
        handleUninstallResult(resultCode)
    }

    internal fun handleUninstallResult(resultCode: Int) {
        val packageName = currentPackage ?: return finish()
        currentPackage = null
        if (resultCode != RESULT_OK && isAppInstalled(packageName)) {
            uninstallQueue.clear()
            Toast.makeText(this, R.string.mass_uninstall_stopped, Toast.LENGTH_SHORT).show()
            finish()
        } else {
            launchNextUninstall()
        }
    }

    private fun launchNextUninstall() {
        while (uninstallQueue.isNotEmpty()) {
            val packageName = uninstallQueue.removeFirst()
            if (!isAppInstalled(packageName)) continue
            currentPackage = packageName
            launchCurrentUninstall()
            return
        }
        Toast.makeText(this, R.string.mass_uninstall_complete, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun launchCurrentUninstall() {
        val packageName = currentPackage ?: return finish()
        try {
            startActivityForResult(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")),
                REQUEST_UNINSTALL,
            )
        } catch (_: Exception) {
            uninstallQueue.clear()
            Toast.makeText(this, R.string.unable_to_open_app, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun isAppInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val EXTRA_PACKAGES = "packages"
        private const val STATE_QUEUE = "uninstall_queue"
        private const val STATE_CURRENT_PACKAGE = "current_package"
        private const val REQUEST_UNINSTALL = 1001

        fun start(context: Context, packageNames: List<String>): Boolean {
            if (packageNames.isEmpty()) return false
            return try {
                context.startActivity(Intent(context, MassUninstallActivity::class.java).apply {
                    if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(packageNames))
                })
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}