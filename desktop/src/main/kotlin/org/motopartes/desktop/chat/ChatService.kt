package org.motopartes.desktop.chat

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleAnthropicExecutor
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

class ChatService(private val tools: MotopartesTools) {

    private var agent: AIAgent<String, String>? = null

    fun configure(apiKey: String, provider: String, model: String) {
        if (apiKey.isBlank()) { agent = null; return }

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
            systemPrompt = """
                Sos un asistente de Motopartes, un negocio de venta minorista de repuestos de motos en Argentina.
                Ayudas a gestionar productos, clientes, pedidos, finanzas y cotizacion del dolar.
                Responde siempre en español argentino, de forma concisa y clara.
                Cuando el usuario pida algo que requiere datos, usa las herramientas disponibles.
                Los precios estan en pesos argentinos (ARS) salvo que se indique lo contrario.
                Para crear pedidos necesitas: ID del cliente y lista de items con productId, quantity y unitPrice.
            """.trimIndent(),
            llmModel = llmModel,
            toolRegistry = toolRegistry,
            maxIterations = 15
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
            "google" to listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.5-flash-lite"),
            "anthropic" to listOf("claude-sonnet-4-5", "claude-opus-4-5"),
            "openai" to listOf("gpt-4o", "gpt-4o-mini")
        )
    }
}
