package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.security.AutoLockTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.util.Base64

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        private val KEY_AUTO_LOCK = intPreferencesKey("auto_lock_timeout")
        private val KEY_SECURE_SCREEN = booleanPreferencesKey("secure_screen_flag")
        private val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
        private val KEY_VAULT_EXPLANATION_DISMISSED = booleanPreferencesKey("vault_explanation_dismissed")
        private val KEY_THEME_MODE = intPreferencesKey("theme_mode") // 0: System, 1: Light, 2: Dark
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_THEME_PALETTE = intPreferencesKey("theme_palette")
        private val KEY_BLUR_VAULT_THUMBNAILS = booleanPreferencesKey("blur_vault_thumbnails")
        private val KEY_BLUR_RADIUS = androidx.datastore.preferences.core.floatPreferencesKey("blur_radius")
        private val KEY_VIEWER_DETAILS = booleanPreferencesKey("viewer_details_overlay")
        private val KEY_APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        private val KEY_APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        private val KEY_VAULT_RESTORE_MODE = intPreferencesKey("vault_restore_mode")
        private val KEY_VAULT_RESTORE_ALBUM = stringPreferencesKey("vault_restore_album")
    }

    val gridColumnsFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_GRID_COLUMNS] ?: 3).coerceIn(2, 5)
    }

    val autoLockTimeoutFlow: Flow<AutoLockTimeout> = context.dataStore.data.map { prefs ->
        val ord = prefs[KEY_AUTO_LOCK] ?: 0
        AutoLockTimeout.fromOrdinal(ord)
    }

    val secureScreenFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SECURE_SCREEN] ?: true
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ONBOARDING_DONE] ?: false
    }

    val vaultExplanationDismissedFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VAULT_EXPLANATION_DISMISSED] ?: false
    }

    val themeModeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_THEME_MODE] ?: 0).coerceIn(0, 2)
    }

    val dynamicColorFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DYNAMIC_COLOR] ?: true
    }

    val themePaletteFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[KEY_THEME_PALETTE] ?: 0).coerceIn(0, 11)
    }

    val blurVaultThumbnailsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BLUR_VAULT_THUMBNAILS] ?: false
    }

    val blurRadiusFlow: Flow<Float> = context.dataStore.data.map { prefs ->
        (prefs[KEY_BLUR_RADIUS] ?: 22f).takeIf { it.isFinite() }?.coerceIn(6f, 36f) ?: 22f
    }

    val viewerDetailsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_VIEWER_DETAILS] ?: false
    }

    val appLockEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_ENABLED] ?: false
    }

    val appLockConfigFlow: Flow<Pair<Boolean, String>> = context.dataStore.data.map { prefs ->
        (prefs[KEY_APP_LOCK_ENABLED] ?: false) to (prefs[KEY_APP_LOCK_PIN] ?: "")
    }

    val appLockPinFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_APP_LOCK_PIN] ?: ""
    }

    val vaultRestorePreferenceFlow: Flow<VaultRestorePreference> = context.dataStore.data.map { prefs ->
        VaultRestorePreference(
            mode = VaultRestoreMode.fromOrdinal(
                prefs[KEY_VAULT_RESTORE_MODE] ?: VaultRestoreMode.PRIVATE_GALLERY.ordinal
            ),
            albumRelativePath = prefs[KEY_VAULT_RESTORE_ALBUM] ?: ""
        )
    }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_GRID_COLUMNS] = columns.coerceIn(2, 5) }
    }

    suspend fun setAutoLockTimeout(timeout: AutoLockTimeout) {
        context.dataStore.edit { prefs -> prefs[KEY_AUTO_LOCK] = timeout.ordinal }
    }

    suspend fun setSecureScreen(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SECURE_SCREEN] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_ONBOARDING_DONE] = completed }
    }

    suspend fun setVaultExplanationDismissed(dismissed: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_VAULT_EXPLANATION_DISMISSED] = dismissed }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.coerceIn(0, 2) }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setThemePalette(palette: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_PALETTE] = palette.coerceIn(0, 11) }
    }

    suspend fun setBlurVaultThumbnails(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_BLUR_VAULT_THUMBNAILS] = enabled }
    }

    suspend fun setBlurRadius(radius: Float) {
        context.dataStore.edit { prefs -> prefs[KEY_BLUR_RADIUS] = radius.takeIf { it.isFinite() }?.coerceIn(6f, 36f) ?: 22f }
    }

    suspend fun setViewerDetails(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_VIEWER_DETAILS] = enabled }
    }

    suspend fun setVaultRestorePreference(preference: VaultRestorePreference) {
        require(preference.mode != VaultRestoreMode.SELECTED_ALBUM || preference.albumRelativePath.isNotBlank()) {
            "Choose an album before making it the restore destination"
        }
        context.dataStore.edit { prefs ->
            prefs[KEY_VAULT_RESTORE_MODE] = preference.mode.ordinal
            prefs[KEY_VAULT_RESTORE_ALBUM] = preference.albumRelativePath
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_APP_LOCK_ENABLED] = enabled }
    }

    suspend fun setAppLockPin(pin: String) {
        context.dataStore.edit { prefs -> prefs[KEY_APP_LOCK_PIN] = hashPin(pin) }
    }

    fun verifyAppLockPin(pin: String, storedVerifier: String): Boolean {
        if (!storedVerifier.startsWith("pbkdf2-sha1\$")) {
            return MessageDigest.isEqual(pin.toByteArray(), storedVerifier.toByteArray())
        }
        val parts = storedVerifier.split('$')
        if (parts.size != 4) return false
        return runCatching {
            val iterations = parts[1].toInt()
            val salt = Base64.decode(parts[2], Base64.NO_WRAP)
            val expected = Base64.decode(parts[3], Base64.NO_WRAP)
            val actual = derivePin(pin, salt, iterations, expected.size)
            MessageDigest.isEqual(actual, expected)
        }.getOrDefault(false)
    }

    suspend fun migrateLegacyAppLockPin() {
        val stored = appLockPinFlow.first()
        if (stored.length == 4 && stored.all(Char::isDigit)) setAppLockPin(stored)
    }

    private fun hashPin(pin: String): String {
        require(pin.length == 4 && pin.all(Char::isDigit)) { "PIN must contain four digits" }
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iterations = 150_000
        val hash = derivePin(pin, salt, iterations, 32)
        return listOf(
            "pbkdf2-sha1",
            iterations.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(hash, Base64.NO_WRAP)
        ).joinToString('$'.toString())
    }

    private fun derivePin(pin: String, salt: ByteArray, iterations: Int, bytes: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, bytes * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
