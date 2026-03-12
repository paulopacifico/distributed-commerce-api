package com.paulopacifico.inventoryservice.shared.api

import java.time.OffsetDateTime

data class ApiErrorResponse(
    val timestamp: OffsetDateTime,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val details: List<String> = emptyList(),
)
