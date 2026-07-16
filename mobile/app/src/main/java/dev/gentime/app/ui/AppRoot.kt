package dev.gentime.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.gentime.app.ui.screens.AttendanceScreen
import dev.gentime.app.ui.screens.HomeScreen
import dev.gentime.app.ui.screens.LeaveScreen
import dev.gentime.app.ui.screens.LoginScreen
import dev.gentime.app.ui.screens.SettingsScreen
import dev.gentime.app.ui.screens.SupervisorScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun AppRoot(state: AppState, vm: AppViewModel, activity: FragmentActivity) {
    if (state.loading && !state.signedIn) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (!state.signedIn) {
        LoginScreen(state = state, onSignIn = vm::signIn)
        return
    }

    val nav = rememberNavController()
    val isSupervisor = state.profile?.role == "supervisor" || state.profile?.role == "admin"

    val tabs = buildList {
        add(Tab("home", "Home", Icons.Filled.Home))
        add(Tab("attendance", "Attendance", Icons.Filled.CalendarMonth))
        add(Tab("leave", "Leave", Icons.Filled.EventNote))
        if (isSupervisor) add(Tab("supervisor", "Team", Icons.Filled.SupervisorAccount))
        add(Tab("settings", "Settings", Icons.Filled.Settings))
    }

    Scaffold(
        bottomBar = {
            val backStack by nav.currentBackStackEntryAsState()
            val current = backStack?.destination
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { androidx.compose.material3.Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm.repository, activity) }
            composable("attendance") { AttendanceScreen(vm.repository) }
            composable("leave") { LeaveScreen() }
            composable("supervisor") { SupervisorScreen() }
            composable("settings") { SettingsScreen(state, onSignOut = vm::signOut) }
        }
    }
}
