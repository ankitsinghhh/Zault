package com.example.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.PrivateGalleryApplication
import com.example.R
import com.example.data.security.AutoLockTimeout
import com.example.data.preferences.VaultRestoreMode
import com.example.data.preferences.VaultRestorePreference
import com.example.ui.components.RestoreDestinationDialog
import com.example.ui.components.RestoreAlbumPickerDialog
import com.example.ui.components.SetPinDialog
import com.example.ui.theme.GalleryPalettes
import com.example.ui.viewer.formatFileSize
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as PrivateGalleryApplication
    val scope = rememberCoroutineScope()

    val autoLockTimeout by app.preferencesRepository.autoLockTimeoutFlow.collectAsState(initial = AutoLockTimeout.IMMEDIATELY)
    val secureScreen by app.preferencesRepository.secureScreenFlow.collectAsState(initial = true)
    val themeMode by app.preferencesRepository.themeModeFlow.collectAsState(initial = 0)
    val themePalette by app.preferencesRepository.themePaletteFlow.collectAsState(initial = 0)
    val blurVaultThumbnails by app.preferencesRepository.blurVaultThumbnailsFlow.collectAsState(initial = false)
    val blurRadius by app.preferencesRepository.blurRadiusFlow.collectAsState(initial = 22f)
    val viewerDetails by app.preferencesRepository.viewerDetailsFlow.collectAsState(initial = false)
    val appLockEnabled by app.preferencesRepository.appLockEnabledFlow.collectAsState(initial = false)
    val appLockPin by app.preferencesRepository.appLockPinFlow.collectAsState(initial = "")
    val totalVaultBytes by app.vaultRepository.getTotalStorage().collectAsState(initial = 0L)
    val restorePreference by app.preferencesRepository.vaultRestorePreferenceFlow.collectAsState(initial = VaultRestorePreference())

    var showAutoLockDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showRestoreModeDialog by remember { mutableStateOf(false) }
    var showRestoreAlbumDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // App Lock & Access Section
            SectionHeader(title = "App Lock & Security")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // App Lock Switch
                    SettingSwitchRow(
                        icon = Icons.Default.Password,
                        title = "App Lock",
                        subtitle = if (appLockEnabled) "App is protected with 4-digit PIN" else "Require 4-digit PIN when opening app",
                        checked = appLockEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (appLockPin.isNotBlank()) {
                                    scope.launch { app.preferencesRepository.setAppLockEnabled(true) }
                                } else {
                                    showSetPinDialog = true
                                }
                            } else {
                                scope.launch { app.preferencesRepository.setAppLockEnabled(false) }
                            }
                        },
                        modifier = Modifier.testTag("app_lock_switch")
                    )

                    if (appLockEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                        SettingClickableRow(
                            icon = Icons.Default.Pin,
                            title = "Change 4-Digit PIN",
                            value = "Update lock PIN",
                            onClick = { showSetPinDialog = true },
                            modifier = Modifier.testTag("change_pin_row")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Auto Lock
                    SettingClickableRow(
                        icon = Icons.Default.LockClock,
                        title = "Auto-lock Vault",
                        value = autoLockTimeout.displayName,
                        onClick = { showAutoLockDialog = true },
                        modifier = Modifier.testTag("auto_lock_setting_row")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Allow Screenshots Toggle (Default: disabled/blocked)
                    val allowScreenshots = !secureScreen
                    SettingSwitchRow(
                        icon = Icons.Default.ScreenLockPortrait,
                        title = "Allow Screenshots",
                        subtitle = if (allowScreenshots) "Screenshots & screen capture are allowed" else "Screenshots are blocked by security policy (Default: Disabled)",
                        checked = allowScreenshots,
                        onCheckedChange = { allowed ->
                            scope.launch { app.preferencesRepository.setSecureScreen(!allowed) }
                        },
                        modifier = Modifier.testTag("allow_screenshots_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Vault Display & Privacy Section
            SectionHeader(title = "Media Display")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingSwitchRow(
                        icon = if (blurVaultThumbnails) Icons.Default.VisibilityOff else Icons.Default.PhotoLibrary,
                        title = "Blur Media Thumbnails",
                        subtitle = if (blurVaultThumbnails) "Lock icon overlayed on blurred photo (Click to open)" else "Photos appear in gallery view with normal previews",
                        checked = blurVaultThumbnails,
                        onCheckedChange = {
                            scope.launch { app.preferencesRepository.setBlurVaultThumbnails(it) }
                        },
                        modifier = Modifier.testTag("blur_vault_thumbnails_switch")
                    )

                    if (blurVaultThumbnails) {
                        var blurSliderValue by remember(blurRadius) { mutableStateOf(blurRadius) }
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Blur Level",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${blurRadius.roundToInt()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Slider(
                                value = blurSliderValue,
                                onValueChange = { blurSliderValue = it },
                                onValueChangeFinished = {
                                    scope.launch { app.preferencesRepository.setBlurRadius(blurSliderValue) }
                                },
                                valueRange = 6f..36f,
                                steps = 14,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("blurness_level_slider")
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingSwitchRow(
                        icon = Icons.Default.Info,
                        title = "Viewer Details",
                        subtitle = "Show file size and resolution over photo and video previews",
                        checked = viewerDetails,
                        onCheckedChange = { enabled ->
                            scope.launch { app.preferencesRepository.setViewerDetails(enabled) }
                        },
                        modifier = Modifier.testTag("viewer_details_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Appearance & Theme Section
            SectionHeader(title = "Appearance & Theme")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val themeTitle = when (themeMode) {
                        1 -> "Light theme"
                        2 -> "Dark theme"
                        else -> "System default"
                    }

                    SettingClickableRow(
                        icon = Icons.Default.Brightness4,
                        title = "App Theme",
                        value = themeTitle,
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.testTag("theme_setting_row")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingClickableRow(
                        icon = Icons.Default.Palette,
                        title = "Color Palette",
                        value = GalleryPalettes.first { it.id == themePalette }.name,
                        onClick = { showPaletteDialog = true },
                        modifier = Modifier.testTag("theme_palette_setting")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage Section Header
            SectionHeader(title = "Storage & Isolation")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingClickableRow(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Vault restore destination",
                        value = if (restorePreference.mode == VaultRestoreMode.SELECTED_ALBUM)
                            restorePreference.albumRelativePath.ifBlank { "Choose album" }
                        else restorePreference.mode.displayName,
                        onClick = { showRestoreModeDialog = true },
                        modifier = Modifier.testTag("vault_restore_destination_setting")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingInfoRow(
                        icon = Icons.Default.PieChart,
                        title = "Encrypted Vault Storage",
                        subtitle = "${formatFileSize(totalVaultBytes)} used by private media"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingInfoRow(
                        icon = Icons.Default.Memory,
                        title = "Device Free Storage",
                        subtitle = "${formatFileSize(app.storageManager.getAvailableStorageBytes())} available"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    SettingInfoRow(
                        icon = Icons.Default.CloudOff,
                        title = "Zero Cloud Backup",
                        subtitle = "Locked Folders are explicitly excluded from Android Backup and Google Drive syncing."
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // About
            SectionHeader(title = "About")

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_zault_icon),
                        contentDescription = "Zault Icon",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Zault",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Version 1.0.0 • Keystore-protected AES-256 vault, PIN app lock, privacy blurring & biometric security.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }

    // Auto-lock Dialog
    if (showRestoreModeDialog) {
        RestoreDestinationDialog(
            selected = restorePreference.mode,
            includeAlwaysAsk = true,
            onChoose = { mode ->
                showRestoreModeDialog = false
                if (mode == VaultRestoreMode.SELECTED_ALBUM) {
                    showRestoreAlbumDialog = true
                } else {
                    scope.launch { app.preferencesRepository.setVaultRestorePreference(VaultRestorePreference(mode)) }
                }
            },
            onDismiss = { showRestoreModeDialog = false }
        )
    }
    if (showRestoreAlbumDialog) {
        RestoreAlbumPickerDialog(
            containsPhotos = true,
            containsVideos = true,
            onChoose = { album ->
                showRestoreAlbumDialog = false
                scope.launch {
                    app.preferencesRepository.setVaultRestorePreference(
                        VaultRestorePreference(VaultRestoreMode.SELECTED_ALBUM, album.relativePath)
                    )
                }
            },
            onDismiss = { showRestoreAlbumDialog = false }
        )
    }

    if (showAutoLockDialog) {
        AlertDialog(
            onDismissRequest = { showAutoLockDialog = false },
            title = { Text("Auto-lock Vault") },
            text = {
                Column {
                    AutoLockTimeout.values().forEach { timeout ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        app.preferencesRepository.setAutoLockTimeout(timeout)
                                        app.sessionManager.setAutoLockTimeout(timeout)
                                        showAutoLockDialog = false
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = autoLockTimeout == timeout,
                                onClick = {
                                    scope.launch {
                                        app.preferencesRepository.setAutoLockTimeout(timeout)
                                        app.sessionManager.setAutoLockTimeout(timeout)
                                        showAutoLockDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(timeout.displayName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAutoLockDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Theme Picker Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme") },
            text = {
                Column {
                    listOf(
                        0 to "System default",
                        1 to "Light theme",
                        2 to "Dark theme"
                    ).forEach { (mode, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        app.preferencesRepository.setThemeMode(mode)
                                        showThemeDialog = false
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    scope.launch {
                                        app.preferencesRepository.setThemeMode(mode)
                                        showThemeDialog = false
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (showPaletteDialog) {
        AlertDialog(
            onDismissRequest = { showPaletteDialog = false },
            title = { Text("Choose Color Palette") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    GalleryPalettes.forEach { palette ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch { app.preferencesRepository.setThemePalette(palette.id) }
                                    showPaletteDialog = false
                                }
                                .background(
                                    if (themePalette == palette.id) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(palette.preview)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(palette.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            RadioButton(
                                selected = themePalette == palette.id,
                                onClick = {
                                    scope.launch { app.preferencesRepository.setThemePalette(palette.id) }
                                    showPaletteDialog = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPaletteDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    // Set 4-Digit PIN Dialog
    if (showSetPinDialog) {
        SetPinDialog(
            onDismiss = { showSetPinDialog = false },
            onPinSet = { pin ->
                scope.launch {
                    app.preferencesRepository.setAppLockPin(pin)
                    app.preferencesRepository.setAppLockEnabled(true)
                    showSetPinDialog = false
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingInfoRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingClickableRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
