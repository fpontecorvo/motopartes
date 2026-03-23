package org.motopartes.desktop.chat

import ai.koog.agents.core.agent.AIAgent
import org.motopartes.desktop.chat.ChatMessage
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

class ChatService(private val tools: MotopartesTools) {

    private var agent: AIAgent<String, String>? = null
    val messages = mutableListOf<ChatMessage>()

    fun clearChat() { messages.clear() }

    fun configure(apiKey: String, provider: String, model: String) {
        if (apiKey.isBlank()) {
            agent = null; return
        }

        val executor = when (provider) {
            "anthropic" -> simpleAnthropicExecutor(apiKey)
            "openai" -> simpleOpenAIExecutor(apiKey)
            "google" -> simpleGoogleAIExecutor(apiKey)
            else -> error("Provider no soportado: $provider")
        }

        val llmModel = when (provider) {
            "anthropic" -> when {
                model.contains("opus") -> AnthropicModels.Opus_4_5
                else -> AnthropicModels.Sonnet_4_5
            }

            "openai" -> when {
                model.contains("mini") -> OpenAIModels.Chat.GPT4oMini
                else -> OpenAIModels.Chat.GPT4o
            }

            "google" -> when {
                model.contains("pro") -> GoogleModels.Gemini2_5Pro
                model.contains("lite") -> GoogleModels.Gemini2_5FlashLite
                else -> GoogleModels.Gemini2_5Flash
            }

            else -> error("Provider no soportado")
        }

        val toolRegistry = ToolRegistry.builder()
            .tools(tools)
            .build()

        agent = AIAgent(
            promptExecutor = executor,
            systemPrompt = SYSTEM_PROMPT,
            llmModel = llmModel,
            toolRegistry = toolRegistry,
            maxIterations = 20
        )
    }

    val isConfigured: Boolean get() = agent != null

    suspend fun chat(message: String): String {
        val a = agent ?: return "Configura la API key primero en el boton de ajustes."
        return try {
            a.run(message)
        } catch (e: Exception) {
            "Error: ${e.message ?: "Error desconocido"}"
        }
    }

    companion object {
        val PROVIDERS = listOf("google", "anthropic", "openai")

        val MODELS = mapOf(
            "google" to listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"),
            "anthropic" to listOf("claude-sonnet-4-5", "claude-opus-4-5"),
            "openai" to listOf("gpt-4o", "gpt-4o-mini")
        )

        private val SYSTEM_PROMPT = """
Sos el asistente de Motopartes, un negocio de venta minorista de repuestos de motos en Argentina.
Tu rol es ayudar al dueño a gestionar productos, clientes, ventas, finanzas y proveedor.

═══ REGLA #1: SIEMPRE USA HERRAMIENTAS ═══
NUNCA respondas preguntas sobre datos del negocio de memoria. SIEMPRE llama una herramienta primero.
Si el usuario pregunta cualquier cosa sobre productos, stock, clientes, ventas, finanzas o proveedor → USA UNA HERRAMIENTA.

═══ GUIA DE HERRAMIENTAS ═══

INFORMACION GENERAL:
- "como va el negocio" / "resumen" / "estado general" → getBusinessSummary()
- "a cuanto esta el dolar" → getDollarRate()

PRODUCTOS:
- "que tengo en stock" / "mi inventario" / "productos disponibles" → listProductsWithStock()
- "busca [nombre/codigo/marca/moto]" → searchProducts(query)
- "todos los productos" / "catalogo completo" → listProducts()
- "detalle del producto X" → getProduct(id)
- "crear producto" / "agregar producto" → createProduct(...)
- "cambiar precio" / "actualizar producto" → updateProduct(id, ...)
- "ajustar stock" / "entrada de mercaderia" → adjustStock(id, delta)

CLIENTES:
- "mis clientes" / "lista de clientes" → listClients()
- "busca al cliente [nombre]" → searchClients(query)
- "ficha del cliente" / "detalle cliente" → getClient(id)
- "agregar cliente" / "nuevo cliente" → createClient(...)

PEDIDOS (flujo: CREATED → CONFIRMED → ASSEMBLED → INVOICED):
- "ventas" / "ventas pendientes" → listOrders() o listOrders("CREATED")
- "detalle pedido #X" → getOrder(id)
- "crear pedido" → createOrder(clientId, itemsJson)
- "venta rapida" / "procesar pedido completo" → quickOrder(clientId, itemsJson)
- "confirmar pedido" → confirmOrder(id)
- "armar pedido" → assembleOrder(id)
- "facturar pedido" → invoiceOrder(id)
- "cancelar pedido" → cancelOrder(id)

FINANZAS:
- "movimientos" / "ultimos movimientos" → getMovements()
- "movimientos de [cliente]" → getClientMovements(clientId)
- "cobrar" / "registro de pago de cliente" → recordClientPayment(clientId, amount, desc)
- "pagar al proveedor" → recordSupplierPayment(amount, desc)
- "datos del proveedor" / "deuda proveedor" → getSupplier()

IMPORTACION:
- "importar productos" / "cargar CSV" → importProducts(csvContent)
- "importar factura" / "cargar factura proveedor" → importPurchaseInvoice(csvContent)

═══ FLUJO DE PEDIDOS ═══
1. CREATED: pedido nuevo, se puede editar
2. CONFIRMED: items bloqueados
3. ASSEMBLED: stock descontado de los productos
4. INVOICED: venta registrada, deuda generada al cliente
En cualquier momento (excepto INVOICED) se puede cancelar con cancelOrder.
Para hacer todo de una vez, usa quickOrder.

═══ COMPORTAMIENTO ═══
- Responde SIEMPRE en español argentino, conciso y claro.
- Precios en pesos argentinos (ARS) salvo que se indique USD.
- Si una herramienta retorna ERROR, explica el problema y sugeri la solucion.
- Si necesitas un ID que no tenes, busca primero (searchProducts, searchClients, etc).
- Si el usuario pide algo ambiguo, usa la herramienta mas probable y pedile clarificacion solo si es necesario.
- Nunca inventes datos. Si no hay datos, deci que no hay.
- Para crear ventas, siempre confirma los items y precios con el usuario antes de ejecutar.
        """.trimIndent()
    }
}
