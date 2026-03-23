package org.motopartes.desktop.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.motopartes.config.AppPaths
import org.motopartes.service.*
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

@Composable
fun ServiceScreen(
    serviceManager: ServiceManager,
    onRestart: (ServiceType) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val services by serviceManager.servicesFlow.collectAsState()
    val logs by serviceManager.logsFlow.collectAsState()

    var selectedLogSource by remember { mutableStateOf("All") }
    var selectedLogLevel by remember { mutableStateOf("All") }

    val logSources = listOf("All", "Database", "API", "MCP", "ServiceManager")
    val logLevels = listOf("All", "DEBUG", "INFO", "WARN", "ERROR")

    val filteredLogs = remember(logs, selectedLogSource, selectedLogLevel) {
        logs.filter { log ->
            val sourceMatch = selectedLogSource == "All" || log.source.contains(selectedLogSource, ignoreCase = true)
            val levelMatch = selectedLogLevel == "All" || log.level.name == selectedLogLevel
            sourceMatch && levelMatch
        }
    }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            lazyListState.scrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Summary Bar
            SummaryBar(services)

            Spacer(modifier = Modifier.height(16.dp))

            // Service Cards
            ServiceCardsSection(
                services = services,
                onRestart = onRestart,
                onCopyMcpConfig = { port ->
                    scope.launch {
                        copyMcpConfigToClipboard(port)
                        snackbarHostState.showSnackbar("Config copiada al portapapeles")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Log Viewer
            LogViewerSection(
                logs = filteredLogs,
                lazyListState = lazyListState,
                selectedSource = selectedLogSource,
                selectedLevel = selectedLogLevel,
                logSources = logSources,
                logLevels = logLevels,
                onSourceChanged = { selectedLogSource = it },
                onLevelChanged = { selectedLogLevel = it },
                onClearLogs = { serviceManager.clearLogs() }
            )
        }
    }
}

@Composable
private fun SummaryBar(services: Map<ServiceType, Service>) {
    val runningCount = services.values.count { it.status == ServiceStatus.RUNNING }
    val totalCount = services.size
    val statusText = "$runningCount/$totalCount servicios activos"

    val indicatorColor = when {
        runningCount == totalCount -> Color(0xFF4CAF50)
        runningCount > 0 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(indicatorColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ServiceCardsSection(
    services: Map<ServiceType, Service>,
    onRestart: (ServiceType) -> Unit,
    onCopyMcpConfig: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        services.forEach { (serviceType, service) ->
            ServiceCard(
                service = service,
                onRestart = { onRestart(serviceType) },
                onCopyMcpConfig = onCopyMcpConfig
            )
        }
    }
}

@Composable
private fun ServiceCard(
    service: Service,
    onRestart: () -> Unit,
    onCopyMcpConfig: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Service Name and Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = service.type.displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(service.status)
                    IconButton(
                        onClick = onRestart,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reiniciar servicio",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Port and Uptime
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                val servicePort = service.port
                if (servicePort != null && servicePort > 0) {
                    InfoItem("Puerto", servicePort.toString())
                }
                InfoItem("Uptime", calculateUptime(service.startedAt))
            }

            // Error message (if applicable)
            val errorMsg = service.errorMessage
            if (service.status == ServiceStatus.ERROR && errorMsg != null) {
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFF44336),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // MCP Config Copy Button
            val mcpPort = service.port
            if (service.type == ServiceType.MCP_SERVER && mcpPort != null && mcpPort > 0) {
                Button(
                    onClick = { onCopyMcpConfig(mcpPort) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copiar config JSON para Claude Desktop")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: ServiceStatus) {
    val (backgroundColor, textColor, label) = when (status) {
        ServiceStatus.RUNNING -> Triple(Color(0xFF4CAF50), Color.White, "RUNNING")
        ServiceStatus.STARTING -> Triple(Color(0xFFFFC107), Color.Black, "STARTING")
        ServiceStatus.ERROR -> Triple(Color(0xFFF44336), Color.White, "ERROR")
        ServiceStatus.STOPPED -> Triple(Color(0xFF9E9E9E), Color.White, "STOPPED")
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LogViewerSection(
    logs: List<LogEntry>,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    selectedSource: String,
    selectedLevel: String,
    logSources: List<String>,
    logLevels: List<String>,
    onSourceChanged: (String) -> Unit,
    onLevelChanged: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Filtros:", style = MaterialTheme.typography.labelSmall)

            FilterDropdown(
                label = "Origen",
                items = logSources,
                selectedItem = selectedSource,
                onItemSelected = onSourceChanged,
                modifier = Modifier.weight(1f)
            )

            FilterDropdown(
                label = "Nivel",
                items = logLevels,
                selectedItem = selectedLevel,
                onItemSelected = onLevelChanged,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onClearLogs,
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Limpiar", fontSize = 12.sp)
            }

            Button(
                onClick = { openLogDirectory() },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Folder,
                    contentDescription = "Abrir carpeta de logs",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Abrir carpeta", fontSize = 12.sp)
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        // Log List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "No hay logs para mostrar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            } else {
                items(logs) { logEntry ->
                    LogEntryRow(logEntry)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    items: List<String>,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            readOnly = true,
            value = selectedItem,
            onValueChange = {},
            label = { Text(label, fontSize = 11.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .height(48.dp),
            textStyle = MaterialTheme.typography.labelSmall
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, fontSize = 12.sp) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LogEntryRow(logEntry: LogEntry) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm:ss") }
    val timestamp = logEntry.timestamp.format(timeFormatter)

    val badgeColor = when (logEntry.level) {
        LogLevel.DEBUG -> Color(0xFF9E9E9E)
        LogLevel.INFO -> Color(0xFF2196F3)
        LogLevel.WARN -> Color(0xFFFFC107)
        LogLevel.ERROR -> Color(0xFFF44336)
    }

    val badgeTextColor = when (logEntry.level) {
        LogLevel.WARN -> Color.Black
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.widthIn(max = 60.dp)
        )

        Surface(
            shape = RoundedCornerShape(3.dp),
            color = badgeColor,
            modifier = Modifier.widthIn(max = 50.dp)
        ) {
            Text(
                text = logEntry.level.name,
                color = badgeTextColor,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(2.dp, 1.dp),
                fontSize = 10.sp
            )
        }

        Text(
            text = logEntry.source,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.widthIn(max = 80.dp)
        )

        Text(
            text = logEntry.message,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2
        )
    }
}

private fun copyMcpConfigToClipboard(port: Int) {
    val jsonConfig = """{
  "mcpServers": {
    "motopartes": {
      "url": "http://localhost:$port/sse"
    }
  }
}"""

    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(StringSelection(jsonConfig), null)
}

private fun openLogDirectory() {
    try {
        val logsDir = AppPaths.dataDir().resolve("logs").toFile()
        if (!logsDir.exists()) logsDir.mkdirs()
        Desktop.getDesktop().open(logsDir)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private val ServiceType.displayName: String
    get() = when (this) {
        ServiceType.DATABASE -> "Database"
        ServiceType.API_REST -> "API REST"
        ServiceType.MCP_SERVER -> "MCP Server"
    }

private fun calculateUptime(startedAt: LocalDateTime?): String {
    if (startedAt == null) return "N/A"

    val now = LocalDateTime.now()
    val seconds = ChronoUnit.SECONDS.between(startedAt, now)

    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86400 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        else -> "${seconds / 86400}d ${(seconds % 86400) / 3600}h"
    }
}
