package com.acedanger.traceart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.acedanger.traceart.library.Picture
import com.acedanger.traceart.library.allTags
import com.acedanger.traceart.library.assetUri
import com.acedanger.traceart.library.filterByTag

@Composable
private fun TagChip(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("$label  $count", fontSize = 22.sp, modifier = Modifier.padding(vertical = 10.dp)) },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.heightIn(min = 56.dp),
    )
}

@Composable
fun LibraryScreen(
    pictures: List<Picture>,
    onOpen: (Picture) -> Unit,
    selectedTag: String? = null,
    onSelectTag: (String?) -> Unit = {},
    version: String = "",
    updateAvailable: Boolean = false,
    onAbout: () -> Unit = {},
    onMyDrawings: () -> Unit = {},
) {
    val tags = remember(pictures) { allTags(pictures) }
    val shown = remember(pictures, selectedTag) { filterByTag(pictures, selectedTag) }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF3E0)).safeDrawingPadding()) {
        // Title and buttons share a row on a tablet; on a narrow screen the buttons wrap below.
        FlowRow(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 24.dp, top = 32.dp, bottom = 8.dp),
        ) {
            Text(
                "Pick a picture",
                fontSize = 40.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f).widthIn(min = 300.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onMyDrawings,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4F8B)),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.padding(end = 12.dp).heightIn(min = 56.dp),
                ) { Text("🖼️  My Drawings", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                VersionChip(version, updateAvailable, onAbout)
            }
        }
        if (tags.isNotEmpty()) {
            // One tag at a time; tapping the selected tag again (or "All") shows everything.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            ) {
                item(key = "all") {
                    TagChip("All", pictures.size, selectedTag == null) { onSelectTag(null) }
                }
                rowItems(tags, key = { it }) { tag ->
                    val count = pictures.count { tag in it.tags }
                    TagChip(tag.replaceFirstChar { it.uppercase() }, count, selectedTag == tag) {
                        onSelectTag(if (selectedTag == tag) null else tag)
                    }
                }
            }
        }
        val pictures = shown
        if (pictures.isEmpty()) {
            Text("No pictures yet.", fontSize = 24.sp, modifier = Modifier.padding(32.dp))
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(pictures, key = { it.id }) { picture ->
                Card(
                    onClick = { onOpen(picture) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = picture.assetUri(),
                            contentDescription = picture.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White),
                        )
                        Text(
                            picture.name,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** "v1.6 ⓘ" in the corner; opens About. An orange ↑ dot means a newer version is on GitHub. */
@Composable
private fun VersionChip(version: String, updateAvailable: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.heightIn(min = 56.dp).semantics {
            contentDescription = "About, version $version" + if (updateAvailable) ", update available" else ""
        },
    ) {
        Text("v$version", fontSize = 20.sp)
        if (updateAvailable) {
            Spacer(Modifier.width(10.dp))
            androidx.compose.foundation.layout.Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(28.dp).background(UpdateOrange, CircleShape),
            ) { Text("↑", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(10.dp))
        Text("ⓘ", fontSize = 22.sp)
    }
}
