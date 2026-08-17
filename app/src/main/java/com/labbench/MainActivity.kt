package com.labbench

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.labbench.data.LabDatabase
import com.labbench.data.LabRepository
import com.labbench.timer.ProtocolTimerService
import com.labbench.ui.calculators.CalculatorDetailScreen
import com.labbench.ui.calculators.CalculatorHubScreen
import com.labbench.ui.cultures.CultureDetailScreen
import com.labbench.ui.cultures.CultureListScreen
import com.labbench.ui.inventory.InventoryScreen
import com.labbench.ui.theme.LabBenchTheme
import com.labbench.ui.timers.TimerScreen
import com.labbench.ui.today.TodayScreen

class LabBenchApp : Application() {
    val repository: LabRepository by lazy {
        LabRepository(LabDatabase.get(this)) { Prefs.operator(this) }
    }

    override fun onCreate() {
        super.onCreate()
        ProtocolTimerService.createChannels(this)
    }
}

object Prefs {
    private const val FILE = "labbench_prefs"
    fun operator(app: Application): String =
        app.getSharedPreferences(FILE, 0).getString("operator", "").orEmpty()

    fun setOperator(app: Application, value: String) {
        app.getSharedPreferences(FILE, 0).edit().putString("operator", value).apply()
    }
}

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationsIfNeeded()

        val repository = (application as LabBenchApp).repository
        setContent {
            LabBenchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LabBenchRoot(repository)
                }
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private data class Destination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    Destination("today", "Today", Icons.Outlined.Today),
    Destination("calculators", "Calculate", Icons.Outlined.Calculate),
    Destination("cultures", "Cultures", Icons.Outlined.Science),
    Destination("timers", "Timers", Icons.Outlined.Timer),
    Destination("inventory", "Inventory", Icons.Outlined.Inventory2)
)

@Composable
fun LabBenchRoot(repository: LabRepository) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomDestinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute?.startsWith(destination.route) == true,
                        onClick = { navController.navigateTab(destination.route) },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding)
        ) {
            composable("today") {
                TodayScreen(
                    repository = repository,
                    onOpenCulture = { navController.navigate("cultures/$it") },
                    onOpenTimers = { navController.navigateTab("timers") }
                )
            }
            composable("calculators") {
                CalculatorHubScreen(onOpen = { navController.navigate("calculators/$it") })
            }
            composable("calculators/{id}") { entry ->
                CalculatorDetailScreen(
                    calculatorId = entry.arguments?.getString("id").orEmpty(),
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("cultures") {
                CultureListScreen(
                    repository = repository,
                    onOpen = { navController.navigate("cultures/$it") }
                )
            }
            composable("cultures/{id}") { entry ->
                CultureDetailScreen(
                    cultureId = entry.arguments?.getString("id").orEmpty(),
                    repository = repository,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("timers") { TimerScreen(repository = repository) }
            composable("inventory") { InventoryScreen(repository = repository) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
