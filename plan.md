# Plan del Sistema — Motopartes

## Descripción General
App de escritorio para gestión de venta minorista de motopartes.
Un solo proveedor mayorista. Venta exclusivamente en pesos argentinos.
Productos pueden estar valuados en USD o ARS; se convierten a ARS con cotización dólar configurable.

## Arquitectura
- **Tipo:** App de escritorio + API REST embebida + app móvil Android
- **UI:** Compose Desktop (JVM) + Jetpack Compose (Android)
- **DB:** SQLite embebida (archivo en disco)
- **Lenguaje:** Kotlin
- **Build:** Gradle Kotlin DSL, multi-módulo
- **Plataforma target:** Windows/macOS/Linux (desktop), Android (mobile)

### Estructura de módulos
```
shared/      → Modelos de dominio, lógica de negocio, acceso a datos (DB)
desktop/     → UI Compose Desktop, navegación, punto de entrada
api/         → API REST (Ktor + Netty, puerto 8080)
mobile/      → App Android (Jetpack Compose), cliente REST
mcp-server/  → MCP server (stdio + SSE)
buildSrc/    → Convention plugins (kotlin-jvm, JDK 25)
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

### Stage 6 — Acceso remoto y app móvil (parcial)

Objetivo: acceder a la API desde fuera de la red local (Cloudflare Tunnel gratuito)
y crear una app Android liviana que consuma la misma API/DB.

```
[Celular] → internet → Cloudflare Tunnel (HTTPS) → cloudflared en PC → localhost:8080 → SQLite
[Desktop] → localhost:8080 (directo, sin cambios)
```

#### 6.1 — Autenticación de la API ✅
- [x] Agregar setting `api_key` en `AppSettings` (SettingsRepository)
- [x] Auto-generar UUID como API key al primer inicio si no existe
- [x] Middleware Ktor que valide header `X-API-Key` en cada request
- [x] Endpoint `GET /health` público (sin auth) para monitoreo
- [x] Bypass localhost (desktop no necesita key)
- [x] Card "API Key" en pantalla Configuración del desktop (mostrar, copiar, regenerar)
- [x] CORS: permitir header `X-API-Key`
- [x] Tests de auth (request sin key → 401, key inválida → 401, key válida → 200, health → 200)
- [ ] Bloquear `/backup/restore` desde fuera (solo localhost)
- [ ] Restringir CORS: reemplazar `anyHost()` por origins configurables

#### 6.2 — Cloudflare Tunnel
- [ ] Documentar instalación de `cloudflared` (Windows + macOS)
- [ ] Script/instrucciones para crear túnel con URL random (gratis, sin dominio)
- [ ] Instrucciones opcionales para túnel con dominio propio
- [ ] Mostrar URL del túnel en pantalla Servicios del desktop (si se detecta cloudflared)

#### 6.3 — App móvil: setup y base ✅
- [x] Módulo `mobile/` Android (Kotlin + Jetpack Compose + Material 3)
- [x] Tema: dark con amber (#FFB74D), mismo look que desktop
- [x] Pantalla Config: URL del servidor + API key + botón "Conectar"
- [x] Cliente HTTP (Ktor Client) con header `X-API-Key` automático
- [x] DTOs propios en mobile (mirror de api/dto, sin dependencia de shared)
- [x] Navegación: BottomNavigation con 5 pantallas
- [x] Build APK en GitHub Actions (job `build-android` en release.yml)
- [x] DataStore Preferences para persistir conexión
- [ ] UpdateService: check de version.json + banner de descarga del APK

#### 6.4 — Pantallas móviles ✅
- [x] Productos: búsqueda + lista + detalle (precio, stock) + ajuste de stock
- [x] Ventas: ver pedidos con filtro por estado
- [x] Clientes: lista, búsqueda, ver deuda, registrar cobro
- [x] Cotización dólar: ver actual, actualizar
- [x] Config: URL servidor, API key, estado de conexión
- [ ] Ventas: crear pedido rápido desde el móvil

#### 6.5 — Cache offline (opcional, después)
- [ ] Room DB local para cachear últimos datos consultados
- [ ] Banner "Offline — datos de hace X minutos" si API no responde
- [ ] Cola de operaciones de escritura para sincronizar al reconectar

### Stage 7 — Integración WhatsApp (semi-automática)

Objetivo: recibir mensajes de WhatsApp de clientes, generar respuestas sugeridas
con IA (Gemini + MCP tools), y enviarlas solo con aprobación manual.

```
[Cliente WhatsApp] → Meta Cloud API → Webhook → API Motopartes → DB
    → Gemini genera sugerencia → Pantalla "Mensajes" → Revisás → Enviar/Editar/Descartar
```

#### 7.1 — Schema y modelo de datos
- [ ] Tabla `whatsapp_chats`: id, clientId (nullable), phoneNumber, name, lastMessageAt
- [ ] Tabla `whatsapp_messages`: id, chatId, direction (IN/OUT), content, audioUrl,
      transcription, suggestedReply, replyStatus (PENDING/SENT/EDITED/DISCARDED), timestamps
- [ ] Repository + Service en `shared/`
- [ ] Matcheo automático de phoneNumber con Client.phone existente
- [ ] Si no matchea, crear chat sin cliente vinculado (se puede vincular después)

#### 7.2 — WhatsApp Business Cloud API
- [ ] Crear cuenta Meta Business + app WhatsApp Business
- [ ] Configurar webhook URL (via Cloudflare Tunnel): POST /api/v1/whatsapp/webhook
- [ ] Verificación del webhook (challenge de Meta)
- [ ] Recibir mensajes de texto: parsear, guardar en DB, matchear cliente
- [ ] Recibir mensajes de audio: descargar media, transcribir, guardar
- [ ] Enviar mensajes: POST a la API de Meta con el texto aprobado
- [ ] Guardar tokens (access token, phone number ID) en AppSettings

#### 7.3 — Transcripción de audio
- [ ] Whisper API (OpenAI cloud) — ~$0.006/min, simple, buena calidad en español
- [ ] Alternativa: whisper.cpp local (gratis, pero más setup)
- [ ] Descargar audio del webhook de Meta → transcribir → guardar texto en message.transcription

#### 7.4 — Generación de respuestas sugeridas
- [ ] Al recibir mensaje, enviar al pipeline Gemini existente con contexto:
      - Últimos N mensajes del hilo (para entender "dale mandame 2")
      - Datos del cliente (nombre, deuda, pedidos recientes)
      - System prompt orientado a vendedor de motopartes
- [ ] Gemini usa MCP tools para consultar stock, precios, crear pedidos
- [ ] Guardar sugerencia en message.suggestedReply con status PENDING
- [ ] Si la IA detecta intención de pedido, armar el pedido pero NO confirmarlo
      (queda en estado CREATED hasta aprobación manual)

#### 7.5 — Pantalla "Mensajes" en Desktop
- [ ] Panel izquierdo: lista de chats ordenados por último mensaje
      - Badge con cantidad de mensajes pendientes por chat
      - Indicador de cliente vinculado / no vinculado
- [ ] Panel derecho: hilo de conversación del chat seleccionado
      - Mensajes entrantes (texto y/o transcripción de audio)
      - Respuesta sugerida con botones: [Enviar] [Editar] [Descartar]
      - Mensajes enviados (confirmados)
- [ ] Botón para vincular chat con cliente existente si no se matcheó
- [ ] Notificación visual: badge en NavigationRail "Mensajes (3)"

#### 7.6 — Pantalla "Mensajes" en Mobile
- [ ] Lista de chats con badge de pendientes
- [ ] Detalle de chat con mismo flujo: ver sugerencia → aprobar/editar/descartar
- [ ] Push notification al recibir mensaje nuevo (Firebase Cloud Messaging)

#### 7.7 — Configuración
- [ ] Card "WhatsApp" en pantalla Configuración del desktop:
      - Access Token de Meta
      - Phone Number ID
      - Toggle activar/desactivar
      - Estado de conexión (webhook activo/inactivo)
- [ ] Card "Whisper" en configuración:
      - API Key de OpenAI (para transcripción)
      - O toggle para usar whisper.cpp local

### Stage 8 — Campañas y engagement de clientes

Objetivo: usar el historial de ventas para generar mensajes proactivos a clientes
que aumenten la facturación. Mensajes revisados manualmente antes de enviar (misma
mecánica que Stage 7). Canal principal: WhatsApp.

#### 8.1 — Schema y modelo de datos
- [ ] Tabla `product_categories`: id, name, restockCycleDays (ej: "Frenos", 180 días)
- [ ] Campo `categoryId` en Products (o inferencia automática desde código/nombre)
- [ ] Tabla `campaign_rules`: id, type (RESTOCK/PRICE_ALERT/CROSS_SELL/INACTIVE/BACK_IN_STOCK/LOYALTY),
      enabled, params (JSON: días, %, categoría, etc.)
- [ ] Tabla `campaign_messages`: id, ruleId, clientId, productId, type, text,
      status (PENDING/SENT/EDITED/DISCARDED), createdAt, sentAt
- [ ] Repository + Service en `shared/`

#### 8.2 — Motor de campañas (CampaignService)
- [ ] **Recompra de consumibles**: detectar compras pasadas de productos con ciclo de
      reposición vencido, generar recordatorio con precio y stock actual
- [ ] **Alerta de suba de precio**: al importar CSV de precios, comparar pre/post,
      notificar a clientes que compraron productos que subieron
- [ ] **Stock repuesto (back in stock)**: cuando un producto pasa de stock=0 a stock>0,
      avisar a clientes que lo compraron antes
- [ ] **Cross-selling por modelo de moto**: extraer modelo del nombre de productos
      comprados, sugerir otros productos compatibles con el mismo modelo
- [ ] **Clientes inactivos**: clientes sin compras en los últimos N días configurables
- [ ] **Fidelidad/volumen**: clientes que superan X monto acumulado en el mes,
      ofrecer % de descuento en próximo pedido
- [ ] Generación de texto con Gemini: recibe datos del cliente + producto + regla,
      genera mensaje natural en español argentino
- [ ] Ejecución manual (botón "Generar campañas") o automática (cron configurable)

#### 8.3 — Pantalla "Campañas" en Desktop
- [ ] Dashboard: resumen por tipo de campaña (cuántos mensajes pendientes, enviados, conversiones)
- [ ] Lista de mensajes agrupados por tipo o por cliente
- [ ] Preview del mensaje con datos reales (nombre, producto, precio, stock)
- [ ] Acciones: [Enviar por WhatsApp] [Editar] [Descartar]
- [ ] Configuración de reglas: activar/desactivar cada tipo, ajustar parámetros
      (días de ciclo, % de descuento, umbral de inactividad)
- [ ] Métricas: tasa de conversión (mensajes enviados → pedidos generados)

#### 8.4 — Pantalla "Campañas" en Mobile
- [ ] Vista simplificada de mensajes pendientes con acciones rápidas
- [ ] Notificación cuando hay nuevas sugerencias de campaña

#### 8.5 — Integración con WhatsApp (requiere Stage 7)
- [ ] Mensajes de campaña aparecen en la pantalla "Mensajes" como salientes pendientes
- [ ] Al enviar, inicia un hilo de WhatsApp con el cliente
- [ ] Si el cliente responde, entra al flujo normal de chat con IA (Stage 7)
- [ ] Tracking: si el cliente hace un pedido dentro de X días de recibir el mensaje,
      se cuenta como conversión de esa campaña

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
- WhatsApp: integración semi-automática (IA sugiere, humano aprueba) via Meta Cloud API
- Chats persistentes por cliente con contexto para la IA
- Audio: Whisper API para transcripción de audios de WhatsApp
- Campañas: motor de reglas configurable, mensajes generados con IA, aprobación manual
- Engagement basado en datos: ciclos de recompra, alertas de precio, cross-sell por modelo
