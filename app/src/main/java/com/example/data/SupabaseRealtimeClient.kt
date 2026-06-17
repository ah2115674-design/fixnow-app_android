package com.example.data

import android.util.Log
import com.example.BuildConfig
import okhttp3.*
import java.util.concurrent.TimeUnit

object SupabaseRealtimeClient {
    private const val TAG = "SupabaseRealtime"
    private val client = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS) // Keep-alive socket
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var onBookingUpdateCallback: ((bookingId: Long, status: String, techLat: Double?, techLng: Double?) -> Unit)? = null
    private var onTechUpdateCallback: ((phone: String, lat: Double, lng: Double, isOnline: Boolean) -> Unit)? = null

    fun setBookingCallback(callback: (bookingId: Long, status: String, techLat: Double?, techLng: Double?) -> Unit) {
        onBookingUpdateCallback = callback
    }

    fun setTechCallback(callback: (phone: String, lat: Double, lng: Double, isOnline: Boolean) -> Unit) {
        onTechUpdateCallback = callback
    }

    fun connect() {
        if (isConnected) return

        val originalUrl = BuildConfig.SUPABASE_URL
        val wsBaseUrl = originalUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
        
        val urlWithTrailingSlash = if (wsBaseUrl.endsWith("/")) wsBaseUrl else "$wsBaseUrl/"
        val wsUrl = "${urlWithTrailingSlash}realtime/v1/websocket?apikey=${BuildConfig.SUPABASE_ANON_KEY}&vsn=1.0.0"

        Log.d(TAG, "Connecting to WebSocket URL: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                Log.d(TAG, "🔌 Realtime connection established successfully!")
                
                // Bookings and technicians channel joining messages
                val joinBookings = "{\"topic\":\"realtime:public:bookings\",\"event\":\"phx_join\",\"payload\":{},\"ref\":\"1\"}"
                webSocket.send(joinBookings)
                
                val joinTechs = "{\"topic\":\"realtime:public:technicians\",\"event\":\"phx_join\",\"payload\":{},\"ref\":\"2\"}"
                webSocket.send(joinTechs)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "🔔 Raw Message: $text")
                try {
                    if (text.contains("bookings") && (text.contains("UPDATE") || text.contains("INSERT"))) {
                        val idPattern = "\"id\":(\\d+)".toRegex()
                        val statusPattern = "\"status\":\"([^\"]+)\"".toRegex()
                        val techLatPattern = "\"tech_latitude\":([0-9.]+)".toRegex()
                        val techLngPattern = "\"tech_longitude\":([0-9.]+)".toRegex()

                        val bookingId = idPattern.find(text)?.groupValues?.get(1)?.toLongOrNull()
                        val status = statusPattern.find(text)?.groupValues?.get(1)
                        val techLat = techLatPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
                        val techLng = techLngPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()

                        if (bookingId != null && status != null) {
                            Log.d(TAG, "Parsed Realtime Booking update: id=$bookingId, status=$status")
                            onBookingUpdateCallback?.invoke(bookingId, status, techLat, techLng)
                        }
                    } else if (text.contains("technicians") && text.contains("UPDATE")) {
                        val phonePattern = "\"phone\":\"([^\"]+)\"".toRegex()
                        val latPattern = "\"latitude\":([0-9.]+)".toRegex()
                        val lngPattern = "\"longitude\":([0-9.]+)".toRegex()
                        val onlinePattern = "\"is_online\":(true|false)".toRegex()

                        val phone = phonePattern.find(text)?.groupValues?.get(1)
                        val lat = latPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
                        val lng = lngPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
                        val isOnlineStr = onlinePattern.find(text)?.groupValues?.get(1)

                        if (phone != null && lat != null && lng != null) {
                            val isOnline = isOnlineStr?.toBoolean() ?: false
                            Log.d(TAG, "Parsed Realtime Tech update: phone=$phone, lat=$lat, lng=$lng")
                            onTechUpdateCallback?.invoke(phone, lat, lng, isOnline)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding message: ", e)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                Log.d(TAG, "Realtime connection closing: $reason (code $code)")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                Log.e(TAG, "Realtime connection failure: ", t)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "App closed")
        isConnected = false
    }

    fun clearCallbacks() {
        onBookingUpdateCallback = null
        onTechUpdateCallback = null
    }
}
