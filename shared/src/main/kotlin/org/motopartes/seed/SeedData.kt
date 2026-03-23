package org.motopartes.seed

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.motopartes.db.DatabaseFactory
import org.motopartes.model.*
import org.motopartes.repository.*
import org.motopartes.service.FinanceService
import org.motopartes.service.OrderService
import java.math.BigDecimal
import kotlin.random.Random

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

    println("Seeding database...")

    // Supplier
    val supplier = supplierRepo.insert(Supplier(name = "Distribuidora Moto Sur SRL", phone = "011-4555-8800"))
    println("Proveedor: ${supplier.name}")

    // Dollar rates (last 30 days)
    val baseRate = 1180.0
    for (i in 30 downTo 0) {
        val date = LocalDate(2026, 3, 20).let {
            val dayOffset = i
            // Simple date arithmetic
            val day = 20 - dayOffset
            if (day > 0) LocalDate(2026, 3, day)
            else LocalDate(2026, 2, 28 + day)
        }
        val rate = baseRate + Random.nextDouble(-30.0, 30.0) + (30 - i) * 2.0
        dollarRateRepo.insert(DollarRate(rate = BigDecimal.valueOf(rate).setScale(2, java.math.RoundingMode.HALF_UP), date = date))
    }
    println("Cotizaciones: 31 dias")

    // Products (80 productos de motopartes)
    data class ProductSeed(val code: String, val name: String, val price: Double, val currency: Currency, val stock: Int)
    val productSeeds = listOf(
        ProductSeed("RUL-001", "Ruleman 6200", 2.50, Currency.USD, 120),
        ProductSeed("RUL-002", "Ruleman 6201", 2.80, Currency.USD, 85),
        ProductSeed("RUL-003", "Ruleman 6202", 3.20, Currency.USD, 60),
        ProductSeed("RUL-004", "Ruleman 6203", 3.50, Currency.USD, 45),
        ProductSeed("RUL-005", "Ruleman 6204 ZZ", 4.00, Currency.USD, 30),
        ProductSeed("RUL-006", "Ruleman 6205 2RS", 4.50, Currency.USD, 25),
        ProductSeed("CAD-001", "Cadena 428H 120L", 18500.00, Currency.ARS, 15),
        ProductSeed("CAD-002", "Cadena 428H 130L", 19800.00, Currency.ARS, 12),
        ProductSeed("CAD-003", "Cadena 520H 120L", 28500.00, Currency.ARS, 8),
        ProductSeed("CAD-004", "Cadena 520 O-Ring 120L", 42000.00, Currency.ARS, 5),
        ProductSeed("KIT-001", "Kit Arrastre Honda CG 150", 35000.00, Currency.ARS, 10),
        ProductSeed("KIT-002", "Kit Arrastre Yamaha YBR 125", 32000.00, Currency.ARS, 8),
        ProductSeed("KIT-003", "Kit Arrastre Honda XR 250", 48000.00, Currency.ARS, 6),
        ProductSeed("KIT-004", "Kit Arrastre Bajaj Rouser 200", 45000.00, Currency.ARS, 7),
        ProductSeed("PAS-001", "Pastillas de freno delanteras Honda CG", 6500.00, Currency.ARS, 40),
        ProductSeed("PAS-002", "Pastillas de freno traseras Honda CG", 5800.00, Currency.ARS, 35),
        ProductSeed("PAS-003", "Pastillas de freno delanteras Yamaha FZ", 7200.00, Currency.ARS, 25),
        ProductSeed("PAS-004", "Pastillas de freno delanteras Bajaj Rouser", 8500.00, Currency.ARS, 20),
        ProductSeed("PAS-005", "Pastillas de freno traseras universales", 4500.00, Currency.ARS, 50),
        ProductSeed("FIL-001", "Filtro de aceite Honda CG/XR", 3200.00, Currency.ARS, 60),
        ProductSeed("FIL-002", "Filtro de aceite Yamaha YBR/FZ", 3500.00, Currency.ARS, 45),
        ProductSeed("FIL-003", "Filtro de aire Honda CG 150", 4800.00, Currency.ARS, 30),
        ProductSeed("FIL-004", "Filtro de aire Yamaha YBR 125", 5200.00, Currency.ARS, 25),
        ProductSeed("FIL-005", "Filtro de aire Honda Wave 110", 3800.00, Currency.ARS, 35),
        ProductSeed("FIL-006", "Filtro de nafta universal", 1200.00, Currency.ARS, 80),
        ProductSeed("ACE-001", "Aceite Motul 3000 20W50 1L", 8500.00, Currency.ARS, 50),
        ProductSeed("ACE-002", "Aceite Motul 5100 15W50 1L", 14500.00, Currency.ARS, 30),
        ProductSeed("ACE-003", "Aceite Castrol Power1 10W40 1L", 9800.00, Currency.ARS, 40),
        ProductSeed("ACE-004", "Aceite YPF Extra 20W50 1L", 5200.00, Currency.ARS, 70),
        ProductSeed("BUJ-001", "Bujia NGK CR7HSA", 3500.00, Currency.ARS, 100),
        ProductSeed("BUJ-002", "Bujia NGK DPR8EA-9", 4200.00, Currency.ARS, 60),
        ProductSeed("BUJ-003", "Bujia Champion RN2C", 3800.00, Currency.ARS, 45),
        ProductSeed("COR-001", "Correa de transmision Honda PCX 150", 12000.00, Currency.ARS, 10),
        ProductSeed("COR-002", "Correa de transmision Yamaha NMAX", 14500.00, Currency.ARS, 8),
        ProductSeed("COR-003", "Correa de transmision Kymco Agility", 11000.00, Currency.ARS, 12),
        ProductSeed("CAB-001", "Cable de acelerador Honda CG 150", 4500.00, Currency.ARS, 20),
        ProductSeed("CAB-002", "Cable de embrague Honda CG 150", 4800.00, Currency.ARS, 18),
        ProductSeed("CAB-003", "Cable de freno trasero Honda CG", 3200.00, Currency.ARS, 22),
        ProductSeed("CAB-004", "Cable de velocimetro Honda CG", 3800.00, Currency.ARS, 15),
        ProductSeed("CAB-005", "Cable de acelerador Yamaha YBR", 4200.00, Currency.ARS, 16),
        ProductSeed("RET-001", "Reten de bancada 6205", 1800.00, Currency.ARS, 40),
        ProductSeed("RET-002", "Reten de bancada 6204", 1600.00, Currency.ARS, 35),
        ProductSeed("RET-003", "Reten de horquilla 33x46", 2500.00, Currency.ARS, 30),
        ProductSeed("RET-004", "Reten de valvula Honda CG", 800.00, Currency.ARS, 60),
        ProductSeed("JUN-001", "Junta de cilindro Honda CG 150", 3500.00, Currency.ARS, 15),
        ProductSeed("JUN-002", "Juego de juntas completo Honda CG", 8500.00, Currency.ARS, 10),
        ProductSeed("JUN-003", "Junta de cilindro Yamaha YBR 125", 3200.00, Currency.ARS, 12),
        ProductSeed("JUN-004", "Junta tapa de valvulas Honda CG", 1500.00, Currency.ARS, 25),
        ProductSeed("PIR-001", "Piston Honda CG 150 STD", 12000.00, Currency.ARS, 8),
        ProductSeed("PIR-002", "Piston Honda CG 150 0.50", 12500.00, Currency.ARS, 6),
        ProductSeed("PIR-003", "Aros de piston Honda CG 150 STD", 5500.00, Currency.ARS, 12),
        ProductSeed("EMB-001", "Discos de embrague Honda CG (juego)", 8000.00, Currency.ARS, 15),
        ProductSeed("EMB-002", "Discos de embrague Yamaha YBR (juego)", 8500.00, Currency.ARS, 12),
        ProductSeed("EMB-003", "Resortes de embrague Honda CG", 2500.00, Currency.ARS, 20),
        ProductSeed("LUZ-001", "Lampara H4 12V 35W halogena", 2800.00, Currency.ARS, 50),
        ProductSeed("LUZ-002", "Lampara LED H4 6000K", 8500.00, Currency.ARS, 30),
        ProductSeed("LUZ-003", "Lampara de giro BA15S 12V", 800.00, Currency.ARS, 80),
        ProductSeed("LUZ-004", "Lampara de freno BAY15D 12V", 900.00, Currency.ARS, 60),
        ProductSeed("ELE-001", "CDI Honda CG 150", 15000.00, Currency.ARS, 8),
        ProductSeed("ELE-002", "Regulador de voltaje Honda CG", 12000.00, Currency.ARS, 10),
        ProductSeed("ELE-003", "Bobina de encendido Honda CG", 9500.00, Currency.ARS, 12),
        ProductSeed("ELE-004", "Relay de arranque universal", 3500.00, Currency.ARS, 25),
        ProductSeed("SUS-001", "Amortiguador trasero Honda CG 150", 22000.00, Currency.ARS, 6),
        ProductSeed("SUS-002", "Amortiguador trasero Yamaha YBR", 24000.00, Currency.ARS, 5),
        ProductSeed("SUS-003", "Resortes de horquilla Honda CG", 6500.00, Currency.ARS, 10),
        ProductSeed("TOR-001", "Tornillo de carter M6x20 (x10)", 1500.00, Currency.ARS, 50),
        ProductSeed("TOR-002", "Tornillo de escape M8x30 (x4)", 1200.00, Currency.ARS, 40),
        ProductSeed("TOR-003", "Tuerca de eje trasero M14", 800.00, Currency.ARS, 60),
        ProductSeed("TOR-004", "Kit tornilleria carenado (x20)", 3500.00, Currency.ARS, 20),
        ProductSeed("NEU-001", "Camara 275/300-18", 5500.00, Currency.ARS, 20),
        ProductSeed("NEU-002", "Camara 275/300-17", 5200.00, Currency.ARS, 25),
        ProductSeed("NEU-003", "Camara 350/400-18", 6500.00, Currency.ARS, 15),
        ProductSeed("MAN-001", "Manija de freno derecha Honda CG", 3200.00, Currency.ARS, 15),
        ProductSeed("MAN-002", "Manija de embrague izquierda Honda CG", 3200.00, Currency.ARS, 15),
        ProductSeed("MAN-003", "Puño de acelerador Honda CG", 2800.00, Currency.ARS, 20),
        ProductSeed("ESP-001", "Espejo retrovisor derecho universal", 3500.00, Currency.ARS, 25),
        ProductSeed("ESP-002", "Espejo retrovisor izquierdo universal", 3500.00, Currency.ARS, 25),
        ProductSeed("ESP-003", "Par espejos Honda CG originales", 8500.00, Currency.ARS, 10),
        ProductSeed("ACC-001", "Cubre cadena Honda CG 150", 4500.00, Currency.ARS, 10),
        ProductSeed("ACC-002", "Pedal de freno trasero Honda CG", 5500.00, Currency.ARS, 8),
    )

    val products = productSeeds.map { seed ->
        val purchasePrice = BigDecimal.valueOf(seed.price)
        productRepo.insert(Product(code = seed.code, name = seed.name, purchasePrice = purchasePrice, purchaseCurrency = seed.currency, stock = seed.stock))
    }
    println("Productos: ${products.size}")

    // Clients (30 clientes)
    val clientNames = listOf(
        "Juan Pérez" to ("011-5555-0001" to "Av. Rivadavia 4500, CABA"),
        "María García" to ("011-5555-0002" to "Calle 13 N° 450, La Plata"),
        "Carlos López" to ("011-5555-0003" to "San Martín 890, Quilmes"),
        "Ana Martínez" to ("011-5555-0004" to "Mitre 230, Avellaneda"),
        "Roberto Sánchez" to ("011-5555-0005" to "Belgrano 1100, Lanús"),
        "Laura Fernández" to ("011-5555-0006" to "Moreno 670, Lomas de Zamora"),
        "Diego Romero" to ("011-5555-0007" to "9 de Julio 340, Banfield"),
        "Lucía Torres" to ("011-5555-0008" to "Alsina 890, Temperley"),
        "Martín Díaz" to ("011-5555-0009" to "Brown 560, Adrogué"),
        "Valentina Ruiz" to ("011-5555-0010" to "Colón 1200, Florencio Varela"),
        "Pablo Álvarez" to ("011-5555-0011" to "Italia 780, Berazategui"),
        "Sofía Castro" to ("011-5555-0012" to "Sarmiento 1500, San Justo"),
        "Matías Morales" to ("011-5555-0013" to "Pueyrredón 890, Ramos Mejía"),
        "Camila Ortiz" to ("011-5555-0014" to "Av. Directorio 3400, CABA"),
        "Fernando Gutiérrez" to ("011-5555-0015" to "Constitución 670, Morón"),
        "Florencia Herrera" to ("011-5555-0016" to "Maipú 450, Merlo"),
        "Tomás Acosta" to ("011-5555-0017" to "Lavalle 1100, Moreno"),
        "Julieta Medina" to ("011-5555-0018" to "Libertad 890, Ituzaingó"),
        "Nicolás Suárez" to ("011-5555-0019" to "Paso 340, Castelar"),
        "Agustina Flores" to ("011-5555-0020" to "Saavedra 670, Haedo"),
        "Alejandro Vega" to ("011-5555-0021" to "French 1200, San Miguel"),
        "Isabella Navarro" to ("011-5555-0022" to "Pellegrini 450, José C. Paz"),
        "Sebastián Cruz" to ("011-5555-0023" to "Urquiza 780, Pilar"),
        "Mía Reyes" to ("011-5555-0024" to "Güemes 560, Escobar"),
        "Joaquín Molina" to ("011-5555-0025" to "Corrientes 340, Tigre"),
        "Emilia Cabrera" to ("011-5555-0026" to "Ayacucho 890, San Fernando"),
        "Lautaro Figueroa" to ("011-5555-0027" to "Las Heras 1200, Zárate"),
        "Antonella Campos" to ("011-5555-0028" to "Chacabuco 670, Campana"),
        "Santiago Ríos" to ("011-5555-0029" to "Balcarce 450, San Nicolás"),
        "Bianca Herrera" to ("011-5555-0030" to "Dorrego 890, Pergamino"),
    )
    val clients = clientNames.map { (name, info) ->
        clientRepo.insert(Client(name = name, phone = info.first, address = info.second))
    }
    println("Clientes: ${clients.size}")

    // Orders (40 pedidos en distintos estados)
    val statuses = listOf(
        OrderStatus.CREATED, OrderStatus.CREATED, OrderStatus.CREATED,
        OrderStatus.CONFIRMED, OrderStatus.CONFIRMED, OrderStatus.CONFIRMED,
        OrderStatus.ASSEMBLED, OrderStatus.ASSEMBLED,
        OrderStatus.INVOICED, OrderStatus.INVOICED, OrderStatus.INVOICED, OrderStatus.INVOICED,
        OrderStatus.INVOICED, OrderStatus.INVOICED, OrderStatus.INVOICED,
        OrderStatus.CANCELLED,
    )

    for (i in 1..40) {
        val client = clients[Random.nextInt(clients.size)]
        val numItems = Random.nextInt(1, 6)
        val selectedProducts = products.shuffled().take(numItems)
        val latestRate = dollarRateRepo.getLatest()?.rate ?: BigDecimal("1200.00")
        val markup = BigDecimal("1.30")
        val items = selectedProducts.map { Triple(it.id, Random.nextInt(1, 8), it.suggestedSalePrice(latestRate, markup, markup)) }
        val day = Random.nextInt(1, 21)
        val hour = Random.nextInt(8, 20)
        val now = LocalDateTime(2026, 3, day, hour, Random.nextInt(0, 60))

        val targetStatus = statuses[i % statuses.size]
        val order = orderService.createOrder(client.id, items, now)

        if (targetStatus.ordinal >= OrderStatus.CONFIRMED.ordinal) {
            orderService.confirmOrder(order.id)
        }
        if (targetStatus.ordinal >= OrderStatus.ASSEMBLED.ordinal) {
            orderService.assembleOrder(order.id, emptyMap()) // use original quantities
        }
        if (targetStatus == OrderStatus.INVOICED) {
            orderService.invoiceOrder(order.id, now)
        }
        if (targetStatus == OrderStatus.CANCELLED) {
            orderService.cancelOrder(order.id)
        }
    }
    println("Pedidos: 40")

    // Some client payments
    for (i in 1..10) {
        val client = clients[Random.nextInt(clients.size)]
        if (client.balance > java.math.BigDecimal.ZERO) {
            val amount = BigDecimal.valueOf(Random.nextDouble(1000.0, 20000.0)).setScale(2, java.math.RoundingMode.HALF_UP)
            financeService.recordClientPayment(client.id, amount, LocalDateTime(2026, 3, Random.nextInt(1, 21), 10, 0))
        }
    }

    // Supplier payment
    financeService.recordSupplierPayment(BigDecimal("150000.00"), LocalDateTime(2026, 3, 15, 10, 0), "Pago parcial marzo")

    println("Seed completado!")
}
