package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.motopartes.desktop.component.*
import org.motopartes.model.DollarRate
import org.motopartes.repository.DollarRateRepository
import org.motopartes.repository.SettingsRepository
import java.math.BigDecimal

@Composable
fun DollarRateScreen(dollarRateRepo: DollarRateRepository, settingsRepo: SettingsRepository) {
    var rates by remember { mutableStateOf(dollarRateRepo.getAll()) }
    var showForm by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(10) }
    val currentRate = rates.firstOrNull()

    fun coefToPercent(coef: BigDecimal): String = coef.subtract(BigDecimal.ONE).multiply(BigDecimal(100)).stripTrailingZeros().toPlainString()
    fun percentToCoef(pct: String): BigDecimal? = pct.toBigDecimalOrNull()?.let { BigDecimal.ONE.add(it.divide(BigDecimal(100))) }

    var currentMarkupArs by remember { mutableStateOf(settingsRepo.getMarkupArs()) }
    var currentMarkupUsd by remember { mutableStateOf(settingsRepo.getMarkupUsd()) }
    var showArsForm by remember { mutableStateOf(false) }
    var showUsdForm by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf(settingsRepo.getOrCreateApiKey()) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Configuracion", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        // Top cards: Dollar rate + Markups
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // Dollar rate card
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cotizacion Dolar", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick = { showForm = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Nueva")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (currentRate != null) {
                        Text("$${currentRate.rate.toPlainString()}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Fecha: ${currentRate.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("Sin cotizacion", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }

            // Markup ARS card
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Margen ARS", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick = { showArsForm = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Nueva")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("${coefToPercent(currentMarkupArs)}%", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Productos en pesos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Markup USD card
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Margen USD", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick = { showUsdForm = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Nueva")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("${coefToPercent(currentMarkupUsd)}%", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Productos en dolares", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // API Key card
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("API Key", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Clave de acceso para la app movil y conexiones remotas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            apiKey,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    FilledTonalIconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(apiKey))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copiar", Modifier.size(18.dp))
                    }
                    FilledTonalIconButton(onClick = { showRegenerateConfirm = true }) {
                        Icon(Icons.Default.Refresh, "Regenerar", Modifier.size(18.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        // Dollar rate history
        Text("Historial de cotizaciones", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Fecha", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Cotizacion", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val pagedRates = rates.paginate(currentPage, pageSize)
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(pagedRates, key = { it.id }) { rate ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(rate.date.toString(), Modifier.weight(1f))
                    Text("$${rate.rate.toPlainString()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        PaginationBar(currentPage, rates.size, pageSize, { currentPage = it }, { pageSize = it; currentPage = 0 }, listState = listState)
    }

    if (showForm) {
        val today = remember {
            val now = java.time.LocalDate.now()
            LocalDate(now.year, now.monthValue, now.dayOfMonth)
        }
        var rateText by remember { mutableStateOf("") }

        FormDialog("Nueva Cotizacion", onDismiss = { showForm = false }, onConfirm = {
            val parsed = rateText.toBigDecimalOrNull() ?: return@FormDialog
            if (parsed <= BigDecimal.ZERO) return@FormDialog
            dollarRateRepo.insert(DollarRate(rate = parsed, date = today))
            rates = dollarRateRepo.getAll(); showForm = false
        }) {
            Text("Fecha: $today", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(rateText, { rateText = it }, label = { Text("Cotizacion (ARS por 1 USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }

    if (showArsForm) {
        var pctText by remember { mutableStateOf("") }
        FormDialog("Nuevo Margen ARS", onDismiss = { showArsForm = false }, onConfirm = {
            val coef = percentToCoef(pctText) ?: return@FormDialog
            if (coef <= BigDecimal.ONE) return@FormDialog
            settingsRepo.setMarkupArs(coef)
            currentMarkupArs = coef
            showArsForm = false
        }) {
            Text("Margen actual: ${coefToPercent(currentMarkupArs)}%", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(pctText, { pctText = it }, label = { Text("Porcentaje de ganancia") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Ej: 30 = 30% de ganancia sobre el costo") })
        }
    }

    if (showUsdForm) {
        var pctText by remember { mutableStateOf("") }
        FormDialog("Nuevo Margen USD", onDismiss = { showUsdForm = false }, onConfirm = {
            val coef = percentToCoef(pctText) ?: return@FormDialog
            if (coef <= BigDecimal.ONE) return@FormDialog
            settingsRepo.setMarkupUsd(coef)
            currentMarkupUsd = coef
            showUsdForm = false
        }) {
            Text("Margen actual: ${coefToPercent(currentMarkupUsd)}%", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(pctText, { pctText = it }, label = { Text("Porcentaje de ganancia") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Ej: 40 = 40% de ganancia sobre el costo") })
        }
    }

    if (showRegenerateConfirm) {
        AlertDialog(
            onDismissRequest = { showRegenerateConfirm = false },
            title = { Text("Regenerar API Key") },
            text = { Text("Se generara una nueva clave. La app movil y cualquier conexion remota dejaran de funcionar hasta que configures la nueva clave.\n\n¿Continuar?") },
            confirmButton = {
                Button(
                    onClick = {
                        val newKey = java.util.UUID.randomUUID().toString()
                        settingsRepo.setApiKey(newKey)
                        apiKey = newKey
                        showRegenerateConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Regenerar") }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}
