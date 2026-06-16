package com.linkease

import androidx.compose.runtime.Composable

// Browsers have no hardware/gesture back action to intercept for in-app navigation.
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
}
