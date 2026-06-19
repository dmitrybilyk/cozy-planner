package com.linkease

data class Client(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val colorHex: String = "#2196F3",
    val hourlyRate: Double = 0.0,
    val packageTotal: Int = 0,
    val packageUsed: Int = 0,
    val birthDate: String? = null, // "MM-DD" format
    val firebaseClientId: String? = null,
)
