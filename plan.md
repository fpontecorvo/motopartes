# Plan del Sistema — Motopartes

## Descripción General
App de escritorio para gestión de venta minorista de motopartes.
Un solo proveedor mayorista. Venta exclusivamente en pesos argentinos.
Productos pueden estar valuados en USD o ARS; se convierten a ARS con cotización dólar configurable.

## Arquitectura
- **Tipo:** App de escritorio standalone (sin servidor, sin networking)
- **UI:** Compose Desktop (JVM)
- **DB:** SQLite embebida (archivo en disco)
- **Lenguaje:** Kotlin
- **Build:** Gradle Kotlin DSL, multi-módulo
- **Plataforma target:** Windows (desarrollo en Mac)

### Estructura de módulos
```
shared/    → Modelos de dominio, lógica de negocio, acceso a datos (DB)
desktop/   → UI Compose Desktop, navegación, punto de entrada
```

### Persistencia
- Windows: `%APPDATA%/motopartes/data.db`
- Mac (dev): `~/Library/Application Support/motopartes/data.db`
- Detección de OS en runtime via `System.getProperty("os.name")`
- Migraciones de schema versionadas

## Modelo de Dominio

### Entidades
- **Producto** — código (del proveedor), nombre, descripción, precio, moneda (USD/ARS)
- **Stock** — producto + cantidad disponible
- **Cliente** — datos de contacto, saldo deudor
- **Proveedor** — uno solo, saldo a pagar
- **Pedido/Remito** — cliente, items con precio en ARS, estado (creado → confirmado → armado → entregado)
- **Movimiento financiero** — cobros a clientes, pagos al proveedor
- **Cotización dólar** — valor actual + historial

### Flujo principal
1. Compra al proveedor → entrada de stock → genera deuda con proveedor
2. Cliente hace pedido → se genera remito con items y precios en ARS (conversión automática si el producto está en USD)
3. Confirmación de disponibilidad → armado → entrega
4. Cliente paga (total o parcial) → se reduce su saldo deudor
5. Pago al proveedor → se reduce deuda

## Stages

### Stage 1 — Fundación ✅
- [x] Reestructurar proyecto: `shared/` + `desktop/` (reemplazar `app/` y `utils/`)
- [x] Configurar Compose Desktop
- [x] Configurar SQLite embebida (Exposed)
- [x] Schema DB: tablas de todas las entidades
- [x] Modelo de dominio en `shared/`
- [x] Navegación base en `desktop/`
- [x] Resolución de path de DB por OS

### Stage 2 — Core de negocio ✅
- [x] CRUD productos (repositorio + UI)
- [x] Gestión de stock (entrada/salida manual con validación)
- [x] CRUD clientes (repositorio + UI)
- [x] CRUD proveedor (repositorio + UI)
- [x] Cotización dólar (valor actual, historial, UI)
- [x] Conversión automática USD → ARS en precios
- [x] Búsqueda de productos por código o nombre

### Stage 3 — Pedidos ✅
- [x] Crear remito (seleccionar cliente, agregar items, calcular totales en ARS)
- [x] Flujo de estados: creado → confirmado → armado → entregado
- [x] Descuento automático de stock al confirmar pedido
- [x] Entrada automática de stock al registrar compra al proveedor (PurchaseService + UI)
- [x] Validación de stock disponible (falla confirmación si stock insuficiente)

### Stage 4 — Finanzas ✅
- [x] Cuenta corriente clientes (saldo deudor — se incrementa al entregar pedido)
- [x] Cuenta corriente proveedor (saldo a pagar — se incrementa al registrar compra)
- [x] Registro de cobros a clientes (FinanceService + UI)
- [x] Registro de pagos al proveedor (FinanceService + UI)
- [x] Historial de movimientos financieros (FinanceScreen con tabla)

### Stage 5 — Preparar para el futuro (parcial)
- [x] Asegurar que `shared/` sea independiente de desktop para plug & play mobile
- [x] API REST (módulo `api/` con Ktor, todos los endpoints expuestos)
- [x] Docker (Dockerfile multi-stage + docker-compose para la API)
- [x] Instaladores nativos (DMG, MSI, DEB via Compose Desktop packaging)
- [x] Precio de compra vs precio de venta (con markup 30% configurable)
- [x] Variable de entorno `MOTOPARTES_DATA_DIR` para path de DB configurable
- [ ] Preparar schema DB para fotos de productos (campo path/blob)
- [x] Backup/restore de la DB (desktop: botones en banner + API: GET/POST endpoints)
- [x] Check de version + banner de descarga (lee version.json de GitHub, abre releases)
- [ ] MCP server para conectar chat con la API

## Decisiones Técnicas
- SQLite por simplicidad y portabilidad (archivo único, zero-config)
- Compose Desktop para UI nativa en JVM
- Módulo `shared/` reutilizado por `desktop/` y `api/`
- API REST con Ktor para integración con MCP y otros clientes
- Venta siempre en ARS; cotización dólar almacenada por fecha para trazabilidad
- Precios: compra (USD/ARS) + venta (siempre ARS, default costo +30%)
