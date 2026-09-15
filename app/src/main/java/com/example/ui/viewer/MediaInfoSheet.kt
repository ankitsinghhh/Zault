package com.example.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    dateTaken: Long,
    width: Int,
    height: Int,
    durationMs: Long,
    albumName: String,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            InfoRow(
                    icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                label = "File Name",
                value = fileName
            )
            HorizontalDivider(
                color = DividerDefaults.color.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            InfoRow(
                icon = Icons.Default.CalendarToday,
                label = "Date Taken",
                value = SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(dateTaken))
            )
            HorizontalDivider(
                color = DividerDefaults.color.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            if (width > 0 && height > 0) {
                InfoRow(
                    icon = Icons.Default.AspectRatio,
                    label = "Resolution",
                    value = "$width × $height (${formatMegapixels(width, height)})"
                )
                HorizontalDivider(
                    color = DividerDefaults.color.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            InfoRow(
                icon = Icons.Default.Image,
                label = "File Size & Type",
                value = "${formatFileSize(sizeBytes)} • $mimeType"
            )
            HorizontalDivider(
                color = DividerDefaults.color.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 10.dp)
            )

            if (durationMs > 0) {
                InfoRow(
                    icon = Icons.Default.Timer,
                    label = "Duration",
                    value = formatDuration(durationMs)
                )
                HorizontalDivider(
                    color = DividerDefaults.color.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }

            InfoRow(
                icon = Icons.Default.Folder,
                label = "Album",
                value = albumName
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(Locale.getDefault(), "%.1f %s", value, units[digitGroups])
}

fun formatMegapixels(width: Int, height: Int): String {
    val mp = (width * height).toDouble() / 1_000_000.0
    return String.format(Locale.getDefault(), "%.1f MP", mp)
}
