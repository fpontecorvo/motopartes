package org.motopartes.desktop.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val PAGE_SIZE_OPTIONS = listOf(10, 25, 50)

fun <T> List<T>.paginate(page: Int, pageSize: Int): List<T> {
    val start = (page * pageSize).coerceAtMost(size)
    val end = (start + pageSize).coerceAtMost(size)
    return subList(start, end)
}

fun totalPages(totalItems: Int, pageSize: Int): Int =
    if (totalItems == 0) 1 else (totalItems + pageSize - 1) / pageSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaginationBar(
    currentPage: Int,
    totalItems: Int,
    pageSize: Int,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState? = null
) {
    // Scroll to top when page changes
    LaunchedEffect(currentPage) {
        listState?.animateScrollToItem(0)
    }
    val pages = totalPages(totalItems, pageSize)
    val from = if (totalItems == 0) 0 else currentPage * pageSize + 1
    val to = ((currentPage + 1) * pageSize).coerceAtMost(totalItems)

    Row(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Info
        Text(
            "Mostrando $from–$to de $totalItems",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Navigation
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { onPageChange(0) }, enabled = currentPage > 0) {
                Icon(Icons.Default.KeyboardDoubleArrowLeft, "Primera", Modifier.size(20.dp))
            }
            IconButton(onClick = { onPageChange(currentPage - 1) }, enabled = currentPage > 0) {
                Icon(Icons.AutoMirrored.Default.KeyboardArrowLeft, "Anterior", Modifier.size(20.dp))
            }
            Text(
                "${currentPage + 1} / $pages",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = { onPageChange(currentPage + 1) }, enabled = currentPage < pages - 1) {
                Icon(Icons.AutoMirrored.Default.KeyboardArrowRight, "Siguiente", Modifier.size(20.dp))
            }
            IconButton(onClick = { onPageChange(pages - 1) }, enabled = currentPage < pages - 1) {
                Icon(Icons.Default.KeyboardDoubleArrowRight, "Ultima", Modifier.size(20.dp))
            }
        }

        // Page size selector
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Filas:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PAGE_SIZE_OPTIONS.forEach { size ->
                FilterChip(
                    selected = pageSize == size,
                    onClick = { onPageSizeChange(size); onPageChange(0) },
                    label = { Text("$size") }
                )
            }
            val isCustom = pageSize !in PAGE_SIZE_OPTIONS
            var showCustomDialog by remember { mutableStateOf(false) }
            FilterChip(
                selected = isCustom,
                onClick = { showCustomDialog = true },
                label = { Text(if (isCustom) "$pageSize" else "Otro") }
            )
            if (showCustomDialog) {
                var customText by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showCustomDialog = false },
                    title = { Text("Filas por pagina") },
                    text = {
                        OutlinedTextField(
                            customText, { customText = it },
                            label = { Text("Cantidad") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val v = customText.toIntOrNull()
                            if (v != null && v > 0) { onPageSizeChange(v); onPageChange(0) }
                            showCustomDialog = false
                        }) { Text("Aplicar") }
                    },
                    dismissButton = { TextButton(onClick = { showCustomDialog = false }) { Text("Cancelar") } }
                )
            }
        }
    }
}
