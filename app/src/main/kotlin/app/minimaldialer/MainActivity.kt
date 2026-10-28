package app.minimaldialer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.minimaldialer.calling.domain.CallSelection
import app.minimaldialer.calling.telecom.DefaultDialerRole
import app.minimaldialer.contacts.ui.ContactsScreen
import app.minimaldialer.contacts.ui.ContactsViewModel
import app.minimaldialer.contacts.ui.FavoritesScreen
import app.minimaldialer.contacts.ui.RecentsScreen
import app.minimaldialer.contacts.ui.RecentsViewModel
import app.minimaldialer.core.designsystem.MinimalDialerTheme
import app.minimaldialer.core.model.ThemeMode
import app.minimaldialer.dialer.DefaultDialerBanner
import app.minimaldialer.dialer.DialerScreen
import app.minimaldialer.dialer.DialerViewModel
import app.minimaldialer.recordings.RecordingsScreen
import app.minimaldialer.recordings.RecordingsViewModel
import app.minimaldialer.settings.SettingsScreen
import app.minimaldialer.settings.SettingsViewModel
import kotlinx.coroutines.flow.MutableStateFlow

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    KEYPAD("keypad", "Keypad", Icons.Filled.Dialpad),
    RECENTS("recents", "Recents", Icons.Filled.History),
    FAVORITES("favorites", "Favourites", Icons.Filled.Star),
    CONTACTS("contacts", "Contacts", Icons.Filled.People),
    RECORDINGS("recordings", "Recordings", Icons.Filled.FiberManualRecord),
}

private const val SETTINGS = "settings"

class MainActivity : ComponentActivity() {

    /** Number handed to us by a DIAL / tel: intent, consumed by the keypad. */
    private val incomingNumber = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        incomingNumber.value = intent?.dialNumber()
        val appContainer = container
        setContent {
            val theme by appContainer.preferences.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            MinimalDialerTheme(theme) { App(appContainer) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dialNumber()?.let { incomingNumber.value = it }
    }

    @Composable
    private fun App(container: AppContainer) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val calls by container.calls.calls.collectAsStateWithLifecycle()

        val dialerVm: DialerViewModel = viewModel { DialerViewModel(container.contacts, container.calls) }
        val contactsVm: ContactsViewModel = viewModel { ContactsViewModel(container.contacts) }
        val recentsVm: RecentsViewModel = viewModel { RecentsViewModel(container.callLog, container.contacts) }

        val pendingNumber by incomingNumber.collectAsStateWithLifecycle()
        LaunchedEffect(pendingNumber) {
            pendingNumber?.let {
                dialerVm.setNumber(it)
                incomingNumber.value = null
                nav.navigate(Tab.KEYPAD.route) { launchSingleTop = true }
            }
        }

        var isDefault by remember { mutableStateOf(DefaultDialerRole.isHeld(this)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = DefaultDialerRole.isHeld(this@MainActivity) }
        val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            isDefault = DefaultDialerRole.isHeld(this)
        }
        val call: (String) -> Unit = { number -> dialerVm.call(number) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (route != SETTINGS) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding: PaddingValues ->
            NavHost(nav, startDestination = Tab.KEYPAD.route) {
                composable(Tab.KEYPAD.route) {
                    DialerScreen(
                        viewModel = dialerVm,
                        contentPadding = padding,
                        onOpenSettings = { nav.navigate(SETTINGS) },
                        banner = {
                            when {
                                CallSelection.hasLiveCall(calls) -> ReturnToCallBanner { container.calls.openInCallScreen(this@MainActivity) }
                                !isDefault -> DefaultDialerBanner { DefaultDialerRole.requestIntent(this@MainActivity)?.let { roleLauncher.launch(it) } }
                                else -> Unit
                            }
                        },
                    )
                }
                composable(Tab.RECENTS.route) { RecentsScreen(recentsVm, call, padding) }
                composable(Tab.FAVORITES.route) { FavoritesScreen(contactsVm, call, padding) }
                composable(Tab.CONTACTS.route) { ContactsScreen(contactsVm, call, padding) }
                composable(Tab.RECORDINGS.route) {
                    val vm: RecordingsViewModel = viewModel { RecordingsViewModel(container.library, container.player, container.sharer) }
                    RecordingsScreen(vm, padding) { nav.navigate(SETTINGS) }
                }
                composable(SETTINGS) {
                    val vm: SettingsViewModel = viewModel {
                        SettingsViewModel(container.preferences, container.recorder, container.library, container.sharedCopies, container.recordingsDir)
                    }
                    SettingsScreen(
                        viewModel = vm,
                        isDefaultDialer = { DefaultDialerRole.isHeld(this@MainActivity) },
                        defaultDialerIntent = { DefaultDialerRole.requestIntent(this@MainActivity) },
                        appVersion = BuildConfig.VERSION_NAME,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReturnToCallBanner(onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        color = app.minimaldialer.core.designsystem.DialerTheme.callColors.call,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            "Return to call",
            color = app.minimaldialer.core.designsystem.DialerTheme.callColors.onCall,
            style = MaterialTheme.typography.titleSmall,
            modifier = androidx.compose.ui.Modifier.padding(14.dp),
        )
    }
}
