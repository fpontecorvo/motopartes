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
- [x] MCP server (módulo `mcp-server/` con Kotlin SDK, transporte stdio + SSE)

### Stage 6 — Acceso remoto y app móvil

Objetivo: acceder a la API desde fuera de la red local (Cloudflare Tunnel gratuito)
y crear una app Android liviana que consuma la misma API/DB.

```
[Celular] → internet → Cloudflare Tunnel (HTTPS) → cloudflared en PC → localhost:8080 → SQLite
[Desktop] → localhost:8080 (directo, sin cambios)
```

#### 6.1 — Autenticación de la API
- [ ] Agregar setting `api_key` en `AppSettings` (SettingsRepository)
- [ ] Auto-generar UUID como API key al primer inicio si no existe
- [ ] Middleware Ktor que valide header `X-API-Key` en cada request
- [ ] Endpoint `GET /health` público (sin auth) para monitoreo
- [ ] Bloquear `/backup/restore` desde fuera (solo localhost)
- [ ] Card "API Key" en pantalla Configuración del desktop (mostrar, copiar, regenerar)
- [ ] Restringir CORS: reemplazar `anyHost()` por origins configurables
- [ ] Tests de auth (request sin key → 401, key inválida → 401, key válida → 200)

#### 6.2 — Cloudflare Tunnel
- [ ] Documentar instalación de `cloudflared` (Windows + macOS)
- [ ] Script/instrucciones para crear túnel con URL random (gratis, sin dominio)
- [ ] Instrucciones opcionales para túnel con dominio propio
- [ ] Mostrar URL del túnel en pantalla Servicios del desktop (si se detecta cloudflared)

#### 6.3 — App móvil: setup y base
- [ ] Módulo `mobile/` Android (Kotlin + Jetpack Compose + Material 3)
- [ ] Tema: dark con amber (#FFB74D), mismo look que desktop
- [ ] Pantalla Config: URL del servidor + API key + botón "Conectar"
- [ ] Cliente HTTP (Ktor Client) con header `X-API-Key` automático
- [ ] Reutilizar DTOs de `api/dto/` (copiar o módulo compartido)
- [ ] Navegación: BottomNavigation con 5 pantallas
- [ ] Build APK en GitHub Actions (job `build-android` en release.yml)
- [ ] UpdateService: check de version.json + banner de descarga del APK

#### 6.4 — Pantallas móviles
- [ ] Productos: búsqueda + lista + detalle (precio, stock)
- [ ] Ventas: crear pedido rápido, ver pedidos pendientes
- [ ] Clientes: lista, ver deuda, registrar cobro
- [ ] Cotización dólar: ver actual, actualizar
- [ ] Config: URL servidor, API key, estado de conexión

#### 6.5 — Cache offline (opcional, después)
- [ ] Room DB local para cachear últimos datos consultados
- [ ] Banner "Offline — datos de hace X minutos" si API no responde
- [ ] Cola de operaciones de escritura para sincronizar al reconectar

## Decisiones Técnicas
- SQLite por simplicidad y portabilidad (archivo único, zero-config)
- Compose Desktop para UI nativa en JVM
- Módulo `shared/` reutilizado por `desktop/` y `api/`
- API REST con Ktor para integración con MCP y otros clientes
- Venta siempre en ARS; cotización dólar almacenada por fecha para trazabilidad
- Precios: compra (USD/ARS) + venta (siempre ARS, default costo +30%)
- Acceso remoto via Cloudflare Tunnel (gratis, sin abrir puertos, HTTPS automático)
- App móvil: Android nativo (Jetpack Compose), cliente REST puro
- Auth: API Key en header `X-API-Key`, almacenada en AppSettings
