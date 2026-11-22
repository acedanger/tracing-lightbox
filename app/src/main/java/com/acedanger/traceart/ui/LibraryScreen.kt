package com.acedanger.traceart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
) {
    val tags = remember(pictures) { allTags(pictures) }
    val shown = remember(pictures, selectedTag) { filterByTag(pictures, selectedTag) }
    Column(Modifier.fillMaxSize().background(Color(0xFFFFF3E0)).safeDrawingPadding()) {
        Text(
            "Pick a picture",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, top = 32.dp, bottom = 8.dp),
        )
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
