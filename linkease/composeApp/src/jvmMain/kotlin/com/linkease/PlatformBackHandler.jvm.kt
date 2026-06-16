package com.linkease

import androidx.compose.runtime.Composable

// This target only exists to share domain code with the Spring Boot server module;
// the UI composables are never actually rendered here.
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
}
