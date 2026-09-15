package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.example.data.database.VaultDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.MediaStoreRepository
import com.example.data.repository.VaultRepository
import com.example.data.repository.VaultTransferManager
import com.example.data.security.BiometricAuthenticator
import com.example.data.security.CryptoManager
import com.example.data.security.VaultSessionManager
import com.example.data.security.VaultStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PrivateGalleryApplication : Application(), ImageLoaderFactory {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var cryptoManager: CryptoManager
        private set
    lateinit var storageManager: VaultStorageManager
        private set
    lateinit var database: VaultDatabase
        private set
    lateinit var sessionManager: VaultSessionManager
        private set
    lateinit var biometricAuthenticator: BiometricAuthenticator
        private set
    lateinit var preferencesRepository: UserPreferencesRepository
        private set
    lateinit var mediaStoreRepository: MediaStoreRepository
        private set
    lateinit var vaultRepository: VaultRepository
        private set
    lateinit var vaultTransferManager: VaultTransferManager
        private set

    override fun onCreate() {
        super.onCreate()
        storageManager = VaultStorageManager(this)
        cryptoManager = CryptoManager(this)
        database = VaultDatabase.getInstance(this)
        sessionManager = VaultSessionManager(storageManager)
        registerActivityLifecycleCallbacks(sessionManager)
        biometricAuthenticator = BiometricAuthenticator(this)
        preferencesRepository = UserPreferencesRepository(this)
        applicationScope.launch {
            preferencesRepository.autoLockTimeoutFlow.collect { sessionManager.setAutoLockTimeout(it) }
        }
        applicationScope.launch { preferencesRepository.migrateLegacyAppLockPin() }
        mediaStoreRepository = MediaStoreRepository(this)
        vaultRepository = VaultRepository(database, storageManager)
        vaultTransferManager = VaultTransferManager(
            context = this,
            database = database,
            cryptoManager = cryptoManager,
            storageManager = storageManager,
            mediaStoreRepository = mediaStoreRepository
        )
        sessionManager.addOnLockListener(vaultTransferManager::clearSensitiveMemoryCaches)
        sessionManager.addOnLockListener(cryptoManager::clearCachedDataKey)

        // Resolve any durable move journal left behind by process death before
        // removing unrelated temporary files.
        applicationScope.launch { vaultTransferManager.recoverInterruptedTransfers() }
        storageManager.cleanStagingDirectory()
        storageManager.cleanViewerTemp()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(false)
        .build()
}
