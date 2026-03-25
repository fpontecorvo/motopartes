package org.motopartes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.motopartes.mobile.api.ApiClient
import org.motopartes.mobile.api.getApiKey
import org.motopartes.mobile.api.getServerUrl
import org.motopartes.mobile.screen.*
import org.motopartes.mobile.ui.theme.MotopartesTheme

enum class Screen(val route: String, val title: String, val icon: ImageVector) {
    PRODUCTS("products", "Productos", Icons.Default.Inventory2),
    ORDERS("orders", "Ventas", Icons.Default.Receipt),
    CLIENTS("clients", "Clientes", Icons.Default.People),
    DOLLAR("dollar", "Dolar", Icons.Default.AttachMoney),
    CONFIG("config", "Config", Icons.Default.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MotopartesTheme {
                MotopartesApp()
            }
        }
    }
}

@Composable
fun MotopartesApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    var apiClient by remember { mutableStateOf<ApiClient?>(null) }
    var isConnected by remember { mutableStateOf(false) }

    // Load saved connection on startup
    LaunchedEffect(Unit) {
        val url = context.getServerUrl()
        val key = context.getApiKey()
        if (url.isNotBlank() && key.isNotBlank()) {
            val client = ApiClient(url, key)
            val health = client.health()
            if (health.isSuccess) {
                apiClient = client
                isConnected = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Screen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(screen.icon, screen.title) },
                        label = { Text(screen.title) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (isConnected) Screen.PRODUCTS.route else Screen.CONFIG.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.PRODUCTS.route) {
                if (apiClient != null) ProductScreen(apiClient!!)
                else NotConnectedScreen { navController.navigate(Screen.CONFIG.route) }
            }
            composable(Screen.ORDERS.route) {
                if (apiClient != null) OrderScreen(apiClient!!)
                else NotConnectedScreen { navController.navigate(Screen.CONFIG.route) }
            }
            composable(Screen.CLIENTS.route) {
                if (apiClient != null) ClientScreen(apiClient!!)
                else NotConnectedScreen { navController.navigate(Screen.CONFIG.route) }
            }
            composable(Screen.DOLLAR.route) {
                if (apiClient != null) DollarRateScreen(apiClient!!)
                else NotConnectedScreen { navController.navigate(Screen.CONFIG.route) }
            }
            composable(Screen.CONFIG.route) {
                ConfigScreen(
                    isConnected = isConnected,
                    onConnect = { client ->
                        apiClient = client
                        isConnected = true
                        navController.navigate(Screen.PRODUCTS.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onDisconnect = {
                        apiClient?.close()
                        apiClient = null
                        isConnected = false
                    }
                )
            }
        }
    }
}
