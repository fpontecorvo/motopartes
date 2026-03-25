package org.motopartes.mobile.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.motopartes.mobile.api.ApiClient
import org.motopartes.mobile.api.getApiKey
import org.motopartes.mobile.api.getServerUrl
import org.motopartes.mobile.api.saveConnection

@Composable
fun ConfigScreen(
    isConnected: Boolean,
    onConnect: (ApiClient) -> Unit,
    onDisconnect: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        serverUrl = context.getServerUrl()
        apiKey = context.getApiKey()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Configuracion", style = MaterialTheme.typography.headlineMedium)

        // Connection status
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (isConnected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (isConnected) Icons.Default.CheckCircle else Icons.Default.Error,
                    null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
                Column {
                    Text(
                        if (isConnected) "Conectado" else "Sin conexion",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (isConnected && serverUrl.isNotBlank()) {
                        Text(serverUrl, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Server URL
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            label = { Text("URL del servidor") },
            placeholder = { Text("https://ejemplo.trycloudflare.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        // API Key
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            placeholder = { Text("Pegar la clave de la app desktop") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isConnected
        )

        // Error
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // Buttons
        if (!isConnected) {
            Button(
                onClick = {
                    if (serverUrl.isBlank() || apiKey.isBlank()) {
                        error = "Completa ambos campos"
                        return@Button
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        val url = serverUrl.trimEnd('/')
                        val client = ApiClient(url, apiKey)
                        val result = client.health()
                        if (result.isSuccess) {
                            context.saveConnection(url, apiKey)
                            onConnect(client)
                        } else {
                            error = "No se pudo conectar: ${result.exceptionOrNull()?.message ?: "Error desconocido"}"
                            client.close()
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Conectar")
            }
        } else {
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Desconectar")
            }
        }

        Spacer(Modifier.weight(1f))

        // Footer
        Text(
            "Motopartes Mobile",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
fun NotConnectedScreen(onGoToConfig: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.WifiOff, null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(16.dp))
        Text("Sin conexion al servidor", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Configura la URL y API Key para conectar",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onGoToConfig) {
            Text("Ir a Configuracion")
        }
    }
}
