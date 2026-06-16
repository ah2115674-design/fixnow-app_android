package com.example.data

import android.content.Context
import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Automates token propagation to Supabase Backend.
 * Replaces hardcoded client authorizations with authenticated user-specific JWTs.
 */
class AuthInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // Ensure default API Key is present on all calls
        builder.header("apikey", BuildConfig.SUPABASE_ANON_KEY)

        // Read dynamic Authorization Header
        val authManager = SecureAuthManager.getInstance(context)
        val activeJwt = authManager.userToken.value

        val path = originalRequest.url.encodedPath

        // Skip adding personal bearer tokens during standard login/sign-ups
        val isPreAuth = path.contains("auth/v1/signup") || 
                        path.contains("auth/v1/token") ||
                        originalRequest.url.toString() == BuildConfig.SUPABASE_URL ||
                        originalRequest.url.toString() == "${BuildConfig.SUPABASE_URL}/"

        if (!isPreAuth && activeJwt != null) {
            builder.header("Authorization", "Bearer $activeJwt")
        } else if (originalRequest.header("Authorization") == null) {
            builder.header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
        }

        return chain.proceed(builder.build())
    }
}
