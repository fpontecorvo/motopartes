package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.motopartes.desktop.component.*
import org.motopartes.model.DollarRate
import org.motopartes.repository.DollarRateRepository
import java.math.BigDecimal

@Composable
fun DollarRateScreen(dollarRateRepo: DollarRateRepository) {
    var rates by remember { mutableStateOf(dollarRateRepo.getAll()) }
    var showForm by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(10) }
    val currentRate = rates.firstOrNull()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row {
            Text("Cotizacion Dolar", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { showForm = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nueva Cotizacion")
            }
        }
        Spacer(Modifier.height(20.dp))

        ElevatedCard(Modifier.fillMaxWidth(0.4f)) {
            Column(Modifier.padding(24.dp)) {
                Text("Cotizacion actual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (currentRate != null) {
                    Text("$${currentRate.rate.toPlainString()}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Fecha: ${currentRate.date}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("Sin cotizacion", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        Spacer(Modifier.height(24.dp))

        Text("Historial", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Fecha", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Cotizacion", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val pagedRates = rates.paginate(currentPage, pageSize)
        LazyColumn(Modifier.weight(1f)) {
            items(pagedRates, key = { it.id }) { rate ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(rate.date.toString(), Modifier.weight(1f))
                    Text("$${rate.rate.toPlainString()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        PaginationBar(currentPage, rates.size, pageSize, { currentPage = it }, { pageSize = it; currentPage = 0 })
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
}
