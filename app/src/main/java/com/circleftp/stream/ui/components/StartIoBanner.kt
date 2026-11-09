package com.circleftp.stream.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.startapp.sdk.ads.banner.Banner

/**
 * Reusable Start.io Sticky Banner component for Jetpack Compose.
 * Safely embeds the Start.io Banner view with a minimum height constraint
 * to prevent zero-height collapse during UI passes.
 */
@Composable
fun StartIoBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp), // Prevents banner collapse
        factory = { context ->
            Banner(context)
        },
        update = { /* View updates handled internally by StartApp SDK */ },
        onRelease = { banner ->
            banner.removeAllViews()
        }
    )
}
