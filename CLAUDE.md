# CLAUDE.md — Motopartes

## Proyecto
App de escritorio standalone para gestión de venta minorista de motopartes.
Kotlin/JVM, Compose Desktop, SQLite embebida via Exposed.

## Estructura
```
shared/    → Modelos de dominio, lógica de negocio, repositorios, servicios, acceso a DB
desktop/   → UI Compose Desktop (Material 3), punto de entrada
api/       → API REST (Ktor + Netty, puerto 8080)
buildSrc/  → Convention plugins (kotlin-jvm)
```

## Build & Run
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 23)
./gradlew :shared:test         # Tests del módulo shared
./gradlew :desktop:run         # Ejecutar la app desktop
./gradlew :api:run             # Ejecutar la API REST (puerto 8080)
./gradlew :desktop:packageDmg  # Generar instalador macOS
./gradlew build                # Compilar todo
docker compose up --build      # API en Docker
```

## Paquete base
`org.motopartes`

| Módulo  | Paquete                          |
|---------|----------------------------------|
| shared  | `org.motopartes.model`           |
| shared  | `org.motopartes.db`              |
| shared  | `org.motopartes.repository`      |
| shared  | `org.motopartes.service`         |
| shared  | `org.motopartes.config`          |
| desktop | `org.motopartes.desktop`         |
| desktop | `org.motopartes.desktop.screen`  |
| desktop | `org.motopartes.desktop.component`|
| api     | `org.motopartes.api`             |
| api     | `org.motopartes.api.route`       |
| api     | `org.motopartes.api.dto`         |

## Estándares de Código

### Modelos
- Data classes inmutables en `shared/model/`
- `BigDecimal` para dinero (nunca Double/Float)
- Enums en `Enums.kt` cuando son simples, archivo propio si tienen lógica

### Repositorios
- Una clase por entidad en `shared/repository/`
- Aceptan y retornan modelos de dominio (no entidades de Exposed)
- Cada método público wrappea su lógica en `transaction {}`
- Mapper privado `ResultRow.toModel()` al final del archivo

### Servicios
- Lógica de negocio que orquesta múltiples repositorios va en `shared/service/`
- Servicios reciben repositorios y otros servicios como dependencias en constructor
- Operaciones atómicas usan `transaction {}` directo con Exposed DSL
- Timestamps (`LocalDateTime`) se pasan como parámetro `now` para facilitar testing

### Base de datos
- Tablas Exposed (DSL, no DAO) en `shared/db/Tables.kt`
- `DatabaseFactory` maneja conexión y schema
- Producción: archivo SQLite en path del OS
- Tests: `DatabaseFactory.initInMemory()` con temp file SQLite

### Tests
- Toda lógica de negocio y repositorios deben tener tests unitarios
- Usar `@BeforeTest` con `DatabaseFactory.initInMemory()` para tests de DB
- Naming: backtick descriptivo en inglés (`fun \`insert and find by id\`()`)
- Helpers: `sampleXxx()` o `createXxx()` para crear entidades de test

### UI Desktop
- Pantallas en `desktop/screen/`, componentes reutilizables en `desktop/component/`
- NavigationRail para navegación lateral
- Todo el texto de UI en español
- `FormDialog` para formularios, `ConfirmDialog` para confirmaciones, `SearchBar` para búsquedas
- Para `kotlinx.datetime.LocalDateTime`: usar `java.time.LocalDateTime.now()` y convertir (evita conflicto con `kotlin.time.Clock` en Kotlin 2.3)

### Moneda
- Productos pueden tener precio en USD o ARS (`Currency` enum)
- Venta siempre en ARS
- Precio de compra (`purchasePrice`) puede ser USD o ARS
- Precio de venta (`salePrice`) siempre en ARS, por defecto costo + 30%
- Conversión via `Product.purchasePriceInArs(dollarRate)` y `Product.defaultSalePrice()`
- Cotización dólar almacenada con fecha para trazabilidad

### Finanzas
- Entrega de pedido → crea movimiento SALE + incrementa saldo deudor del cliente
- Compra al proveedor → crea movimiento PURCHASE + incrementa deuda con proveedor
- Cobro a cliente → crea movimiento CLIENT_PAYMENT + reduce saldo deudor
- Pago a proveedor → crea movimiento SUPPLIER_PAYMENT + reduce deuda
