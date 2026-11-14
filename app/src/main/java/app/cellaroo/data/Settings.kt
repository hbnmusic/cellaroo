package app.cellaroo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stores secrets encrypted with an AES-256-GCM key that lives in the Android Keystore.
 * The ciphertext sits in a SharedPreferences file that is excluded from backups.
 */
class KeyVault(context: Context) {
    private val prefs = context.getSharedPreferences("cellaroo_secure", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun put(name: String, value: String) {
        if (value.isEmpty()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    fun get(name: String): String? = try {
        val packed = prefs.getString(name, null)
        if (packed == null) null else {
            val (iv, ct) = packed.split(":").map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val ALIAS = "cellaroo_master_key"
    }
}

data class SettingsState(
    val hasKey: Boolean = false,
    val model: String = Settings.DEFAULT_MODEL,
    val estimatesEnabled: Boolean = true,
    val knownModels: List<String> = emptyList(),
)

class Settings(context: Context) {
    private val vault = KeyVault(context)
    private val prefs = context.getSharedPreferences("cellaroo_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsState> = _state

    private fun read() = SettingsState(
        hasKey = !vault.get(KEY_GEMINI).isNullOrBlank(),
        model = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL,
        estimatesEnabled = prefs.getBoolean("estimates", true),
        knownModels = prefs.getString("known_models", "").orEmpty().split(',').filter { it.isNotBlank() },
    )

    fun apiKey(): String? = vault.get(KEY_GEMINI)?.takeIf { it.isNotBlank() }

    fun setApiKey(value: String) {
        vault.put(KEY_GEMINI, value.trim())
        _state.value = read()
    }

    fun setModel(model: String) {
        prefs.edit().putString("model", model.trim().removePrefix("models/").ifBlank { DEFAULT_MODEL }).apply()
        _state.value = read()
    }

    fun setEstimatesEnabled(on: Boolean) {
        prefs.edit().putBoolean("estimates", on).apply()
        _state.value = read()
    }

    fun setKnownModels(models: List<String>) {
        prefs.edit().putString("known_models", models.joinToString(",")).apply()
        _state.value = read()
    }

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        const val FALLBACK_MODEL = "gemini-2.5-flash"
        private const val KEY_GEMINI = "gemini_api_key"
    }
}
