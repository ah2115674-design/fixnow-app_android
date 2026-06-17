package com.example.data

import java.io.Serializable
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class LatLng(
    val latitude: Double,
    val longitude: Double
) : Serializable

// ----------------------------------------------------
// MAPBOX GEOCODING & SEARCH RESPONSE MODELS
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class MapboxGeocodingResponse(
    val type: String,
    val features: List<MapboxFeature>
)

@JsonClass(generateAdapter = true)
data class MapboxFeature(
    val id: String,
    val type: String,
    @param:Json(name = "place_name") val placeName: String,
    val text: String,
    val center: List<Double> // [longitude, latitude]
)

// ----------------------------------------------------
// MAPBOX DIRECTIONS RESPONSE MODELS
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class MapboxDirectionsResponse(
    val routes: List<MapboxRoute>,
    val code: String
)

@JsonClass(generateAdapter = true)
data class MapboxRoute(
    val geometry: String?,
    val duration: Double, // in seconds
    val distance: Double  // in meters
)

// ----------------------------------------------------
// MAPBOX RETROFIT API SERVICE INTERFACE
// ----------------------------------------------------
interface MapboxApiService {
    @GET("geocoding/v5/mapbox.places/{query}.json")
    suspend fun searchPlaces(
        @Path("query") query: String,
        @Query("access_token") accessToken: String,
        @Query("country") country: String = "pk",
        @Query("autocomplete") autocomplete: Boolean = true,
        @Query("limit") limit: Int = 5
    ): MapboxGeocodingResponse

    @GET("geocoding/v5/mapbox.places/{lng},{lat}.json")
    suspend fun reverseGeocode(
        @Path("lng") lng: Double,
        @Path("lat") lat: Double,
        @Query("access_token") accessToken: String
    ): MapboxGeocodingResponse

    @GET("directions/v5/mapbox/driving/{coords}")
    suspend fun getDirections(
        @Path("coords") coords: String, // format: "startLng,startLat;destLng,destLat"
        @Query("access_token") accessToken: String,
        @Query("geometries") geometries: String = "polyline",
        @Query("overview") overview: String = "full"
    ): MapboxDirectionsResponse
}

// ----------------------------------------------------
// MAPBOX RETROFIT CLIENT
// ----------------------------------------------------
object MapboxClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    val apiService: MapboxApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.mapbox.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MapboxApiService::class.java)
    }
}

// ----------------------------------------------------
// MAPBOX TO RE-USE DECODER FOR ENCODED POLYLINE ROUTE
// ----------------------------------------------------
object MapboxPolylineDecoder {
    fun decode(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result ushr 1).inv() else result ushr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result ushr 1).inv() else result ushr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }
}
