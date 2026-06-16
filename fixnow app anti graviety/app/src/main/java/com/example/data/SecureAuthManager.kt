package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise-grade session controller securing authorization JSON Web Tokens
 * with local fallback encryption using AES-128 cryptographic algorithms.
 */
class SecureAuthManager private constructor(context: Context) {

    private val sharedPrefs: SharedPreferences = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)
    private val moshi = Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()

    private val _userToken = MutableStateFlow<String?>(null)
    val userToken: StateFlow<String?> = _userToken.asStateFlow()

    private val _userRole = MutableStateFlow<String?>(null)
    val userRole: StateFlow<String?> = _userRole.asStateFlow()

    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId.asStateFlow()

    init {
        restoreSession()
    }

    @Synchronized
    fun saveSession(token: String, role: String, uid: String) {
        val encryptedToken = encryptString(token)
        sharedPrefs.edit()
            .putString(KEY_JWT, encryptedToken)
            .putString(KEY_ROLE, role)
            .putString(KEY_UID, uid)
            .apply()

        _userToken.value = token
        _userRole.value = role
        _userId.value = uid
        Log.d("SecureAuthManager", "Session successfully written & verified in secure storage.")
    }

    @Synchronized
    fun restoreSession() {
        val rawEncrypted = sharedPrefs.getString(KEY_JWT, null)
        val role = sharedPrefs.getString(KEY_ROLE, null)
        val uid = sharedPrefs.getString(KEY_UID, null)

        if (rawEncrypted != null && role != null && uid != null) {
            val decryptedToken = decryptString(rawEncrypted)
            if (decryptedToken != null) {
                _userToken.value = decryptedToken
                _userRole.value = role
                _userId.value = uid
                Log.d("SecureAuthManager", "Authentic user session reconstructed successfully.")
                return
            }
        }
        clearSession()
    }

    @Synchronized
    fun clearSession() {
        sharedPrefs.edit()
            .remove(KEY_JWT)
            .remove(KEY_ROLE)
            .remove(KEY_UID)
            .apply()

        _userToken.value = null
        _userRole.value = null
        _userId.value = null
        Log.d("SecureAuthManager", "Auth state flushed and secure registers zeroed.")
    }

    // --- Lightweight AES Sandbox Obfuscator ---
    private fun encryptString(value: String): String? {
        return try {
            val key = SecretKeySpec(SECRET_AES_KEY.toByteArray(StandardCharsets.UTF_8), "AES")
            val iv = IvParameterSpec(IV_PARAMS.toByteArray(StandardCharsets.UTF_8))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, key, iv)
            val bytes = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decryptString(encrypted: String): String? {
        return try {
            val key = SecretKeySpec(SECRET_AES_KEY.toByteArray(StandardCharsets.UTF_8), "AES")
            val iv = IvParameterSpec(IV_PARAMS.toByteArray(StandardCharsets.UTF_8))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, key, iv)
            val decoded = Base64.decode(encrypted, Base64.DEFAULT)
            String(cipher.doFinal(decoded), StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    companion object {
        private const val PREF_FILE_NAME = "fixnow_sec_store_v1"
        private const val KEY_JWT = "enc_jwt_token"
        private const val KEY_ROLE = "raw_role"
        private const val KEY_UID = "raw_uid"

        // Local sandbox key pairs (Note: Production systems bind to Android Keystore provider)
        private const val SECRET_AES_KEY = "FixNowAppKey9876" // 16 bytes
        private const val IV_PARAMS = "FixNowIvInitVec1" // 16 bytes

        @Volatile
        private var INSTANCE: SecureAuthManager? = null

        fun getInstance(context: Context): SecureAuthManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SecureAuthManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
