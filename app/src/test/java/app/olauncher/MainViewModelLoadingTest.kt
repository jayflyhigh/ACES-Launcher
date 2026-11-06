package app.olauncher

import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.Looper
import android.os.UserHandle
import androidx.lifecycle.ViewModelProvider
import app.olauncher.ui.search.ContactCommandTestActivity
import app.olauncher.ui.search.awaitCommand
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLauncherApps
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 28], shadows = [CountingLauncherShadow::class])
@LooperMode(LooperMode.Mode.PAUSED)
class MainViewModelLoadingTest {
    @Test
    fun `foreground package bursts and resume produce only one app scan`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            CountingLauncherShadow.scans.set(0)
            val vm = ViewModelProvider(activity)[MainViewModel::class.java]
            repeat(10) { vm.invalidateAppList(refreshNow = true) }
            vm.getAppListIfStale()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(99))
            assertEquals(0, CountingLauncherShadow.scans.get())
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1))
            awaitCommand { vm.appList.value != null }
            assertEquals(1, CountingLauncherShadow.scans.get())
            vm.getAppListIfStale()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(1, CountingLauncherShadow.scans.get())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun `background changes do not scan until resume even when cache is fresh`() {
        val controller = Robolectric.buildActivity(ContactCommandTestActivity::class.java).setup()
        try {
            val activity = controller.get()
            CountingLauncherShadow.scans.set(0)
            val vm = ViewModelProvider(activity)[MainViewModel::class.java]
            vm.getAppList()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            awaitCommand { vm.appList.value != null }
            assertEquals(1, CountingLauncherShadow.scans.get())
            repeat(10) { vm.invalidateAppList(refreshNow = false) }
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
            assertEquals(1, CountingLauncherShadow.scans.get())
            vm.getAppListIfStale()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            awaitCommand { CountingLauncherShadow.scans.get() == 2 }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}

@Implements(LauncherApps::class)
class CountingLauncherShadow : ShadowLauncherApps() {
    companion object {
        val scans = AtomicInteger()
    }

    @Implementation
    public override fun getActivityList(packageName: String?, user: UserHandle?): List<LauncherActivityInfo> {
        if (packageName == null) scans.incrementAndGet()
        return emptyList()
    }
}
