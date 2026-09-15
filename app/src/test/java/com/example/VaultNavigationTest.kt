package com.example

import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.entity.VaultItemEntity
import com.example.ui.vault.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PrivateGalleryApplication::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VaultNavigationTest {
    @Test fun `folder switching and immediate viewer selection use current items`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val app = ApplicationProvider.getApplicationContext<PrivateGalleryApplication>()
        val firstFolder = app.vaultRepository.createVault("First test folder")
        val secondFolder = app.vaultRepository.createVault("Second test folder")
        suspend fun insert(folder: Long, name: String): Long = app.database.vaultItemDao().insertItem(
            VaultItemEntity(vaultId = folder, encryptedFileIdentifier = name,
                originalFileName = "$name.jpg", mimeType = "image/jpeg", mediaType = 1,
                dateTaken = 0, originalSize = 12))
        val firstId = insert(firstFolder, "first")
        val secondId = insert(secondFolder, "second")
        val vm = VaultViewModel(app)
        val store = ViewModelStore().apply { put("vault", vm) }
        val observer = launch(Dispatchers.Unconfined) { vm.uiState.collect { } }
        try {
            withTimeout(10_000) {
                vm.selectVaultFolder(firstFolder)
                vm.uiState.first { it.itemsInCurrentVault.singleOrNull()?.id == firstId }
                vm.selectVaultFolder(secondFolder)
                vm.uiState.first { it.itemsInCurrentVault.singleOrNull()?.id == secondId }
                vm.clearSelection()
                vm.toggleItemSelection(secondId)
                // Exactly the viewer's sequence: action immediately after selecting its item.
                vm.deleteSelectedItems()
                val trash = app.vaultRepository.getTrashItems().first { it.any { item -> item.id == secondId } }
                assertTrue(trash.single { it.id == secondId }.isDeleted)
                assertFalse(app.database.vaultItemDao().getItemById(firstId)!!.isDeleted)
                vm.openTrash()
                vm.uiState.first { it.isInTrashView && it.trashItems.isNotEmpty() }
                vm.selectAll()
                assertEquals(setOf(secondId), vm.uiState.first { it.selectedItemIds.isNotEmpty() }.selectedItemIds)
            }
        } finally {
            observer.cancelAndJoin()
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
