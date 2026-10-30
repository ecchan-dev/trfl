package org.trfl.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

fun wrapCutAtQuoteBoundary(text: String, proposed: Int): Int {
    val cut = proposed.coerceIn(0, text.length)
    val quoteStart = Regex(">>[0-9]+").findAll(text).firstOrNull { cut > it.range.first && cut <= it.range.last }?.range?.first
    val linkStart = postLinks(text).firstOrNull { cut > it.start && cut < it.end }?.start
    return quoteStart ?: linkStart ?: cut
}

/** Measure the lines beside the thumbnail, then let the remaining text use the full width. */
@Composable
fun WrappedPostBody(post: Post, compact: Boolean, onImage: () -> Unit, onQuote: (Long) -> Unit) {
    @Composable fun Body(value: String, maxLines: Int = Int.MAX_VALUE) {
        if (compact) Text(value, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        else QuoteBody(value, onQuote)
    }
    if (post.thumb == null) { Body(post.body, if (compact) 5 else Int.MAX_VALUE); return }
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyLarge
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val imageSize = 120.dp
        val gap = 12.dp
        val beside = maxWidth - imageSize - gap
        val image: @Composable () -> Unit = {
            AsyncImage(post.thumb, "Open attachment ${post.id}", Modifier.size(imageSize).clip(RoundedCornerShape(12.dp)).clickable(onClick = onImage), contentScale = ContentScale.Crop)
        }
        // Avoid a tiny text column with very large accessibility fonts/narrow windows.
        if (beside < 96.dp * density.fontScale) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { image(); Body(post.body, if (compact) 5 else Int.MAX_VALUE) }
        } else {
            val widthPx = with(density) { beside.roundToPx() }
            val heightPx = with(density) { imageSize.toPx() }
            val layout = remember(post.body, style, widthPx, density.density, density.fontScale, measurer) {
                measurer.measure(AnnotatedString(post.body), style, constraints = Constraints(maxWidth = widthPx))
            }
            // A line starting beside the image can extend below its bottom. This avoids
            // leaving an entire line-sized hole when the heights are not exact multiples.
            var lines = (0 until layout.lineCount).takeWhile { layout.getLineTop(it) < heightPx }.size.coerceAtLeast(1)
            if (compact) lines = lines.coerceAtMost(5)
            val cut = wrapCutAtQuoteBoundary(post.body, layout.getLineEnd((lines - 1).coerceAtMost(layout.lineCount - 1), visibleEnd = false))
            val first = post.body.take(cut).trimEnd('\n')
            val remaining = post.body.drop(cut)
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    image()
                    Box(Modifier.weight(1f)) {
                        if (compact && lines == 5 && remaining.isNotEmpty()) Text(post.body, maxLines = 5, overflow = TextOverflow.Ellipsis)
                        else Body(first, lines)
                    }
                }
                if (remaining.isNotEmpty() && (!compact || lines < 5)) Body(remaining, if (compact) 5 - lines else Int.MAX_VALUE)
            }
        }
    }
}
