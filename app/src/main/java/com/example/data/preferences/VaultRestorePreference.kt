package com.example.data.preferences

enum class VaultRestoreMode(val displayName: String) {
    ALWAYS_ASK("Always ask"),
    ORIGINAL_LOCATION("Original location"),
    SELECTED_ALBUM("Selected album"),
    PRIVATE_GALLERY("PrivateGallery album");

    companion object {
        fun fromOrdinal(value: Int): VaultRestoreMode = entries.getOrNull(value) ?: PRIVATE_GALLERY
    }
}

data class VaultRestorePreference(
    val mode: VaultRestoreMode = VaultRestoreMode.PRIVATE_GALLERY,
    val albumRelativePath: String = ""
)

sealed interface VaultRestoreDestination {
    data object OriginalLocation : VaultRestoreDestination
    data class Album(val relativePath: String) : VaultRestoreDestination
    data object PrivateGallery : VaultRestoreDestination
}
