# Motopartes

Sistema de gestion de venta minorista de motopartes. App de escritorio + API REST.

## Tecnologias

- **Kotlin 2.3** / JVM 23
- **Compose Desktop** (Material 3) — UI de escritorio
- **Ktor 3.1** — API REST
- **Exposed** — ORM para SQLite
- **SQLite** — base de datos embebida (archivo local)

## Estructura del proyecto

```
shared/    → Modelos, repositorios, servicios, acceso a DB
desktop/   → App de escritorio (Compose Desktop)
api/       → API REST (Ktor + Netty, puerto 8080)
buildSrc/  → Convention plugins de Gradle
```

## Requisitos para desarrollo

- **JDK 23** ([Eclipse Temurin](https://adoptium.net/) recomendado)
- **Git**

En macOS:
```bash
brew install --cask temurin@23
export JAVA_HOME=$(/usr/libexec/java_home -v 23)
```

En Windows:
- Descargar e instalar JDK 23 de [Adoptium](https://adoptium.net/)
- Configurar `JAVA_HOME` en variables de entorno

## Desarrollo

```bash
# Compilar todo
./gradlew build

# Ejecutar la app de escritorio
./gradlew :desktop:run

# Ejecutar la API REST (puerto 8080)
./gradlew :api:run

# Ejecutar tests
./gradlew :shared:test

# Cargar datos de ejemplo
./gradlew :shared:seed
```

## API REST

La API expone toda la logica de negocio bajo `/api/v1/`:

| Recurso | Endpoints |
|---------|-----------|
| Productos | `GET/POST /products`, `GET/PUT/DELETE /products/{id}`, `GET /products/search?q=`, `PATCH /products/{id}/stock` |
| Clientes | `GET/POST /clients`, `GET/PUT/DELETE /clients/{id}`, `GET /clients/search?q=` |
| Proveedor | `GET/POST/PUT /supplier` |
| Pedidos | `GET/POST /orders`, `GET/DELETE /orders/{id}`, `PUT /orders/{id}/items`, `POST /orders/{id}/confirm\|assemble\|invoice\|cancel` |
| Cotizacion | `GET/POST /dollar-rates`, `GET /dollar-rates/latest`, `GET /dollar-rates/{date}` |
| Finanzas | `GET /finance/movements`, `POST /finance/client-payment`, `POST /finance/supplier-payment` |
| Compras | `POST /purchases` |

Ejemplo:
```bash
curl http://localhost:8080/api/v1/products
curl http://localhost:8080/api/v1/products/search?q=ruleman
curl -X POST http://localhost:8080/api/v1/dollar-rates \
  -H "Content-Type: application/json" \
  -d '{"rate":"1450.00","date":"2026-03-21"}'
```

## Distribucion e instalacion

### Generar instalador

Cada instalador se genera **desde el SO de destino** (no hay cross-compile):

| SO | Comando | Genera | Ubicacion |
|----|---------|--------|-----------|
| macOS | `./gradlew :desktop:packageDmg` | `Motopartes-1.0.0.dmg` | `desktop/build/compose/binaries/main/dmg/` |
| Windows | `gradlew.bat :desktop:packageMsi` | `Motopartes-1.0.0.msi` | `desktop\build\compose\binaries\main\msi\` |
| Linux | `./gradlew :desktop:packageDeb` | `motopartes_1.0.0_amd64.deb` | `desktop/build/compose/binaries/main/deb/` |

El instalador incluye el JRE embebido — el usuario final **no necesita instalar Java**.

### macOS

```bash
./gradlew :desktop:packageDmg
# Abrir el .dmg, arrastrar Motopartes a Aplicaciones
```

### Windows

Requisitos para buildear:
1. **JDK 23** instalado
2. **WiX Toolset v3** instalado ([wixtoolset.org](https://wixtoolset.org))

```bash
git clone <repo> motopartes
cd motopartes/untitled
gradlew.bat :desktop:packageMsi
# Ejecutar el .msi generado para instalar
```

Si no se quiere instalar WiX, se puede generar una carpeta con el `.exe` directo:
```bash
gradlew.bat :desktop:createDistributable
# El ejecutable queda en: desktop\build\compose\binaries\main\app\
```

### Linux (Debian/Ubuntu)

```bash
./gradlew :desktop:packageDeb
sudo dpkg -i desktop/build/compose/binaries/main/deb/motopartes_1.0.0_amd64.deb
```

## Docker (solo API)

La API se puede correr en Docker. La app de escritorio no se dockeriza porque requiere interfaz grafica.

```bash
# Levantar
docker compose up --build

# Levantar en background
docker compose up -d --build

# Parar
docker compose down
```

La API queda en `http://localhost:8080`. Los datos se persisten en un volumen Docker (`motopartes-data`).

### Variables de entorno

| Variable | Descripcion | Default |
|----------|-------------|---------|
| `MOTOPARTES_DATA_DIR` | Directorio para la base de datos SQLite | Segun el SO (`~/Library/Application Support/motopartes` en macOS, `~/.local/share/motopartes` en Linux) |
