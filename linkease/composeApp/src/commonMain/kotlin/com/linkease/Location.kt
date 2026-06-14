package com.linkease

data class Location(
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val colorHex: String = "#4CAF50"
)
