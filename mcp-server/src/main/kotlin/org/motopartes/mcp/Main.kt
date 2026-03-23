package org.motopartes.mcp

import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.runBlocking
import org.motopartes.db.DatabaseFactory

fun main(args: Array<String>) {
    DatabaseFactory.init()

    val server = createMcpServer()

    val mode = args.firstOrNull() ?: "--stdio"

    when (mode) {
        "--stdio" -> runBlocking {
            val transport = StdioServerTransport(System.`in`.asSource().buffered(), System.out.asSink().buffered())
            server.createSession(transport)
            // Keep alive
            while (true) { kotlinx.coroutines.delay(1000) }
        }
        "--sse" -> {
            val port = args.getOrNull(1)?.toIntOrNull() ?: 3001
            embeddedServer(Netty, port = port) {
                mcp { server }
            }.start(wait = true)
        }
        else -> {
            System.err.println("Uso: mcp-server [--stdio | --sse <port>]")
            System.err.println("  --stdio  (default) Comunicacion por stdin/stdout, para Claude Desktop")
            System.err.println("  --sse <port>       Servidor SSE en el puerto indicado, para clientes web")
        }
    }
}
