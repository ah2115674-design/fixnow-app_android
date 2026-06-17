package com.example.data

import android.content.Context
import com.example.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response

/**
 * FIX: This interceptor now correctly injects the user's JWT (access token)
 * as the Authorization Bearer header on every request.
 *
 * Previously, every call used the SUPABASE_ANON_KEY as the Bearer token.
 * The anon key is NOT a user JWT — auth.uid() returns null when it's used,
 * which caused ALL RLS policies to silently block every insert/select/update.
 *
 * Now:
 *  - If a real user JWT is saved in SecureAuthManager → use it as Bearer
 *  - Otherwise fall back to the anon key (unauthenticated / public routes)
 *  - The apikey header always uses the anon key (this is correct for Supabase)
 */
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // apikey header: always the anon key (required by Supabase on every request)
        builder.header("apikey", BuildConfig.SUPABASE_ANON_KEY)

        // Authorization header: use real user JWT if available, else fall back to anon key
        val authManager = SecureAuthManager.getInstance(context)
        val userToken = authManager.userToken.value

        if (!userToken.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $userToken")
        } else {
            // Only public/auth endpoints should reach here (signup, login)
            if (original.header("Authorization") == null) {
                builder.header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            }
        }

        return chain.proceed(builder.build())
    }
}
