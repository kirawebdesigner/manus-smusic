package com.smusic.app.data.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Spotify Authorization Code with PKCE for a public Android client. */
class SpotifyAuthManager(context: Context, private val clientId: String) {
    private val prefs = context.applicationContext.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
    private val redirectUri = "smusic://spotify-callback"

    fun isConfigured(): Boolean = clientId.isNotBlank()
    fun isConnected(): Boolean = prefs.getString(KEY_REFRESH_TOKEN, null) != null || prefs.getString(KEY_ACCESS_TOKEN, null) != null

    fun authorizationUri(): Uri? {
        if (!isConfigured()) return null
        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(32)
        prefs.edit().putString(KEY_VERIFIER, verifier).putString(KEY_STATE, state).apply()
        val challenge = Base64.encodeToString(sha256(verifier), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", "user-read-private")
            .build()
    }

    suspend fun handleCallback(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val error = uri.getQueryParameter("error")
        if (error != null) return@withContext Result.failure(IllegalStateException("Spotify authorization failed: $error"))
        val code = uri.getQueryParameter("code") ?: return@withContext Result.failure(IllegalStateException("Spotify callback did not contain an authorization code."))
        if (uri.getQueryParameter("state") != prefs.getString(KEY_STATE, null)) return@withContext Result.failure(IllegalStateException("Spotify authorization state did not match."))
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return@withContext Result.failure(IllegalStateException("Spotify PKCE verifier is missing."))
        runCatching {
            val connection = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                val body = listOf(
                    "client_id" to clientId,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "code_verifier" to verifier
                ).joinToString("&") { "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}" }
                connection.outputStream.use { it.write(body.toByteArray()) }
                val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (connection.responseCode !in 200..299) error("Spotify token exchange failed: ${JSONObject(response).optString("error_description", "HTTP ${connection.responseCode}")}")
                val json = JSONObject(response)
                prefs.edit()
                    .putString(KEY_ACCESS_TOKEN, json.optString("access_token"))
                    .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L)
                    .apply()
                json.optString("refresh_token").takeIf { it.isNotBlank() }?.let { prefs.edit().putString(KEY_REFRESH_TOKEN, it).apply() }
            } finally {
                connection.disconnect()
            }
        }.fold(onSuccess = { Result.success(Unit) }, onFailure = { Result.failure(it) })
    }

    suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        if (token != null && System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES_AT, 0L) - 60_000L) return@withContext token
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null) ?: return@withContext null
        refreshToken(refresh)
    }

    private fun refreshToken(refresh: String): String? {
        val connection = (URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            val body = "grant_type=refresh_token&refresh_token=${URLEncoder.encode(refresh, "UTF-8")}&client_id=${URLEncoder.encode(clientId, "UTF-8")}"
            connection.outputStream.use { it.write(body.toByteArray()) }
            if (connection.responseCode !in 200..299) return null
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val token = json.optString("access_token").ifBlank { return null }
            prefs.edit().putString(KEY_ACCESS_TOKEN, token).putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L).apply()
            token
        } finally {
            connection.disconnect()
        }
    }

    private fun randomUrlSafe(length: Int): String {
        val bytes = ByteArray(length)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING).take(length)
    }

    private fun sha256(value: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_VERIFIER = "verifier"
        private const val KEY_STATE = "state"
    }
}
