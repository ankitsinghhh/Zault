package com.example.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed class BiometricAuthResult {
    data object Success : BiometricAuthResult()
    data class Error(val errorCode: Int, val errString: String) : BiometricAuthResult()
    data object Failed : BiometricAuthResult()
    data object NotAvailable : BiometricAuthResult()
}

class BiometricAuthenticator(private val context: Context) {

    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val authenticators = BIOMETRIC_STRONG
        val status = biometricManager.canAuthenticate(authenticators)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun promptBiometric(
        activity: FragmentActivity,
        title: String = "Unlock Vault",
        subtitle: String = "Authenticate to access your private media",
        onResult: (BiometricAuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")

        val promptInfo = promptInfoBuilder.build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onResult(BiometricAuthResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onResult(BiometricAuthResult.Error(errorCode, errString.toString()))
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onResult(BiometricAuthResult.Failed)
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onResult(BiometricAuthResult.Error(-1, e.message ?: "Authentication failed to start"))
        }
    }
}
