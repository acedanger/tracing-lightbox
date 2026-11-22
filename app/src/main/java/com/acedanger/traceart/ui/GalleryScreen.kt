package com.acedanger.traceart.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.acedanger.traceart.library.Picture
import com.acedanger.traceart.library.assetUri
import com.acedanger.traceart.photos.DrawingPhoto
import java.text.DateFormat
import java.util.Date

private val Cream = Color(0xFFFFF3E0)
private val Pink = Color(0xFFFF4F8B)

/** Her own collection: every drawing photo Trace Art has taken, newest first. */
@Composable
fun GalleryScreen(
    photos: List<DrawingPhoto>,
    pictures: List<Picture>,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onDelete: (DrawingPhoto) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    val index = open
    if (index != null && photos.isNotEmpty()) {
        PhotoViewer(
            photos = photos,
            pictures = pictures,
            start = index.coerceIn(0, photos.lastIndex),
            onClose = { open = null },
            onDelete = onDelete,
        )
        return
    }
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().background(Cream).safeDrawingPadding()) {
        FlowRow(
            itemVerticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) { Text("‹  Back", fontSize = 22.sp) }
            Column(Modifier.weight(1f).widthIn(min = 240.dp)) {
                Text("My Drawings", fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold)
                if (photos.isNotEmpty()) {
                    Text(
                        if (photos.size == 1) "1 drawing" else "${photos.size} drawings",
                        fontSize = 20.sp,
                        color = Color.DarkGray,
                    )
                }
            }
            Button(
                onClick = onTakePhoto,
                colors = ButtonDefaults.buttonColors(containerColor = Pink),
                modifier = Modifier.heightIn(min = 64.dp),
            ) { Text("📷  Take a photo", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        }

        if (photos.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier.fillMaxSize().padding(32.dp),
            ) {
                Text("🖼️", fontSize = 80.sp)
                Text("No drawings here yet", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text(
                    "When you finish tracing, tap 📷 Photo to save your drawing here.",
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(photos, key = { _, p -> p.uri.toString() }) { i, photo ->
                val traced = pictures.firstOrNull { it.id == photo.pictureId }
                Card(onClick = { open = i }, shape = RoundedCornerShape(24.dp)) {
                    Box {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = "Drawing from ${shortDate(photo.takenMs)}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.8f).background(Color.White),
                        )
                        if (traced != null) {
                            AsyncImage(
                                model = traced.assetUri(),
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(64.dp)
                                    .clip(RoundedCornerShape(14.dp)).background(Color.White).padding(4.dp),
                            )
                        }
                    }
                    Text(
                        traced?.name ?: "My drawing",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp),
                    )
                    Text(
                        shortDate(photo.takenMs),
                        fontSize = 16.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(start = 14.dp, bottom = 12.dp, end = 14.dp),
                    )
                }
            }
        }
    }
}

/** Full-screen, swipeable: made for showing Mom. "Show outline" puts the traced picture beside it. */
@Composable
private fun PhotoViewer(
    photos: List<DrawingPhoto>,
    pictures: List<Picture>,
    start: Int,
    onClose: () -> Unit,
    onDelete: (DrawingPhoto) -> Unit,
) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start) { photos.size }
    var compare by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val current = photos.getOrNull(pager.currentPage)
    val traced = pictures.firstOrNull { it.id == current?.pictureId }

    // If the last photo was deleted, there's nothing left to show.
    LaunchedEffect(photos.size) { if (photos.isEmpty()) onClose() }

    Box(Modifier.fillMaxSize().background(Color(0xFF1B1B1F))) {
        HorizontalPager(pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val photo = photos[page]
            val pic = pictures.firstOrNull { it.id == photo.pictureId }
            BoxWithConstraints(
                Modifier.fillMaxSize().safeDrawingPadding().padding(top = 96.dp, bottom = 110.dp, start = 24.dp, end = 24.dp),
            ) {
                val outline: @Composable (Modifier) -> Unit = { m ->
                    Box(m, contentAlignment = Alignment.Center) {
                        AsyncImage(
                            model = pic?.assetUri(),
                            contentDescription = "The outline",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                                .background(Color.White).padding(16.dp),
                        )
                    }
                }
                val drawing: @Composable (Modifier) -> Unit = { m ->
                    FittedPhoto(photo.uri, "Your drawing", m, shadow = false)
                }
                when {
                    !compare || pic == null -> drawing(Modifier.fillMaxSize())
                    // Portrait tablet: outline on top, her drawing below.
                    maxHeight > maxWidth -> Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        outline(Modifier.weight(0.4f).fillMaxWidth())
                        drawing(Modifier.weight(0.6f).fillMaxWidth())
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        outline(Modifier.weight(0.45f).fillMaxSize())
                        drawing(Modifier.weight(0.55f).fillMaxSize())
                    }
                }
            }
        }

        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
        ) {
            FilledTonalButton(onClick = onClose, modifier = Modifier.height(56.dp)) { Text("Back", fontSize = 20.sp) }
            Column(Modifier.weight(1f)) {
                Text(traced?.name ?: "My drawing", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                current?.let { Text(longDate(it.takenMs), color = Color(0xFFCCCCCC), fontSize = 18.sp) }
            }
            if (photos.size > 1) {
                Text("${pager.currentPage + 1} / ${photos.size}", color = Color.White, fontSize = 20.sp)
            }
        }

        // Bottom bar
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(20.dp),
        ) {
            if (traced != null) {
                Button(
                    onClick = { compare = !compare },
                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                    modifier = Modifier.height(64.dp),
                ) { Text(if (compare) "Hide outline" else "Show outline", fontSize = 22.sp) }
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.height(64.dp)) {
                Text("Delete", fontSize = 22.sp, color = Color.White)
            }
        }
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this drawing?", fontSize = 26.sp) },
            text = { Text("It will be gone from My Drawings and from the tablet's Gallery.", fontSize = 20.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(current)
                }) { Text("Delete", fontSize = 20.sp, color = Color(0xFFC62828)) }
            },
            dismissButton = {
                Button(onClick = { confirmDelete = false }) { Text("Keep it", fontSize = 20.sp) }
            },
        )
    }
}

private fun shortDate(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
private fun longDate(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(Date(ms))
