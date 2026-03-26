package org.motopartes.mobile.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.motopartes.mobile.api.ApiClient
import org.motopartes.mobile.api.CreateDollarRateRequest
import org.motopartes.mobile.api.DollarRateResponse

@Composable
fun DollarRateScreen(api: ApiClient) {
    var current by remember { mutableStateOf<DollarRateResponse?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newRate by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        isLoading = true
        scope.launch {
            api.getLatestDollarRate().fold(
                onSuccess = { current = it; error = null },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Cotizacion Dolar", style = MaterialTheme.typography.headlineMedium)

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Current rate card
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (current != null) {
                        Text(
                            "$${current!!.rate}",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "ARS por 1 USD",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Fecha: ${current!!.date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "Sin cotizacion",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Update form
            if (showForm) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nueva cotizacion", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = newRate,
                            onValueChange = { newRate = it },
                            label = { Text("Cotizacion (ARS por 1 USD)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showForm = false; newRate = "" }) {
                                Text("Cancelar")
                            }
                            Button(onClick = {
                                val rate = newRate.toBigDecimalOrNull() ?: return@Button
                                if (rate <= java.math.BigDecimal.ZERO) return@Button
                                scope.launch {
                                    val today = java.time.LocalDate.now().toString()
                                    api.setDollarRate(CreateDollarRateRequest(rate.toPlainString(), today)).fold(
                                        onSuccess = { showForm = false; newRate = ""; load() },
                                        onFailure = { error = it.message }
                                    )
                                }
                            }) {
                                Text("Guardar")
                            }
                        }
                    }
                }
            } else {
                Button(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Actualizar cotizacion")
                }
            }
        }
    }
}
