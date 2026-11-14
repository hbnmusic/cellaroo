package app.cellaroo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.cellaroo.MainViewModel
import app.cellaroo.R
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: Int)

private val tabs = listOf(
    Tab("cellar", "Cellar", R.drawable.ic_cellar),
    Tab("scan", "Scan", R.drawable.ic_camera),
    Tab("tonight", "Tonight", R.drawable.ic_glass),
    Tab("stats", "Stats", R.drawable.ic_chart),
)

fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            launch {
                val result = snackbar.showSnackbar(
                    message = e.message,
                    actionLabel = if (e.undoId != null) "Undo" else null,
                    withDismissAction = e.undoId == null,
                    duration = if (e.undoId != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed && e.undoId != null) vm.undo(e.undoId)
            }
        }
    }
    LaunchedEffect(Unit) {
        vm.nav.collect { target ->
            if (target == "home") nav.popBackStack("cellar", inclusive = false) else nav.navigate(target) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = Palette.Background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = Palette.Surface) {
                    for (t in tabs) {
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.openTab(t.route) },
                            icon = { Icon(painterResource(t.icon), contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "cellar",
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).imePadding(),
        ) {
            composable("cellar") { CellarScreen(vm, nav) }
            composable("scan") { ScanScreen(vm, nav) }
            composable("tonight") { TonightScreen(vm, nav) }
            composable("stats") { StatsScreen(vm, nav) }
            composable("wine/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                WineDetailScreen(vm, nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("edit/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                WineEditScreen(vm, nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("paste/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                PasteScreen(vm, nav, it.arguments?.getLong("id") ?: 0L)
            }
            composable("scanReview") { ScanReviewScreen(vm, nav) }
            composable("reconcileReview") { ReconcileReviewScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("locations") { LocationsScreen(vm, nav) }
        }
    }

    val label = busy
    if (label != null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = Palette.SurfaceHigh) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
