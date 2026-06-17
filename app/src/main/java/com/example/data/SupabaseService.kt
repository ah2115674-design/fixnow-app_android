package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ----------------------------------------------------
// AUTH models for Supabase Auth (GoTrue API)
// FIX: Added @JsonClass(generateAdapter = true) to ALL metadata classes.
// Without this, Moshi falls back to reflection which fails in release builds,
// silently sending empty metadata → the DB trigger uses fallback random values
// → every phone lookup fails.
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class CustomerMetadata(
    @Json(name = "name")  val name: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "city")  val city: String,
    @Json(name = "role")  val role: String = "customer"
)

@JsonClass(generateAdapter = true)
data class TechMetadata(
    @Json(name = "name")     val name: String,
    @Json(name = "phone")    val phone: String,
    @Json(name = "city")     val city: String,
    @Json(name = "category") val category: String,
    @Json(name = "cnic")     val cnic: String,
    @Json(name = "role")     val role: String = "technician"
)

@JsonClass(generateAdapter = true)
data class CustomerSignUpOptions(
    @Json(name = "data") val data: CustomerMetadata
)

@JsonClass(generateAdapter = true)
data class TechSignUpOptions(
    @Json(name = "data") val data: TechMetadata
)

@JsonClass(generateAdapter = true)
data class SupabaseCustomerSignUpRequest(
    val email: String,
    val password: String,
    @Json(name = "options") val options: CustomerSignUpOptions
)

@JsonClass(generateAdapter = true)
data class SupabaseTechSignUpRequest(
    val email: String,
    val password: String,
    @Json(name = "options") val options: TechSignUpOptions
)

@JsonClass(generateAdapter = true)
data class SupabaseSignInRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class SupabaseGoogleSignInRequest(
    @Json(name = "provider") val provider: String = "google",
    @Json(name = "id_token") val idToken: String
)

@JsonClass(generateAdapter = true)
data class SupabaseUser(
    val id: String,
    val email: String?,
    @Json(name = "user_metadata") val userMetadata: Map<String, String>?
)

@JsonClass(generateAdapter = true)
data class SupabaseAuthResponse(
    @Json(name = "access_token") val accessToken: String?,
    @Json(name = "token_type")   val tokenType: String?,
    @Json(name = "user")         val user: SupabaseUser?
)

// ----------------------------------------------------
// TABLE DTO Models (PostgREST API)
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class CustomerDto(
    val id: String,
    val phone: String,
    val name: String,
    val email: String,
    val city: String
)

@JsonClass(generateAdapter = true)
data class TechnicianDto(
    val id: String,
    val phone: String,
    val name: String,
    val category: String,
    @Json(name = "sub_category")  val subCategory: String?,
    val city: String,
    val cnic: String,
    @Json(name = "selfie_url")    val selfieUrl: String?,
    @Json(name = "bank_details")  val bankDetails: String?,
    @Json(name = "is_approved")   val isApproved: Boolean,
    @Json(name = "is_online")     val isOnline: Boolean,
    val rating: Double,
    @Json(name = "total_jobs")    val totalJobs: Int,
    @Json(name = "acceptance_rate") val acceptanceRate: Double,
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class BookingDto(
    val id: Long? = null,
    @Json(name = "service_category")  val serviceCategory: String,
    @Json(name = "service_name")      val serviceName: String,
    @Json(name = "issue_description") val issueDescription: String,
    @Json(name = "customer_id")       val customerId: String,
    @Json(name = "customer_phone")    val customerPhone: String,
    @Json(name = "customer_name")     val customerName: String,
    @Json(name = "customer_address")  val customerAddress: String,
    @Json(name = "customer_city")     val customerCity: String,
    @Json(name = "preferred_time")    val preferredTime: String,
    @Json(name = "payment_method")    val paymentMethod: String,
    val price: Double,
    val status: String,
    @Json(name = "technician_id")     val technicianId: String? = null,
    @Json(name = "technician_phone")  val technicianPhone: String? = null,
    @Json(name = "technician_name")   val technicianName: String? = null,
    val rating: Int = 0,
    @Json(name = "review_comment")    val reviewComment: String? = null,
    val latitude: Double,
    val longitude: Double,
    // FIX: tech coordinates start at (0,0) not customer location.
    // They are only set meaningfully once a tech is matched/accepts.
    @Json(name = "tech_latitude")     val techLatitude: Double = 0.0,
    @Json(name = "tech_longitude")    val techLongitude: Double = 0.0,
    @Json(name = "declined_technicians") val declinedTechnicians: String = "",
    @Json(name = "is_manual_assign") val isManualAssign: Boolean = false
)

@JsonClass(generateAdapter = true)
data class EarningDto(
    val id: Long? = null,
    @Json(name = "technician_id")    val technicianId: String,
    @Json(name = "technician_phone") val technicianPhone: String,
    @Json(name = "booking_id")       val bookingId: Long,
    val category: String,
    val amount: Double
)

// ----------------------------------------------------
// DATABASE RPC / RPC RESPONSES
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class BookingReservationResponse(
    val success: Boolean,
    val message: String,
    @Json(name = "updated_status") val updatedStatus: String?
)

@JsonClass(generateAdapter = true)
data class AcceptBookingParams(
    @Json(name = "p_booking_id")    val bookingId: Long,
    @Json(name = "p_technician_id") val technicianId: String
)

@JsonClass(generateAdapter = true)
data class TechDiscoveryParams(
    @Json(name = "p_booking_latitude")  val bookingLatitude: Double,
    @Json(name = "p_booking_longitude") val bookingLongitude: Double,
    @Json(name = "p_city")             val city: String,
    @Json(name = "p_category")         val category: String
)

@JsonClass(generateAdapter = true)
data class TechDiscoveryResponse(
    val id: String,
    val phone: String,
    val name: String,
    val rating: Double,
    val distance: Double
)

// ----------------------------------------------------
// RETROFIT API SERVICE INTERFACE
// NOTE: The AuthInterceptor already injects apikey + Authorization
// headers on every request automatically. The explicit @Header params
// on every method are kept for clarity and as override capability but
// the interceptor is the authoritative source of the JWT.
// ----------------------------------------------------
interface SupabaseService {

    @GET(".")
    suspend fun getHeartbeat(
        @Header("apikey") apiKey: String
    ): Response<Unit>

    // --- AUTH (GoTrue) API ---
    @POST("auth/v1/signup")
    suspend fun customerSignUp(
        @Body request: SupabaseCustomerSignUpRequest,
        @Header("apikey") apiKey: String
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/signup")
    suspend fun techSignUp(
        @Body request: SupabaseTechSignUpRequest,
        @Header("apikey") apiKey: String
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(
        @Body request: SupabaseSignInRequest,
        @Header("apikey") apiKey: String
    ): Response<SupabaseAuthResponse>

    @POST("auth/v1/token?grant_type=id_token")
    suspend fun signInWithGoogle(
        @Body request: SupabaseGoogleSignInRequest,
        @Header("apikey") apiKey: String
    ): Response<SupabaseAuthResponse>

    // --- REST TABLES API ---
    // FIX: Removed per-call @Header("Authorization") params.
    // The AuthInterceptor injects the real user JWT on every call automatically.
    // Passing SUPABASE_ANON_KEY as Bearer here was the root cause of all RLS failures.

    @GET("rest/v1/customers")
    suspend fun getCustomer(
        @Query("phone") phone: String,
        @Header("apikey") apiKey: String
    ): List<CustomerDto>

    @GET("rest/v1/customers")
    suspend fun getCustomerById(
        @Query("id") id: String,
        @Header("apikey") apiKey: String
    ): List<CustomerDto>

    @PATCH("rest/v1/customers")
    suspend fun updateCustomerProfile(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String
    ): Response<Unit>

    @GET("rest/v1/technicians")
    suspend fun getTechnician(
        @Query("phone") phone: String,
        @Header("apikey") apiKey: String
    ): List<TechnicianDto>

    @GET("rest/v1/technicians")
    suspend fun getAllTechnicians(
        @Header("apikey") apiKey: String
    ): List<TechnicianDto>

    @PATCH("rest/v1/technicians")
    suspend fun updateTechnicianProfile(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String
    ): Response<Unit>

    @GET("rest/v1/bookings")
    suspend fun getBookings(
        @Header("apikey") apiKey: String,
        @Query("select") select: String = "*"
    ): List<BookingDto>

    @POST("rest/v1/bookings")
    suspend fun createBooking(
        @Body booking: BookingDto,
        @Header("apikey") apiKey: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<BookingDto>

    @PATCH("rest/v1/bookings")
    suspend fun updateBookingStatus(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String
    ): Response<Unit>

    @POST("rest/v1/earnings")
    suspend fun addEarning(
        @Body earning: EarningDto,
        @Header("apikey") apiKey: String
    ): Response<Unit>

    @GET("rest/v1/earnings")
    suspend fun getEarnings(
        @Query("technician_id") techId: String,
        @Header("apikey") apiKey: String
    ): List<EarningDto>

    @POST("rest/v1/rpc/accept_booking")
    suspend fun acceptBookingRpc(
        @Body params: AcceptBookingParams,
        @Header("apikey") apiKey: String
    ): Response<Boolean>

    @POST("rest/v1/rpc/find_nearby_technicians")
    suspend fun findNearbyTechniciansRpc(
        @Body params: TechDiscoveryParams,
        @Header("apikey") apiKey: String
    ): Response<List<TechDiscoveryResponse>>
}

// ----------------------------------------------------
// SUPABASE CLIENT OBJECT
// ----------------------------------------------------
object SupabaseClient {
    private val moshi = com.squareup.moshi.Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private var authInterceptor: AuthInterceptor? = null

    fun initialize(context: android.content.Context) {
        authInterceptor = AuthInterceptor(context.applicationContext)
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val interceptor = authInterceptor
                if (interceptor != null) {
                    interceptor.intercept(chain)
                } else {
                    // Fallback before initialize() is called: anon key only, no user JWT
                    val builder = chain.request().newBuilder()
                    builder.header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    if (chain.request().header("Authorization") == null) {
                        builder.header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                    }
                    chain.proceed(builder.build())
                }
            }
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val apiService: SupabaseService by lazy {
        val baseUrl = if (BuildConfig.SUPABASE_URL.endsWith("/")) {
            BuildConfig.SUPABASE_URL
        } else {
            "${BuildConfig.SUPABASE_URL}/"
        }
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SupabaseService::class.java)
    }

    suspend fun testConnection(): Boolean {
        return try {
            val response = apiService.getHeartbeat(BuildConfig.SUPABASE_ANON_KEY)
            response.isSuccessful || response.code() == 401 || response.code() == 404
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
