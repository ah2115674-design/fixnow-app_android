package com.example.data

import android.util.Log
import com.example.BuildConfig
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Client for Supabase Realtime (Phoenix Channels over WebSocket).
 *
 * IMPORTANT: To actually receive postgres_changes broadcasts, the join payload
 * must include a `config.postgres_changes` subscription list AND (because the
 * bookings/technicians RLS policies are scoped to `authenticated` users) an
 * `access_token` for the logged-in user. Connecting with only the anon key
 * will join the socket successfully but will receive zero row-change events
 * for RLS-protected tables.
 */
object SupabaseRealtimeClient {
    private const val TAG = "SupabaseRealtime"
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS) // Transport-level keep-alive
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var currentAccessToken: String? = null
    private val refCounter = AtomicInteger(1)
    private var heartbeatTimer: Timer? = null

    private var onBookingUpdateCallback: ((bookingId: Long, status: String, techLat: Double?, techLng: Double?) -> Unit)? = null
    private var onTechUpdateCallback: ((phone: String, lat: Double, lng: Double, isOnline: Boolean) -> Unit)? = null

    fun setBookingCallback(callback: (bookingId: Long, status: String, techLat: Double?, techLng: Double?) -> Unit) {
        onBookingUpdateCallback = callback
    }

    fun setTechCallback(callback: (phone: String, lat: Double, lng: Double, isOnline: Boolean) -> Unit) {
        onTechUpdateCallback = callback
    }

    /**
     * Connects (or reconnects) to Supabase Realtime.
     *
     * @param accessToken The signed-in user's Supabase Auth JWT. Pass null/empty to
     * fall back to the anon key (only works for tables with anon-readable RLS policies).
     * If a connection is already open with a different token (e.g. user just logged in),
     * the socket is closed and re-opened with the new token so RLS re-evaluates correctly.
     */
    @Synchronized
    fun connect(accessToken: String? = null) {
        val tokenToUse = accessToken?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_ANON_KEY

        if (isConnected && tokenToUse == currentAccessToken) return

        // If switching identity (e.g. anonymous -> logged in user), tear down old socket first
        if (isConnected) {
            disconnect()
        }

        currentAccessToken = tokenToUse

        val originalUrl = BuildConfig.SUPABASE_URL
        val wsBaseUrl = originalUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")

        val urlWithTrailingSlash = if (wsBaseUrl.endsWith("/")) wsBaseUrl else "$wsBaseUrl/"
        val wsUrl = "${urlWithTrailingSlash}realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"

        Log.d(TAG, "Connecting to Realtime WebSocket...")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "🔌 Realtime connection established successfully!")

                joinChannel(webSocket, "realtime:public:bookings", "bookings", tokenToUse)
                joinChannel(webSocket, "realtime:public:technicians", "technicians", tokenToUse)

                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    handleMessage(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding realtime message", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                stopHeartbeat()
                Log.d(TAG, "Realtime connection closing: $reason (code $code)")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                stopHeartbeat()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                stopHeartbeat()
                Log.e(TAG, "Realtime connection failure", t)
            }
        })
    }

    /**
     * Sends a phx_join with a postgres_changes subscription for the given table,
     * plus the caller's access token so RLS evaluates as that user (not anon).
     */
    private fun joinChannel(webSocket: WebSocket, topic: String, table: String, accessToken: String) {
        val joinPayload = JSONObject().apply {
            put("config", JSONObject().apply {
                put("postgres_changes", JSONArray().apply {
                    put(JSONObject().apply {
                        put("event", "*")
                        put("schema", "public")
                        put("table", table)
                    })
                })
                put("private", false)
            })
            put("access_token", accessToken)
        }

        val joinMessage = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", joinPayload)
            put("ref", refCounter.getAndIncrement().toString())
        }

        webSocket.send(joinMessage.toString())
    }

    /**
     * Phoenix channels require a periodic heartbeat message (separate from the
     * WebSocket transport ping) or the server will close the connection.
     */
    private fun startHeartbeat(webSocket: WebSocket) {
        stopHeartbeat()
        heartbeatTimer = Timer("supabase-realtime-heartbeat", true).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    if (!isConnected) return
                    val heartbeat = JSONObject().apply {
                        put("topic", "phoenix")
                        put("event", "heartbeat")
                        put("payload", JSONObject())
                        put("ref", refCounter.getAndIncrement().toString())
                    }
                    try {
                        webSocket.send(heartbeat.toString())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send heartbeat", e)
                    }
                }
            }, 25_000L, 25_000L)
        }
    }

    private fun stopHeartbeat() {
        heartbeatTimer?.cancel()
        heartbeatTimer = null
    }

    private fun handleMessage(text: String) {
        val root = JSONObject(text)
        val event = root.optString("event")
        if (event != "postgres_changes") return

        val payload = root.optJSONObject("payload") ?: return
        val data = payload.optJSONObject("data") ?: return
        val table = data.optString("table")
        val record = data.optJSONObject("record") ?: return

        when (table) {
            "bookings" -> {
                if (!record.has("id")) return
                val bookingId = record.optLong("id")
                val status = record.optString("status", "")
                if (status.isEmpty()) return
                val techLat = if (record.has("tech_latitude")) record.optDouble("tech_latitude") else null
                val techLng = if (record.has("tech_longitude")) record.optDouble("tech_longitude") else null
                Log.d(TAG, "Realtime booking update: id=$bookingId status=$status")
                onBookingUpdateCallback?.invoke(bookingId, status, techLat, techLng)
            }
            "technicians" -> {
                val phone = record.optString("phone", "")
                if (phone.isEmpty() || !record.has("latitude") || !record.has("longitude")) return
                val lat = record.optDouble("latitude")
                val lng = record.optDouble("longitude")
                val isOnline = record.optBoolean("is_online", false)
                Log.d(TAG, "Realtime technician update: phone=$phone lat=$lat lng=$lng online=$isOnline")
                onTechUpdateCallback?.invoke(phone, lat, lng, isOnline)
            }
        }
    }

    @Synchronized
    fun disconnect() {
        stopHeartbeat()
        webSocket?.close(1000, "App closed")
        webSocket = null
        isConnected = false
        currentAccessToken = null
    }

    fun clearCallbacks() {
        onBookingUpdateCallback = null
        onTechUpdateCallback = null
    }
}
