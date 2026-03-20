package org.motopartes.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val error: String)

@Serializable
data class SuccessResponse(val success: Boolean = true)

sealed class ApiException(message: String) : RuntimeException(message)
class NotFoundException(message: String) : ApiException(message)
class BadRequestException(message: String) : ApiException(message)
class ConflictException(message: String) : ApiException(message)
