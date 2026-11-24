package com.example.cloble.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.cloble.ui.theme.CloBleColors
import kotlinx.coroutines.launch
import kotlin.math.abs

data class LabeledPhoto(
    val uri: String,
    val label: String = ""
)

val LabeledPhotoListSaver: Saver<List<LabeledPhoto>, Any> = listSaver(
    save = { photos -> photos.flatMap { listOf(it.uri, it.label) } },
    restore = { flat ->
        flat.chunked(2).mapNotNull { pair ->
            val uri = pair.getOrNull(0) ?: return@mapNotNull null
            LabeledPhoto(uri = uri, label = pair.getOrElse(1) { "" })
        }
    }
)

val NullableLabeledPhotoSaver: Saver<LabeledPhoto?, Any> = listSaver(
    save = { photo ->
        if (photo == null) emptyList() else listOf(photo.uri, photo.label)
    },
    restore = { saved ->
        if (saved.size < 2) null else LabeledPhoto(uri = saved[0], label = saved[1])
    }
)

@Composable
fun CloBleFullscreenImageViewer(
    photos: List<LabeledPhoto>,
    initialIndex: Int = 0,
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    val safeInitial = initialIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { photos.size })
    val coroutineScope = rememberCoroutineScope()

    var currentScale by remember { mutableFloatStateOf(1f) }
    var currentOffset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
        currentOffset = Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CloBleColors.DarkScrim)
                .testTag("fullscreen_image_viewer")
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.02f,
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportSize = it }
            ) { page ->
                val photo = photos[page]
                val pageScale = if (pagerState.currentPage == page) currentScale else 1f
                val pageOffset = if (pagerState.currentPage == page) currentOffset else Offset.Zero

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (currentScale > 1.2f) {
                                        currentScale = 1f
                                        currentOffset = Offset.Zero
                                    } else {
                                        currentScale = 2.5f
                                    }
                                }
                            )
                        }
                        .pointerInput(page, viewportSize) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                var pastTouchSlop = false
                                var lockedToPanOrZoom = false
                                var zoom = 1f
                                var pan = Offset.Zero
                                val touchSlop = viewConfiguration.touchSlop

                                do {
                                    val event = awaitPointerEvent()
                                    val activeCount = event.changes.count { it.pressed }
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    if (!pastTouchSlop) {
                                        zoom *= zoomChange
                                        pan += panChange
                                        val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = abs(1 - zoom) * centroidSize
                                        val panMotion = pan.getDistance()
                                        if (zoomMotion > touchSlop || panMotion > touchSlop || activeCount > 1) {
                                            pastTouchSlop = true
                                            lockedToPanOrZoom = activeCount > 1 || currentScale > 1.02f || abs(1 - zoomChange) > 0.01f
                                        }
                                    }

                                    if (pastTouchSlop && (activeCount > 1 || currentScale > 1.02f || lockedToPanOrZoom)) {
                                        val newScale = (currentScale * zoomChange).coerceIn(1f, 4f)
                                        currentScale = newScale
                                        if (newScale <= 1.02f) {
                                            currentOffset = Offset.Zero
                                        } else {
                                            val maxX = (viewportSize.width * (newScale - 1f)) / 2f
                                            val maxY = (viewportSize.height * (newScale - 1f)) / 2f
                                            currentOffset = Offset(
                                                x = (currentOffset.x + panChange.x).coerceIn(-maxX, maxX),
                                                y = (currentOffset.y + panChange.y).coerceIn(-maxY, maxY)
                                            )
                                        }
                                        event.changes.forEach { if (it.pressed) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.label.ifBlank { "Foto pakaian" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = pageScale,
                                scaleY = pageScale,
                                translationX = pageOffset.x,
                                translationY = pageOffset.y
                            )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                CloBleColors.DarkScrim.copy(alpha = 0.82f),
                                Color.Transparent
                            )
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val activePhoto = photos[pagerState.currentPage]
                    if (activePhoto.label.isNotBlank()) {
                        Text(
                            text = activePhoto.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (photos.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${photos.size} · Cubit untuk perbesar",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    } else {
                        Text(
                            text = "Ketuk 2x atau cubit untuk perbesar",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f))
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Tutup tampilan gambar penuh",
                            onClick = onDismiss
                        )
                        .testTag("btn_close_fullscreen_viewer"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CloBleIcons.XClose,
                        contentDescription = "Tutup",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (photos.size > 1) {
                Surface(
                    color = CloBleColors.DarkScrim.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                    ) {
                        itemsIndexed(photos) { index, item ->
                            val isActive = pagerState.currentPage == index
                            val borderColor = if (isActive) CloBleColors.PrimarySoft else Color.White.copy(alpha = 0.35f)
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        border = BorderStroke(if (isActive) 2.dp else 1.dp, borderColor),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = item.label.ifBlank { "Thumbnail ${index + 1}" },
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
