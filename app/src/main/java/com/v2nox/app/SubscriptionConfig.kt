package com.v2nox.app

data class SubscriptionConfig(
    val id: String,
    val name: String = "",
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdateAttempt: Long = 0L,
    val lastUpdateSuccess: Long = 0L,
    val updateIntervalHours: Int? = null,
    val autoUpdate: Boolean = true,
    val uploadBytes: Long = 0L,
    val downloadBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val expireEpochSeconds: Long = 0L,
    val lastError: String? = null,
)
