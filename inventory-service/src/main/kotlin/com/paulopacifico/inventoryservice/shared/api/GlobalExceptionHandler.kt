package com.paulopacifico.inventoryservice.shared.api

import com.paulopacifico.inventoryservice.inventory.application.InsufficientInventoryException
import com.paulopacifico.inventoryservice.inventory.application.InventoryNotFoundException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(InventoryNotFoundException::class)
    fun handleInventoryNotFound(
        exception: InventoryNotFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.NOT_FOUND,
        message = exception.message ?: "Inventory not found",
        path = request.requestURI,
    )

    @ExceptionHandler(InsufficientInventoryException::class)
    fun handleInsufficientInventory(
        exception: InsufficientInventoryException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.CONFLICT,
        message = exception.message ?: "Insufficient inventory",
        path = request.requestURI,
    )

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationError(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> {
        val details = exception.bindingResult.allErrors.map { error ->
            when (error) {
                is FieldError -> "${error.field}: ${error.defaultMessage}"
                else -> error.defaultMessage ?: "Validation error"
            }
        }

        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            message = "Request validation failed",
            path = request.requestURI,
            details = details,
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        exception: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.BAD_REQUEST,
        message = "Constraint violation",
        path = request.requestURI,
        details = exception.constraintViolations.map { "${it.propertyPath}: ${it.message}" },
    )

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedError(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> = buildResponse(
        status = HttpStatus.INTERNAL_SERVER_ERROR,
        message = "An unexpected error occurred",
        path = request.requestURI,
        details = listOf(exception::class.simpleName ?: "Exception"),
    )

    private fun buildResponse(
        status: HttpStatus,
        message: String,
        path: String,
        details: List<String> = emptyList(),
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(
            ApiErrorResponse(
                timestamp = OffsetDateTime.now(ZoneOffset.UTC),
                status = status.value(),
                error = status.reasonPhrase,
                message = message,
                path = path,
                details = details,
            ),
        )
}
