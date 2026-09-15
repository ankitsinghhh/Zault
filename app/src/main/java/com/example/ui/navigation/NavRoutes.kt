package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Photos : Screen("photos")
    data object Albums : Screen("albums")
    data object Vault : Screen("vault")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object Favorites : Screen("favorites")
    data object Trash : Screen("trash")
    data object Onboarding : Screen("onboarding")

    data object AlbumDetail : Screen("album_detail/{bucketId}/{albumName}") {
        fun createRoute(bucketId: Long, albumName: String) = "album_detail/$bucketId/${android.net.Uri.encode(albumName)}"
    }

    data object VaultFolderDetail : Screen("vault_detail/{vaultId}/{vaultName}") {
        fun createRoute(vaultId: Long, vaultName: String) = "vault_detail/$vaultId/${android.net.Uri.encode(vaultName)}"
    }

    data object MediaViewer : Screen("media_viewer/{source}/{initialIndex}?bucketId={bucketId}&vaultId={vaultId}&mediaId={mediaId}") {
        fun createRoute(
            source: String,
            initialIndex: Int,
            bucketId: Long? = null,
            vaultId: Long? = null,
            mediaId: Long? = null
        ): String {
            val base = "media_viewer/$source/$initialIndex"
            val params = mutableListOf<String>()
            if (bucketId != null) params.add("bucketId=$bucketId")
            if (vaultId != null) params.add("vaultId=$vaultId")
            if (mediaId != null) params.add("mediaId=$mediaId")
            return if (params.isNotEmpty()) "$base?${params.joinToString("&")}" else base
        }
    }
}
