# Step 3: Production Supabase Integration Guide for FixNow

This guide explains how to execute **Step 3** to connect your live Android application to your live production **Supabase Back-end Database**. This ensures seamless live persistence, technician matching, active GPS tracking updates, and earnings accounting.

---

## Overview of Setup Process

1. **Step 1 (Done):** Configured Supabase keys in environment properties (`.env.example` / Secrets pane).
2. **Step 2 (Done):** Configured Google Maps permissions, dependencies, and API keys.
3. **Step 3 (This Step):** 
   - Initialize Postgres SQL Tables & Row Level Security (RLS) in the Supabase Dashboard.
   - Configure Gradle serialization dependencies.
   - Implement the Retrofit-based PostgREST Network layer.
   - Wire local Room storage with your cloud database (Offline-First sync).

---

## Part 1: Supabase Database Migration (SQL Setup)

Run this SQL block directly in the **SQL Editor** of your Supabase dashboard (`https://supabase.com` -> Project -> SQL Editor -> New Query). It sets up all tables matching your Room schema with real relational indexes and real-time triggers.

```sql
-- 1. Create Customers table
CREATE TABLE IF NOT EXISTS public.customers (
    phone TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    email TEXT NOT NULL,
    city TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Create Technicians table 
CREATE TABLE IF NOT EXISTS public.technicians (
    phone TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    category TEXT NOT NULL,
    sub_category TEXT,
    city TEXT NOT NULL,
    cnic TEXT NOT NULL,
    selfie_url TEXT,
    bank_details TEXT,
    is_approved BOOLEAN DEFAULT FALSE,
    is_online BOOLEAN DEFAULT FALSE,
    rating DOUBLE PRECISION DEFAULT 4.5,
    total_jobs INT DEFAULT 0,
    acceptance_rate DOUBLE PRECISION DEFAULT 1.0,
    latitude DOUBLE PRECISION DEFAULT 31.5204,
    longitude DOUBLE PRECISION DEFAULT 74.3587,
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Create Bookings table with relationships
CREATE TABLE IF NOT EXISTS public.bookings (
    id BIGSERIAL PRIMARY KEY,
    service_category TEXT NOT NULL,
    service_name TEXT NOT NULL,
    issue_description TEXT,
    customer_phone TEXT REFERENCES public.customers(phone) ON DELETE CASCADE,
    customer_name TEXT NOT NULL,
    customer_address TEXT NOT NULL,
    customer_city TEXT NOT NULL,
    preferred_time TEXT NOT NULL,
    payment_method TEXT NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    status TEXT NOT NULL DEFAULT 'Requested',
    technician_phone TEXT REFERENCES public.technicians(phone) ON DELETE SET NULL,
    technician_name TEXT,
    rating INT DEFAULT 0,
    review_comment TEXT,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    tech_latitude DOUBLE PRECISION DEFAULT 31.5204,
    tech_longitude DOUBLE PRECISION DEFAULT 74.3587,
    declined_technicians TEXT DEFAULT '',
    is_manual_assign BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Create Earnings record table
CREATE TABLE IF NOT EXISTS public.earnings (
    id BIGSERIAL PRIMARY KEY,
    technician_phone TEXT REFERENCES public.technicians(phone) ON DELETE CASCADE,
    booking_id BIGINT REFERENCES public.bookings(id) ON DELETE CASCADE,
    category TEXT NOT NULL,
    amount DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. Enable Realtime Replication for Instant GPS Tracking and status updates
alter publication supabase_realtime add table public.bookings;
alter publication supabase_realtime add table public.technicians;

-- 6. Setup Row Level Security (RLS) - Set to public-read/write for developer-friendly ease, or protect via policies
ALTER TABLE public.customers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.technicians ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.bookings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.earnings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public access to Customers" ON public.customers FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow public access to Technicians" ON public.technicians FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow public access to Bookings" ON public.bookings FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow public access to Earnings" ON public.earnings FOR ALL USING (true) WITH CHECK (true);
```

---

## Part 2: Gradle Dependencies

To enable networking and serialization support, verify that your `/gradle/libs.versions.toml` and `/app/build.gradle.kts` have the libraries for serialization and Retrofit configured.

### standard components of `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
```

---

## Part 3: Live PostgREST Supabase Client Service (Kotlin)

Create a network service interface `/app/src/main/java/com/example/data/SupabaseService.kt` that lets your app write directly to the Postgres database via REST:

```kotlin
package com.example.data

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.*

interface SupabaseService {

    @GET("rest/v1/customers")
    suspend fun getCustomer(
        @Query("phone") phone: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String = "Bearer $apiKey"
    ): List<CustomerDto>

    @POST("rest/v1/customers")
    suspend fun registerCustomer(
        @Body customer: CustomerDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String = "Bearer $apiKey",
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @GET("rest/v1/technicians")
    suspend fun getTechnician(
        @Query("phone") phone: String,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String = "Bearer $apiKey"
    ): List<TechnicianDto>

    @POST("rest/v1/technicians")
    suspend fun registerTechnician(
        @Body technician: TechnicianDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String = "Bearer $apiKey",
        @Header("Prefer") prefer: String = "resolution=merge-duplicates"
    ): Response<Unit>

    @POST("rest/v1/bookings")
    suspend fun createBooking(
        @Body booking: BookingDto,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String = "Bearer $apiKey",
        @Header("Prefer") prefer: String = "return=representation"
    ): List<BookingDto>

    @PATCH("rest/v1/bookings")
    suspend fun updateBookingStatus(
        @Query("id") id: String,
        @Body body: Map<String, String>,
        @Header("apikey") apiKey: String,
        @Header("Authorization") auth: String = "Bearer $apiKey"
    ): Response<Unit>
}

@Serializable
data class CustomerDto(
    val phone: String,
    val name: String,
    val email: String,
    val city: String
)

@Serializable
data class TechnicianDto(
    val phone: String,
    val name: String,
    val category: String,
    val sub_category: String?,
    val city: String,
    val cnic: String,
    val selfie_url: String?,
    val bank_details: String?,
    val is_approved: Boolean,
    val is_online: Boolean,
    val rating: Double,
    val total_jobs: Int,
    val acceptance_rate: Double,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class BookingDto(
    val id: Long? = null,
    val service_category: String,
    val service_name: String,
    val issue_description: String,
    val customer_phone: String,
    val customer_name: String,
    val customer_address: String,
    val customer_city: String,
    val preferred_time: String,
    val payment_method: String,
    val price: Double,
    val status: String,
    val technician_phone: String? = null,
    val technician_name: String? = null,
    val rating: Int = 0,
    val review_comment: String? = null,
    val latitude: Double,
    val longitude: Double,
    val tech_latitude: Double,
    val tech_longitude: Double
)
```

---

## Part 4: Production Sync Architecture Model

In a scalable setup, write-actions are pushed to both Room database and Cloud Supabase database. If network is offline, write to Room and queue it up using `WorkManager` for guaranteed background sync.

### Dual-Write Pattern Example
```kotlin
class HybridRepository(
    private val localDb: AppDatabase,
    private val remoteService: SupabaseService,
    private val apiKey: String
) {
    suspend fun saveCustomer(customer: CustomerProfile) {
        // 1. Instantly update local UI
        localDb.customerDao().insertCustomer(customer)
        
        // 2. Perform background server push
        try {
            remoteService.registerCustomer(
                CustomerDto(customer.phone, customer.name, customer.email, customer.city),
                apiKey
            )
        } catch (e: Exception) {
            // Schedule retry via WorkManager if cloud write fails
        }
    }
}
```

---

## Next Steps for You

1. Access your **Supabase Dashboard**.
2. Run the SQL block in the **SQL Editor**.
3. Obtain your Project **URL** & **Anon Key** from `Settings -> API`.
4. Run your application on production securely!
