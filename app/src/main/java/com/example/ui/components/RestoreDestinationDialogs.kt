package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.PrivateGalleryApplication
import com.example.data.preferences.VaultRestoreMode
import com.example.data.repository.RestoreAlbumOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun RestoreDestinationDialog(
    selected: VaultRestoreMode?,
    includeAlwaysAsk: Boolean,
    onChoose: (VaultRestoreMode) -> Unit,
    onDismiss: () -> Unit
) {
    val choices = VaultRestoreMode.entries.filter { includeAlwaysAsk || it != VaultRestoreMode.ALWAYS_ASK }
    RestoreDialogFrame(onDismiss, scrollContent = true) {
        RestoreDialogHeader(
            icon = Icons.Default.Restore,
            title = if (includeAlwaysAsk) "Restore preference" else "Where should it go?",
            subtitle = if (includeAlwaysAsk) "Choose the default location for vault media"
                else "Select where your media will be restored"
        )
        Spacer(Modifier.size(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            choices.forEach { mode ->
                val (icon, description) = when (mode) {
                    VaultRestoreMode.ALWAYS_ASK -> Icons.AutoMirrored.Filled.HelpOutline to "Choose a location each time"
                    VaultRestoreMode.ORIGINAL_LOCATION -> Icons.Default.History to "Return to the folder recorded when locked"
                    VaultRestoreMode.SELECTED_ALBUM -> Icons.Default.Folder to "Pick an existing album on this device"
                    VaultRestoreMode.PRIVATE_GALLERY -> Icons.Default.PhotoLibrary to "Save in the PrivateGallery album"
                }
                DestinationOptionCard(
                    icon = icon,
                    title = mode.displayName,
                    description = description,
                    selected = selected == mode,
                    onClick = { onChoose(mode) }
                )
            }
        }
        RestoreDialogFooter(onDismiss)
    }
}

@Composable
fun RestoreAlbumPickerDialog(
    containsPhotos: Boolean,
    containsVideos: Boolean,
    onChoose: (RestoreAlbumOption) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val availableHeight = LocalConfiguration.current.screenHeightDp
    val albumListMaxHeight = (availableHeight - 300).coerceIn(120, 390).dp
    val app = context.applicationContext as PrivateGalleryApplication
    var albums by remember(containsPhotos, containsVideos) { mutableStateOf<List<RestoreAlbumOption>?>(null) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(containsPhotos, containsVideos) {
        albums = withContext(Dispatchers.IO) {
            app.mediaStoreRepository.getWritableRestoreAlbums(containsPhotos, containsVideos)
        }
    }
    val matching = remember(albums, search) {
        albums?.filter {
            search.isBlank() || it.displayName.contains(search.trim(), ignoreCase = true) ||
                it.relativePath.contains(search.trim(), ignoreCase = true)
        }
    }
    RestoreDialogFrame(onDismiss) {
        RestoreDialogHeader(
            icon = Icons.Default.Folder,
            title = "Choose an album",
            subtitle = "Restore media to a compatible on-device folder"
        )
        Spacer(Modifier.size(16.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search albums") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = when {
                albums == null -> "Finding albums…"
                else -> "${matching?.size ?: 0} available ${if (matching?.size == 1) "album" else "albums"}"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        when {
            albums == null -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
            matching.isNullOrEmpty() -> AlbumEmptyState(search.isNotBlank())
            else -> Column(
                modifier = Modifier.heightIn(max = albumListMaxHeight).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                matching.forEach { album ->
                    AlbumDestinationCard(album = album, onClick = { onChoose(album) })
                }
            }
        }
        Text(
            text = "Photos: DCIM or Pictures · Videos: DCIM, Pictures or Movies",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, start = 4.dp)
        )
        RestoreDialogFooter(onDismiss)
    }
}

@Composable
private fun RestoreDialogFrame(
    onDismiss: () -> Unit,
    scrollContent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp - 64).coerceAtLeast(260).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).imePadding(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.heightIn(max = maxDialogHeight)
                    .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                content = content
            )
        }
    }
}

@Composable
private fun RestoreDialogHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DestinationOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (selected) Icon(Icons.Default.Check, contentDescription = "Selected",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AlbumDestinationCard(album: RestoreAlbumOption, onClick: () -> Unit) {
    val context = LocalContext.current
    val preview = remember(album.coverUri) {
        ImageRequest.Builder(context).data(album.coverUri).size(112, 112).crossfade(false).build()
    }
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            if (album.coverUri != null) {
                AsyncImage(
                    model = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(album.displayName, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(album.relativePath, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        ) {
            Text("${album.itemCount}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
        }
    }
}

@Composable
private fun AlbumEmptyState(hasSearch: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.Folder, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(36.dp))
        Text(if (hasSearch) "No matching albums" else "No compatible albums found",
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(if (hasSearch) "Try another album name" else "Create a folder in DCIM or Pictures, then try again",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RestoreDialogFooter(onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}
