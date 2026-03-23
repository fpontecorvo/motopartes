package org.motopartes.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.motopartes.config.AppPaths
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Represents the different services managed by Motopartes
 */
enum class ServiceType {
    DATABASE,
    API_REST,
    MCP_SERVER
}

/**
 * Represents the status of a service
 */
enum class ServiceStatus {
    STARTING,
    RUNNING,
    ERROR,
    STOPPED
}

/**
 * Represents a service with its metadata
 */
data class Service(
    val type: ServiceType,
    val name: String,
    val status: ServiceStatus,
    val port: Int?,
    val errorMessage: String?,
    val startedAt: LocalDateTime?
)

/**
 * Log level for LogEntry
 */
enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * Represents a single log entry
 */
data class LogEntry(
    val timestamp: LocalDateTime,
    val level: LogLevel,
    val source: String,
    val message: String
)

/**
 * Thread-safe ring buffer for storing log entries
 */
class LogBuffer(private val capacity: Int = 500) {
    private val buffer = ConcurrentLinkedDeque<LogEntry>()

    /**
     * Add a log entry to the buffer
     */
    fun add(level: LogLevel, source: String, message: String) {
        val entry = LogEntry(
            timestamp = LocalDateTime.now(),
            level = level,
            source = source,
            message = message
        )

        buffer.addLast(entry)

        // Maintain capacity by removing oldest entries
        while (buffer.size > capacity) {
            buffer.removeFirst()
        }
    }

    /**
     * Get all log entries in the buffer
     */
    fun getAll(): List<LogEntry> {
        return buffer.toList()
    }

    /**
     * Get filtered log entries by source and/or level
     */
    fun getFiltered(source: String? = null, level: LogLevel? = null): List<LogEntry> {
        return buffer.filter { entry ->
            (source == null || entry.source == source) &&
            (level == null || entry.level == level)
        }
    }

    /**
     * Clear all log entries
     */
    fun clear() {
        buffer.clear()
    }

    /**
     * Get the current size of the buffer
     */
    fun size(): Int = buffer.size
}

/**
 * Manages services and their states in a reactive manner
 */
class ServiceManager {
    private val services = mutableMapOf<ServiceType, Service>()

    private val _servicesFlow = MutableStateFlow<Map<ServiceType, Service>>(emptyMap())
    val servicesFlow: StateFlow<Map<ServiceType, Service>> = _servicesFlow.asStateFlow()

    private val logBuffer = LogBuffer()

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    init {
        // Initialize all services in STOPPED state
        ServiceType.entries.forEach { serviceType ->
            services[serviceType] = Service(
                type = serviceType,
                name = serviceType.name,
                status = ServiceStatus.STOPPED,
                port = null,
                errorMessage = null,
                startedAt = null
            )
        }
        _servicesFlow.value = services.toMap()
    }

    /**
     * Update the status of a service
     */
    fun updateStatus(
        service: ServiceType,
        status: ServiceStatus,
        port: Int? = null,
        errorMessage: String? = null
    ) {
        val currentService = services[service] ?: return

        val startedAt = when {
            status == ServiceStatus.RUNNING && currentService.status != ServiceStatus.RUNNING -> LocalDateTime.now()
            status != ServiceStatus.RUNNING -> null
            else -> currentService.startedAt
        }

        val updatedService = currentService.copy(
            status = status,
            port = port ?: currentService.port,
            errorMessage = errorMessage,
            startedAt = startedAt
        )

        services[service] = updatedService
        _servicesFlow.value = services.toMap()

        // Log the status change (using reactive log method)
        log(
            level = when (status) {
                ServiceStatus.ERROR -> LogLevel.ERROR
                ServiceStatus.STARTING -> LogLevel.INFO
                ServiceStatus.RUNNING -> LogLevel.INFO
                ServiceStatus.STOPPED -> LogLevel.INFO
            },
            source = "ServiceManager",
            message = "Service ${service.name} status changed to $status" +
                    (if (port != null) " on port $port" else "") +
                    (if (errorMessage != null) ": $errorMessage" else "")
        )
    }

    /**
     * Get a specific service by type
     */
    fun getService(service: ServiceType): Service? {
        return services[service]
    }

    /**
     * Get all services
     */
    fun getAllServices(): Map<ServiceType, Service> {
        return services.toMap()
    }

    /**
     * Get the log buffer
     */
    fun getLogBuffer(): LogBuffer {
        return logBuffer
    }

    /**
     * Add a log entry and update the reactive flow
     */
    fun log(level: LogLevel, source: String, message: String) {
        logBuffer.add(level, source, message)
        _logsFlow.value = logBuffer.getAll()
    }

    /**
     * Clear all log entries and update the reactive flow
     */
    fun clearLogs() {
        logBuffer.clear()
        _logsFlow.value = emptyList()
    }

    /**
     * Get the logs directory path
     */
    fun logDir(): Path {
        return AppPaths.dataDir().resolve("logs")
    }
}
