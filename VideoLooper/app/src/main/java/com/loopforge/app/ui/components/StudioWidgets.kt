package com.loopforge.app.ui.components

import android.net.Uri
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.loopforge.app.R
import com.loopforge.app.analysis.CandidateKind
import com.loopforge.app.analysis.LoopCandidate
import com.loopforge.app.ui.theme.Neon
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.min

/**
 * Plays either the whole original or only the selected loop over and over, with a lap counter
 * so the seam can be judged in motion.
 */
@OptIn(UnstableApi::class)
@Composable
fun LoopPreview(
    uri: Uri,
    startUs: Long,
    endUs: Long,
    loopOnly: Boolean,
    active: Boolean,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var laps by remember { mutableIntStateOf(1) }
    var position by remember { mutableFloatStateOf(0f) }
    var muted by remember { mutableStateOf(true) }
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) laps++
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(uri, startUs, endUs, loopOnly, active) {
        if (!active) {
            player.stop()
            return@LaunchedEffect
        }
        delay(280) // debounce while the user drags handles
        val builder = MediaItem.Builder().setUri(uri)
        if (loopOnly) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionUs(startUs).setEndPositionUs(endUs).build(),
            )
        }
        laps = 1
        player.setMediaItem(builder.build())
        player.prepare()
        player.playWhenReady = true
    }
    LaunchedEffect(player) {
        while (true) {
            val duration = player.duration
            position = if (duration > 0) (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(40)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val isActive by rememberUpdatedState(active)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> if (isActive) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val maxH = 420.dp
        val width = min(maxWidth.value, maxH.value * aspectRatio).dp
        Box(
            Modifier
                .width(width)
                .aspectRatio(aspectRatio.coerceIn(0.3f, 3f))
                .clip(shape)
                .background(Color.Black)
                .border(1.dp, Brush.linearGradient(listOf(Neon.Magenta.copy(alpha = 0.6f), Neon.Cyan.copy(alpha = 0.6f))), shape)
                .clickable { if (player.isPlaying) player.pause() else player.play() },
        ) {
            AndroidView(
                factory = { ctx ->
                    (LayoutInflater.from(ctx).inflate(R.layout.preview_player, FrameLayout(ctx), false) as PlayerView).also { it.player = player }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoPill(
                    if (loopOnly) "LOOP · Durchlauf $laps" else "ORIGINAL",
                    color = if (loopOnly) Neon.Cyan else Neon.TextDim,
                    icon = Icons.Rounded.AllInclusive,
                    modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(50)),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .clickable { muted = !muted },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (muted) "Ton an" else "Ton aus",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Canvas(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp),
            ) {
                drawRect(Color(0x33FFFFFF))
                drawRect(Neon.brush, size = Size(size.width * position, size.height))
            }
        }
    }
}

/** Horizontally scrolling cards of the detected loops. */
@Composable
fun CandidateCarousel(
    candidates: List<LoopCandidate>,
    thumbs: Map<Int, ImageBitmap>,
    selected: LoopCandidate?,
    onSelect: (LoopCandidate) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(candidates, key = { "${it.startFrame}-${it.endFrame}" }) { c ->
            val isSelected = selected != null && selected.startFrame == c.startFrame && selected.endFrame == c.endFrame
            CandidateCard(c, thumbs[c.startFrame], isSelected) { onSelect(c) }
        }
    }
}

@Composable
private fun CandidateCard(candidate: LoopCandidate, thumb: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .width(158.dp)
            .clip(shape)
            .background(if (selected) Color(0x26FFFFFF) else Neon.Glass)
            .border(if (selected) 2.dp else 1.dp, if (selected) Neon.brush else SolidColor(Neon.Stroke), shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.35f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF15152A)),
        ) {
            if (thumb != null) {
                Image(thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            ScoreRing(
                candidate.quality,
                size = 40.dp,
                stroke = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color(0x99000000), CircleShape),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            kindLabel(candidate.kind),
            style = MaterialTheme.typography.labelMedium,
            color = if (candidate.kind == CandidateKind.BEST) Neon.Cyan else Neon.TextDim,
        )
        Text(formatSeconds(candidate.durationUs), style = MaterialTheme.typography.titleMedium, color = Neon.Text)
        Text(
            if (candidate.periodMultiple > 0) "${candidate.periodMultiple}× Periode · ab ${formatPrecise(candidate.startUs)}"
            else "ab ${formatPrecise(candidate.startUs)}",
            style = MaterialTheme.typography.labelSmall,
            color = Neon.TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun kindLabel(kind: CandidateKind) = when (kind) {
    CandidateKind.BEST -> "BESTER LOOP"
    CandidateKind.LONGEST -> "LÄNGSTER"
    CandidateKind.SHORTEST -> "KÜRZESTER"
    CandidateKind.ALTERNATIVE -> "ALTERNATIVE"
    CandidateKind.MANUAL -> "EIGENE WAHL"
}

/**
 * The self-similarity map: pixel (x, y) is bright when frame x looks like frame y. Bright spots
 * away from the diagonal are places where the video repeats itself. Tapping one picks that loop
 * (x = loop end, y = loop start).
 */
@Composable
fun SimilarityMapView(
    bitmap: ImageBitmap,
    candidates: List<Pair<Float, Float>>,
    selection: Pair<Float, Float>?,
    onPick: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val pick by rememberUpdatedState(onPick)
    var dragPoint by remember { mutableStateOf<Offset?>(null) }
    val transition = rememberInfiniteTransition(label = "map")
    val pulse by transition.animateFloat(0.6f, 1f, infiniteRepeatable(tween(900), androidx.compose.animation.core.RepeatMode.Reverse), label = "pulse")
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(1.dp, Neon.Stroke, shape)
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    pick(p.x / size.width, p.y / size.height)
                }
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragPoint = it
                    },
                    onDragEnd = {
                        dragPoint?.let { p ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pick(p.x / size.width, p.y / size.height)
                        }
                        dragPoint = null
                    },
                    onDragCancel = { dragPoint = null },
                ) { change, amount ->
                    change.consume()
                    dragPoint = dragPoint?.plus(amount)
                }
            },
    ) {
        Image(
            bitmap,
            contentDescription = "Wiederholungs-Karte",
            contentScale = ContentScale.FillBounds,
            filterQuality = FilterQuality.Low,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            for ((end, start) in candidates) {
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 7.dp.toPx(), center = Offset(end * w, start * h), style = Stroke(1.5.dp.toPx()))
            }
            selection?.let { (end, start) ->
                val c = Offset(end * w, start * h)
                drawLine(Neon.Cyan.copy(alpha = 0.5f), Offset(c.x, 0f), Offset(c.x, h), strokeWidth = 1.dp.toPx())
                drawLine(Neon.Magenta.copy(alpha = 0.5f), Offset(0f, c.y), Offset(w, c.y), strokeWidth = 1.dp.toPx())
                drawCircle(Neon.Cyan, radius = 12.dp.toPx() * pulse, center = c, style = Stroke(2.5.dp.toPx()))
                drawCircle(Color.White, radius = 3.dp.toPx(), center = c)
            }
            dragPoint?.let { p ->
                drawCircle(Color.White, radius = 18.dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
                drawLine(Color.White.copy(alpha = 0.4f), Offset(p.x, 0f), Offset(p.x, h))
                drawLine(Color.White.copy(alpha = 0.4f), Offset(0f, p.y), Offset(w, p.y))
            }
        }
    }
}

/**
 * Film strip with two draggable handles marking the loop. Dragging inside the window moves the
 * whole loop; the outside is dimmed.
 */
@Composable
fun LoopTimeline(
    frames: List<ImageBitmap>,
    durationUs: Long,
    startUs: Long,
    endUs: Long,
    onChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentStart by rememberUpdatedState(startUs)
    val currentEnd by rememberUpdatedState(endUs)
    val change by rememberUpdatedState(onChange)
    val density = LocalDensity.current
    val handleSlop = with(density) { 28.dp.toPx() }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Color(0xFF15152A)),
        ) {
            frames.forEach { frame ->
                Image(
                    frame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        var mode by remember { mutableIntStateOf(0) } // 1 = start, 2 = end, 3 = window
        var anchorStart by remember { mutableLongStateOf(0L) }
        var anchorEnd by remember { mutableLongStateOf(0L) }
        var accumulated by remember { mutableFloatStateOf(0f) }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(durationUs) {
                    detectHorizontalDragGestures(
                        onDragStart = { p ->
                            val w = size.width.toFloat()
                            val sx = currentStart.toFloat() / durationUs * w
                            val ex = currentEnd.toFloat() / durationUs * w
                            mode = when {
                                abs(p.x - sx) <= handleSlop && abs(p.x - sx) <= abs(p.x - ex) -> 1
                                abs(p.x - ex) <= handleSlop -> 2
                                p.x in sx..ex -> 3
                                p.x < sx -> 1
                                else -> 2
                            }
                            anchorStart = currentStart
                            anchorEnd = currentEnd
                            accumulated = 0f
                        },
                        onDragEnd = { mode = 0 },
                        onDragCancel = { mode = 0 },
                    ) { pointer, amount ->
                        pointer.consume()
                        accumulated += amount
                        val deltaUs = (accumulated / size.width * durationUs).toLong()
                        val minLen = 100_000L
                        when (mode) {
                            1 -> change((anchorStart + deltaUs).coerceIn(0L, anchorEnd - minLen), anchorEnd)
                            2 -> change(anchorStart, (anchorEnd + deltaUs).coerceIn(anchorStart + minLen, durationUs))
                            3 -> {
                                val len = anchorEnd - anchorStart
                                val s = (anchorStart + deltaUs).coerceIn(0L, durationUs - len)
                                change(s, s + len)
                            }
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val sx = startUs.toFloat() / durationUs * w
            val ex = endUs.toFloat() / durationUs * w
            val dim = Color(0xB307070F)
            drawRect(dim, Offset.Zero, Size(sx, h))
            drawRect(dim, Offset(ex, 0f), Size(w - ex, h))
            val stroke = 3.dp.toPx()
            drawRoundRect(
                Neon.brush,
                topLeft = Offset(sx, stroke / 2),
                size = Size((ex - sx).coerceAtLeast(1f), h - stroke),
                cornerRadius = CornerRadius(10.dp.toPx()),
                style = Stroke(stroke),
            )
            val handleW = 10.dp.toPx()
            val handleH = h * 0.62f
            for ((x, color) in listOf(sx to Neon.Magenta, ex to Neon.Cyan)) {
                drawRoundRect(
                    color,
                    topLeft = Offset(x - handleW / 2, (h - handleH) / 2),
                    size = Size(handleW, handleH),
                    cornerRadius = CornerRadius(handleW / 2),
                )
                drawLine(Color.White, Offset(x, h * 0.4f), Offset(x, h * 0.6f), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

/**
 * Last frame before the jump next to the first frame after it. Below, either an automatic blink
 * test of the hard cut or – with a cross-fade – a live dissolve between the two pictures that are
 * mixed in the middle of the fade, so double images become visible before exporting.
 */
@Composable
fun SeamLens(
    last: ImageBitmap?,
    first: ImageBitmap?,
    blendA: ImageBitmap?,
    blendB: ImageBitmap?,
    aspectRatio: Float,
    quality: Float,
) {
    val blending = blendA != null && blendB != null
    val transition = rememberInfiniteTransition(label = "blink")
    val dissolve by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "dissolve",
    )
    val showFirst by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1400
                0f at 0
                0f at 560
                1f at 700
                1f at 1260
                0f at 1400
            },
        ),
        label = "blinkAlpha",
    )
    val firstVisible by remember { derivedStateOf { showFirst > 0.5f } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SeamTile("ENDE", last, aspectRatio, Neon.Cyan, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Neon.TextDim)
            SeamTile("ANFANG", first, aspectRatio, Neon.Magenta, Modifier.weight(1f))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
        ) {
            if (blending) {
                Image(blendA!!, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Image(
                    blendB!!,
                    null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = dissolve },
                )
            } else if (last != null && first != null) {
                Image(last, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                Image(
                    first,
                    null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = showFirst },
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                InfoPill(
                    if (blending) "ÜBERBLENDUNG · Mitte" else "BLINK-TEST · ${if (firstVisible) "Anfang" else "Ende"}",
                    color = Neon.Text,
                    modifier = Modifier.background(Color(0x88000000), RoundedCornerShape(50)),
                )
                InfoPill(
                    when {
                        blending && quality >= 0.85f -> "weicher Übergang"
                        blending && quality >= 0.6f -> "leichtes Doppelbild"
                        blending -> "deutliches Doppelbild"
                        quality >= 0.85f -> "springt kaum"
                        quality >= 0.6f -> "leichter Sprung"
                        else -> "sichtbarer Sprung"
                    },
                    color = Neon.quality(quality),
                    modifier = Modifier.background(Color(0x88000000), RoundedCornerShape(50)),
                )
            }
        }
    }
}

@Composable
private fun SeamTile(label: String, image: ImageBitmap?, aspectRatio: Float, accent: Color, modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio.coerceIn(0.5f, 2.4f))
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF15152A))
                .border(1.5.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
        ) {
            if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

/** Visual recipe of the output: intro, the loop blocks and the outro, proportional in time. */
@Composable
fun ChainBar(introUs: Long, loopUs: Long, repeats: Int, outroUs: Long, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "chain")
    val flow by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "flow")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp)),
    ) {
        val total = (introUs + loopUs * repeats + outroUs).coerceAtLeast(1L).toFloat()
        val w = size.width
        val h = size.height
        val gap = 2.dp.toPx()
        var x = 0f
        if (introUs > 0) {
            val iw = introUs / total * w
            drawRoundRect(Neon.Violet.copy(alpha = 0.7f), Offset(x, 0f), Size(iw - gap, h), CornerRadius(4.dp.toPx()))
            x += iw
        }
        val loopsWidth = loopUs * repeats / total * w
        val drawn = min(repeats, 60)
        val blockW = loopsWidth / drawn
        for (i in 0 until drawn) {
            val color = if (i % 2 == 0) Neon.Magenta else Neon.Cyan
            val active = ((flow * drawn).toInt() == i)
            drawRoundRect(
                color.copy(alpha = if (active) 1f else 0.65f),
                Offset(x + i * blockW, 0f),
                Size((blockW - if (drawn > 30) 0f else gap).coerceAtLeast(1f), h),
                CornerRadius(if (drawn > 30) 0f else 4.dp.toPx()),
            )
        }
        x += loopsWidth
        if (outroUs > 0) {
            drawRoundRect(Neon.Mint.copy(alpha = 0.7f), Offset(x + gap, 0f), Size(w - x - gap, h), CornerRadius(4.dp.toPx()))
        }
    }
}

@Composable
fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Neon.TextDim)
    }
}

@Composable
fun FrameNudger(label: String, valueText: String, accent: Color, onMinus: () -> Unit, onPlus: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Neon.Glass)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NudgeButton("−1", onMinus)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = accent)
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = Neon.Text,
                maxLines = 1,
                softWrap = false,
            )
        }
        NudgeButton("+1", onPlus)
    }
}

@Composable
private fun NudgeButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x1AFFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Neon.Text)
    }
}
