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
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class CustomerMetadata(
    val name: String,
    val phone: String,
    val city: String,
    val role: String = "customer"
)

@JsonClass(generateAdapter = true)
data class TechMetadata(
    val name: String,
    val phone: String,
    val city: String,
    val category: String,
    val cnic: String,
    val role: String = "technician"
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
    @Json(name = "token_type") val tokenType: String?,
    @Json(name = "user") val user: SupabaseUser?
)

// ----------------------------------------------------
// TABLE DTO Models (PostgREST API)
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class CustomerDto(
    val id: String, // UUID in Supabase
    val phone: String,
    val name: String,
    val email: String,
    val city: String
)

@JsonClass(generateAdapter = true)
data class TechnicianDto(
    val id: String, // UUID in Supabase
    val phone: String,
    val name: String,
    val category: String,
    @Json(name = "sub_category") val subCategory: String?,
    val city: String,
    val cnic: String,
    @Json(name = "selfie_url") val selfieUrl: String?,
    @Json(name = "bank_details") val bankDetails: String?,
    @Json(name = "is_approved") val isApproved: Boolean,
    @Json(name = "is_online") val isOnline: Boolean,
    val rating: Double,
    @Json(name = "total_jobs") val totalJobs: Int,
    @Json(name = "acceptance_rate") val acceptanceRate: Double,
    val latitude: Double,
    val longitude: Double
)

@JsonClass(generateAdapter = true)
data class BookingDto(
    val id: Long? = null,
    @Json(name = "service_category") val serviceCategory: String,
    @Json(name = "service_name") val serviceName: String,
    @Json(name = "issue_description") val issueDescription: String,
    @Json(name = "customer_id") val customerId: String, // Required UUID references customers(id)
    @Json(name = "customer_phone") val customerPhone: String,
    @Json(name = "customer_name") val customerName: String,
    @Json(name = "customer_address") val customerAddress: String,
    @Json(name = "customer_city") val customerCity: String,
    @Json(name = "preferred_time") val preferredTime: String,
    @Json(name = "payment_method") val paymentMethod: String,
    val price: Double,
    val status: String,
    @Json(name = "technician_id") val technicianId: String? = null,
    @Json(name = "technician_phone") val technicianPhone: String? = null,
    @Json(name = "technician_name") val technicianName: String? = null,
    val rating: Int = 0,
    @Json(name = "review_comment") val reviewComment: String? = null,
    val latitude: Double,
    val longitude: Double,
    @Json(name = "tech_latitude") val techLatitude: Double,
    @Json(name = "tech_longitude") val techLongitude: Double,
    @Json(name = "declined_technicians") val declinedTechnicians: String = "",
    @Json(name = "is_manual_assign") val isManualAssign: Boolean = false
)

@JsonClass(generateAdapter = true)
data class EarningDto(
    val id: Long? = null,
    @Json(name = "technician_id") val technicianId: String,
    @Json(name = "technician_phone") val technicianPhone: String,
    @Json(name = "booking_id") val bookingId: Long,
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
    @Json(name = "p_booking_id") val bookingId: Long,
    @Json(name = "p_technician_id") val technicianId: String
)

@JsonClass(generateAdapter = true)
data class TechDiscoveryParams(
    @Json(name = "p_booking_latitude") val bookingLatitude: Double,
    @Json(name = "p_booking_longitude") val bookingLongitude: Double,
    @Json(name = "p_city") val city: String,
    @Json(name = "p_category") val category: String
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
// INSERT DTOs (for creating new rows via PostgREST)
// These are separate from the read DTOs because the DB
// assigns the UUID from the Auth user id on insert.
// ----------------------------------------------------
@JsonClass(generateAdapter = true)
data class CustomerInsertDto(
    val id: String,                // = Supabase Auth user UUID
    val phone: String,
    val name: String,
    val email: String,
    val city: String,
    @Json(name = "referral_code") val referralCode: String = "",
    @Json(name = "referred_by_code") val referredByCode: String? = null
)

@JsonClass(generateAdapter = true)
data class TechnicianInsertDto(
    val id: String,                // = Supabase Auth user UUID
    val phone: String,
    val name: String,
    val category: String,
    @Json(name = "sub_category") val subCategory: String = "",
    val city: String,
    val cnic: String,
    @Json(name = "selfie_url") val selfieUrl: String = "",
    @Json(name = "bank_details") val bankDetails: String = "",
    @Json(name = "is_approved") val isApproved: Boolean = false,
    @Json(name = "is_online") val isOnline: Boolean = false,
    val rating: Double = 4.5,
    @Json(name = "total_jobs") val totalJobs: Int = 0,
    @Json(name = "acceptance_rate") val acceptanceRate: Double = 1.0,
    val latitude: Double = 31.5204,
    val longitude: Double = 74.3587,
    @Json(name = "referral_code") val referralCode: String = "",
    @Json(name = "referred_by_code") val referredByCode: String? = null
)

// ============================================================
// SUPPORT TICKETS, COUPONS, FRAUD ALERTS, PAYOUT WITHDRAWALS
// ============================================================

@JsonClass(generateAdapter = true)
data class SupportTicketDto(
    val id: Long? = null,
    @Json(name = "customer_phone") val customerPhone: String,
    @Json(name = "customer_name")  val customerName: String,
    @Json(name = "booking_id")     val bookingId: Long? = null,
    val category: String,
    val description: String,
    val status: String = "Open",
    @Json(name = "sla_timer_minutes") val slaTimerMinutes: Int = 15,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class SupportMessageDto(
    val id: Long? = null,
    @Json(name = "ticket_id") val ticketId: Long,
    val sender: String,
    val message: String,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CouponDto(
    val id: Long? = null,
    val code: String,
    @Json(name = "discount_amount") val discountAmount: Double,
    val description: String,
    @Json(name = "is_referral")  val isReferral: Boolean = false,
    @Json(name = "referee_name") val refereeName: String? = null,
    @Json(name = "is_active")    val isActive: Boolean = true
)

@JsonClass(generateAdapter = true)
data class FraudAlertDto(
    val id: Long? = null,
    val title: String,
    val severity: String,
    val description: String,
    @Json(name = "associated_phone") val associatedPhone: String,
    @Json(name = "is_resolved") val isResolved: Boolean = false,
    @Json(name = "created_at") val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class PayoutWithdrawalDto(
    val id: String,
    val name: String,
    val type: String,
    val amount: Double,
    @Json(name = "payment_details") val paymentDetails: String,
    val status: String = "Pending Approved",
    @Json(name = "created_at") val createdAt: String? = null
)

// ----------------------------------------------------
// RETROFIT API SERVICE INTERFACE
// ----------------------------------------------------
interface SupabaseService {

    // --- HEARTBEAT / CONNECTION check ---
    @GET(".") // Check the root API response
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

    // --- INSERT rows (called after successful Auth signup) ---
    @POST("rest/v1/customers")
    suspend fun createCustomer(
        @Body customer: CustomerInsertDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<CustomerDto>>

    @POST("rest/v1/technicians")
    suspend fun createTechnician(
        @Body technician: TechnicianInsertDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<TechnicianDto>>

        @GET("rest/v1/customers")
    suspend fun getCustomer(
        @Query("phone") phone: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<CustomerDto>

    @GET("rest/v1/customers")
    suspend fun getCustomerById(
        @Query("id") id: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<CustomerDto>

    @PATCH("rest/v1/customers")
    suspend fun updateCustomerProfile(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

    @GET("rest/v1/technicians")
    suspend fun getTechnician(
        @Query("phone") phone: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<TechnicianDto>

    @GET("rest/v1/technicians")
    suspend fun getTechnicianById(
        @Query("id") id: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<TechnicianDto>

    @GET("rest/v1/technicians")
    suspend fun getAllTechnicians(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<TechnicianDto>

    @PATCH("rest/v1/technicians")
    suspend fun updateTechnicianProfile(
        @Query("id") id: String,
        @Body body: Map<String, Any>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

    @GET("rest/v1/bookings")
    suspend fun getBookings(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Query("select") select: String = "*"
    ): List<BookingDto>

    @POST("rest/v1/bookings")
    suspend fun createBooking(
        @Body booking: BookingDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): List<BookingDto>

    @PATCH("rest/v1/bookings")
    suspend fun updateBookingStatus(
        @Query("id") id: String, // id = eq.{id}
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

    @POST("rest/v1/earnings")
    suspend fun addEarning(
        @Body earning: EarningDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

    @GET("rest/v1/earnings")
    suspend fun getEarnings(
        @Query("technician_id") techId: String, // technician_id = eq.{techId}
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<EarningDto>

    // --- RPCs ---
    @POST("rest/v1/rpc/accept_booking")
    suspend fun acceptBookingRpc(
        @Body params: AcceptBookingParams,
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String
    ): Response<Boolean> // returns true/false Boolean in Supabase

    @POST("rest/v1/rpc/find_nearby_technicians")
    suspend fun findNearbyTechniciansRpc(
        @Body params: TechDiscoveryParams,
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String
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

    /**
     * Test connection to live Supabase endpoint asynchronously.
     * Returns true if request responds with any valid HTTP code (even 401 unauth is online, but 200/204 is connected).
     */
    suspend fun testConnection(): Boolean {
        return try {
            val response = apiService.getHeartbeat(BuildConfig.SUPABASE_ANON_KEY)
            // If we get response back, it is online!
            response.isSuccessful || response.code() == 401 || response.code() == 404
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    // ---- SUPPORT TICKETS ----
    @GET("rest/v1/support_tickets")
    suspend fun getSupportTickets(
        @Query("customer_phone") phone: String,
        @Query("order") order: String = "created_at.desc",
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<SupportTicketDto>

    @GET("rest/v1/support_tickets")
    suspend fun getAllSupportTickets(
        @Query("order") order: String = "created_at.desc",
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<SupportTicketDto>

    @POST("rest/v1/support_tickets")
    suspend fun createSupportTicket(
        @Body ticket: SupportTicketDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<SupportTicketDto>>

    @PATCH("rest/v1/support_tickets")
    suspend fun updateSupportTicket(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

    // ---- SUPPORT MESSAGES ----
    @GET("rest/v1/support_messages")
    suspend fun getSupportMessages(
        @Query("ticket_id") ticketId: String,
        @Query("order") order: String = "created_at.asc",
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<SupportMessageDto>

    @POST("rest/v1/support_messages")
    suspend fun createSupportMessage(
        @Body message: SupportMessageDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<SupportMessageDto>>

    // ---- COUPONS ----
    @GET("rest/v1/coupons")
    suspend fun getCoupons(
        @Query("is_active") isActive: String = "eq.true",
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<CouponDto>

    @POST("rest/v1/coupons")
    suspend fun createCoupon(
        @Body coupon: CouponDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<CouponDto>>

    // ---- FRAUD ALERTS ----
    @GET("rest/v1/fraud_alerts")
    suspend fun getFraudAlerts(
        @Query("order") order: String = "created_at.desc",
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<FraudAlertDto>

    @POST("rest/v1/fraud_alerts")
    suspend fun createFraudAlert(
        @Body alert: FraudAlertDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<FraudAlertDto>>

    @PATCH("rest/v1/fraud_alerts")
    suspend fun updateFraudAlert(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

    // ---- PAYOUT WITHDRAWALS ----
    @GET("rest/v1/payout_withdrawals")
    suspend fun getPayoutWithdrawals(
        @Query("order") order: String = "created_at.desc",
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): List<PayoutWithdrawalDto>

    @POST("rest/v1/payout_withdrawals")
    suspend fun createPayoutWithdrawal(
        @Body withdrawal: PayoutWithdrawalDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String,
        @Header("Prefer") prefer: String = "return=representation"
    ): Response<List<PayoutWithdrawalDto>>

    @PATCH("rest/v1/payout_withdrawals")
    suspend fun updatePayoutWithdrawal(
        @Query("id") id: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") authHeader: String
    ): Response<Unit>

}
