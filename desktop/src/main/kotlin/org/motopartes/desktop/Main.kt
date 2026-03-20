package org.motopartes.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.motopartes.db.DatabaseFactory
import org.motopartes.desktop.screen.*
import org.motopartes.repository.*
import org.motopartes.service.BackupService
import org.motopartes.service.FinanceService
import org.motopartes.service.OrderService
import org.motopartes.service.PurchaseService
import org.motopartes.service.UpdateService
import org.motopartes.service.VersionInfo
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.net.URI
import java.time.LocalDate
import kotlin.io.path.Path

fun main() {
    DatabaseFactory.init()

    val productRepo = ProductRepository()
    val clientRepo = ClientRepository()
    val supplierRepo = SupplierRepository()
    val dollarRateRepo = DollarRateRepository()
    val orderRepo = OrderRepository()
    val movementRepo = FinancialMovementRepository()
    val financeService = FinanceService(movementRepo, clientRepo, supplierRepo)
    val orderService = OrderService(orderRepo, productRepo, financeService)
    val purchaseService = PurchaseService(productRepo, financeService, supplierRepo)
    val backupService = BackupService()
    val updateService = UpdateService(UpdateService.APP_VERSION)

    val appIcon = AppIconPainter()

    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        Window(
            onCloseRequest = ::exitApplication,
            title = "Motopartes",
            state = windowState,
            icon = appIcon
        ) {
            App(productRepo, clientRepo, supplierRepo, dollarRateRepo, orderRepo, orderService, purchaseService, financeService, backupService, updateService)
        }
    }
}

@Composable
fun App(
    productRepo: ProductRepository,
    clientRepo: ClientRepository,
    supplierRepo: SupplierRepository,
    dollarRateRepo: DollarRateRepository,
    orderRepo: OrderRepository,
    orderService: OrderService,
    purchaseService: PurchaseService,
    financeService: FinanceService,
    backupService: BackupService,
    updateService: UpdateService
) {
    var currentScreen by remember { mutableStateOf(Screen.PRODUCTS) }
    var snackMessage by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf<VersionInfo?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }

    // Check for updates on launch (background)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            updateAvailable = updateService.checkForUpdate()
        }
    }

    MaterialTheme(colorScheme = MotopartesColors, typography = MotopartesTypography) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(remember { SnackbarHostState() }.also { host ->
                    LaunchedEffect(snackMessage) {
                        snackMessage?.let { host.showSnackbar(it, duration = SnackbarDuration.Short); snackMessage = null }
                    }
                })
            },
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize()) {
                // Banner
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = AppIconPainter(),
                            contentDescription = "Motopartes",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "MOTOPARTES",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sistema de Gestion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))

                        // Backup button
                        IconButton(onClick = {
                            val dialog = FileDialog(null as Frame?, "Guardar backup", FileDialog.SAVE)
                            dialog.file = "motopartes-backup-${LocalDate.now()}.db"
                            dialog.isVisible = true
                            if (dialog.file != null) {
                                val dest = Path(dialog.directory, dialog.file)
                                backupService.backup(dest).fold(
                                    onSuccess = { snackMessage = "Backup guardado en ${dialog.file}" },
                                    onFailure = { snackMessage = "Error: ${it.message}" }
                                )
                            }
                        }) {
                            Icon(Icons.Default.Backup, "Backup", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Restore button
                        IconButton(onClick = {
                            val dialog = FileDialog(null as Frame?, "Restaurar backup", FileDialog.LOAD)
                            dialog.setFilenameFilter { _, name -> name.endsWith(".db") }
                            dialog.isVisible = true
                            if (dialog.file != null) {
                                showRestoreConfirm = dialog.directory + dialog.file
                            }
                        }) {
                            Icon(Icons.Default.Restore, "Restaurar", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(Modifier.width(12.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(currentScreen.icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(currentScreen.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }

                // Update banner
                if (updateAvailable != null && !updateDismissed) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Nueva version disponible: ${updateAvailable!!.version}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = {
                                Desktop.getDesktop().browse(URI(updateAvailable!!.downloadUrl))
                            }) {
                                Text("Descargar", style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { updateDismissed = true }) {
                                Text("Ignorar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
                            }
                        }
                    }
                }

                // Content: nav rail + screens
                Row(Modifier.weight(1f)) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Screen.entries.forEach { screen ->
                            NavigationRailItem(
                                selected = currentScreen == screen,
                                onClick = { currentScreen = screen },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            Screen.PRODUCTS -> ProductScreen(productRepo, dollarRateRepo)
                            Screen.CLIENTS -> ClientScreen(clientRepo)
                            Screen.ORDERS -> OrderScreen(orderRepo, orderService, productRepo, clientRepo, dollarRateRepo)
                            Screen.PURCHASES -> PurchaseScreen(purchaseService, productRepo)
                            Screen.FINANCE -> FinanceScreen(financeService, clientRepo, supplierRepo)
                            Screen.SUPPLIER -> SupplierScreen(supplierRepo)
                            Screen.DOLLAR_RATE -> DollarRateScreen(dollarRateRepo)
                        }
                    }
                }

                // Footer
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Motopartes v1.0",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            "Sistema de gestion de venta minorista",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Restore confirmation dialog
        showRestoreConfirm?.let { path ->
            AlertDialog(
                onDismissRequest = { showRestoreConfirm = null },
                title = { Text("Restaurar backup") },
                text = { Text("Se reemplazara la base de datos actual con el backup seleccionado. Esta accion no se puede deshacer.\n\nSe recomienda hacer un backup antes de restaurar.\n\n¿Continuar?") },
                confirmButton = {
                    Button(
                        onClick = {
                            backupService.restore(Path(path)).fold(
                                onSuccess = { snackMessage = "Backup restaurado. Reinicie la aplicacion para ver los cambios." },
                                onFailure = { snackMessage = "Error: ${it.message}" }
                            )
                            showRestoreConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Restaurar") }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirm = null }) { Text("Cancelar") }
                }
            )
        }
    }
}
