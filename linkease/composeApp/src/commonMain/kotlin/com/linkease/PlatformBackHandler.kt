package com.linkease

import androidx.compose.runtime.Composable

// Android intercepts the hardware/gesture back action for in-app navigation; other
// targets have no equivalent, so they get a no-op actual.
@Composable
expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)
