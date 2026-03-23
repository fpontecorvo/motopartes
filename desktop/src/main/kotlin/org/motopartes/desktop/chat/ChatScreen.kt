package org.motopartes.desktop.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.motopartes.desktop.component.Dropdown

data class ChatMessage(val role: String, val content: String) // "user" or "assistant"

@Composable
fun ChatScreen(chatService: ChatService) {
    // Messages live in chatService so they persist across screen switches
    var messagesVersion by remember { mutableStateOf(0) }
    val messages = chatService.messages
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(!chatService.isConfigured) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank() || isLoading) return
        inputText = ""
        messages.add(ChatMessage("user", text))
        messagesVersion++
        isLoading = true
        scope.launch {
            val response = chatService.chat(text)
            messages.add(ChatMessage("assistant", response))
            messagesVersion++
            isLoading = false
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Chat Asistente", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (chatService.isConfigured) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                    Text("Conectado", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(8.dp))
            }
            if (messages.isNotEmpty()) {
                FilledTonalButton(onClick = { chatService.clearChat(); messagesVersion++ }) {
                    Text("Nuevo Chat")
                }
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, "Configuracion", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!chatService.isConfigured) {
            // Not configured message
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Configure la API key para comenzar", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { showSettings = true }) { Text("Configurar") }
                }
            }
        } else {
            // Messages
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (messages.isEmpty()) {
                    item {
                        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Escribi algo para empezar. Por ejemplo:\n\"Listame los productos con stock bajo\"\n\"Cuanto debe Juan Perez?\"\n\"Crea un venta para el cliente 1 con 5 del producto 7\"",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(messages) { msg ->
                    MessageBubble(msg)
                }
                if (isLoading) {
                    item {
                        Row(Modifier.padding(start = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Pensando...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Input
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter && event.type == KeyEventType.KeyDown && !event.isShiftPressed) {
                            sendMessage(); true
                        } else false
                    },
                    placeholder = { Text("Escribi un mensaje...") },
                    singleLine = true,
                    enabled = !isLoading,
                    shape = MaterialTheme.shapes.medium
                )
                FilledTonalButton(
                    onClick = { sendMessage() },
                    enabled = inputText.isNotBlank() && !isLoading
                ) {
                    Icon(Icons.AutoMirrored.Default.Send, "Enviar", Modifier.size(18.dp))
                }
            }
        }
    }

    // Settings dialog
    if (showSettings) {
        SettingsDialog(chatService, onDismiss = { showSettings = false })
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 600.dp),
            shape = MaterialTheme.shapes.medium,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = if (isUser) 0.dp else 1.dp
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SettingsDialog(chatService: ChatService, onDismiss: () -> Unit) {
    var apiKey by remember { mutableStateOf(ChatSettings.apiKey) }
    var provider by remember { mutableStateOf(ChatSettings.provider) }
    var model by remember { mutableStateOf(ChatSettings.model) }
    val models = ChatService.MODELS[provider] ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuracion del Chat") },
        text = {
            Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Provider
                Text("Proveedor", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChatService.PROVIDERS.forEach { p ->
                        FilterChip(
                            selected = provider == p,
                            onClick = { provider = p; model = ChatService.MODELS[p]?.first() ?: "" },
                            label = { Text(p.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                // Model
                Text("Modelo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { m ->
                        FilterChip(
                            selected = model == m,
                            onClick = { model = m },
                            label = { Text(m.substringBefore("-2025")) }
                        )
                    }
                }

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                ChatSettings.apiKey = apiKey
                ChatSettings.provider = provider
                ChatSettings.model = model
                chatService.configure(apiKey, provider, model)
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
