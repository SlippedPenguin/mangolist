package com.slippedpenguin.mangolist.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.SubcomposeAsyncImage
import coil.request.AsyncImagePainter

/*
 * CoverImage — AsyncImage with a guaranteed visible fallback.
 *
 * The old bare `AsyncImage(model = entry.cover)` rendered literally nothing
 * (a near-black box) when the URL was null or failed to load — that's what
 * made some manga cards look "bugged out / showing nothing" even though the
 * underlying data (title, cover URL) is perfectly fine on AniList.
 *
 * This component draws a tier-tinted placeholder with the title's first
 * letter whenever the image is missing, loading, or errored, and swaps in
 * the real image (with a soft crossfade) once it decodes.
 */
@Composable
fun CoverImage(
    model: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    tint: Color = MaterialTheme.colorScheme.surfaceVariant,
    label: String? = null,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        crossfade = true,
    ) {
        when (val state = painter.state) {
            is AsyncImagePainter.State.Success -> {
                Image(
                    painter = state.painter,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> CoverPlaceholder(tint = tint, label = label)
        }
    }
}

@Composable
private fun CoverPlaceholder(tint: Color, label: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label?.trim()?.take(1)?.uppercase() ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
