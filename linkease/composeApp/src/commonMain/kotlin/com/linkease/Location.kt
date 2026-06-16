package com.linkease

data class Location(
    val id: Long = 0,
    val name: String,
    val address: String = "",
    val colorHex: String = "#4CAF50",
    val mapsLink: String? = null,
)

// Percent-encodes a UTF-8 string for use in a URL query (RFC 3986 unreserved chars left as-is).
// Implemented by hand because the project avoids expect/actual — encodeToByteArray() is a plain
// Kotlin stdlib function available on every target, so this needs no platform-specific code.
private fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val ch = code.toChar()
        if (code < 128 && (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.' || ch == '~')) {
            append(ch)
        } else {
            append('%')
            append(code.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

// Google Maps search link — Google resolves the address into a pin when the link is opened,
// so no geocoding API key is needed in the app itself.
fun addressMapsUrl(address: String): String? =
    if (address.isBlank()) null else "https://www.google.com/maps/search/?api=1&query=" + urlEncode(address)

// A pasted Google Maps link (precise — the user pinned it themselves in the Maps app)
// takes priority over a generated address search, which only guesses at the right pin.
fun Location.mapsUrl(): String? = mapsLink?.takeIf { it.isNotBlank() } ?: addressMapsUrl(address)
