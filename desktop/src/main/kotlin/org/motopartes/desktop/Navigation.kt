package org.motopartes.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(val title: String, val icon: ImageVector) {
    PRODUCTS("Productos", Icons.Default.Inventory),
    CLIENTS("Clientes", Icons.Default.People),
    ORDERS("Pedidos", Icons.Default.Receipt),
    PURCHASES("Compras", Icons.Default.LocalShipping),
    FINANCE("Finanzas", Icons.Default.AccountBalance),
    SUPPLIER("Proveedor", Icons.Default.Store),
    DOLLAR_RATE("Cotización", Icons.Default.CurrencyExchange),
}
