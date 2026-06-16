package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerProfile?

    @Query("SELECT * FROM customers WHERE referralCode = :code LIMIT 1")
    suspend fun getCustomerByReferralCode(code: String): CustomerProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerProfile)

    @Query("SELECT * FROM customers")
    fun getAllCustomersFlow(): Flow<List<CustomerProfile>>
}

@Dao
interface TechnicianDao {
    @Query("SELECT * FROM technicians WHERE phone = :phone LIMIT 1")
    suspend fun getTechnicianByPhone(phone: String): TechnicianProfile?

    @Query("SELECT * FROM technicians WHERE referralCode = :code LIMIT 1")
    suspend fun getTechnicianByReferralCode(code: String): TechnicianProfile?

    @Query("SELECT * FROM technicians")
    fun getAllTechniciansFlow(): Flow<List<TechnicianProfile>>

    @Query("SELECT * FROM technicians WHERE city = :city AND isApproved = 1 AND isOnline = 1 AND category = :category")
    suspend fun getEligibleOnlineTechnicians(city: String, category: String): List<TechnicianProfile>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTechnician(technician: TechnicianProfile)

    @Query("UPDATE technicians SET isOnline = :isOnline WHERE phone = :phone")
    suspend fun updateOnlineStatus(phone: String, isOnline: Boolean)

    @Query("UPDATE technicians SET isApproved = :isApproved WHERE phone = :phone")
    suspend fun updateApprovalStatus(phone: String, isApproved: Boolean)

    @Query("UPDATE technicians SET rating = :rating, totalJobs = :totalJobs WHERE phone = :phone")
    suspend fun updateTechnicianRating(phone: String, rating: Double, totalJobs: Int)

    @Query("UPDATE technicians SET latitude = :lat, longitude = :lng WHERE phone = :phone")
    suspend fun updateLocation(phone: String, lat: Double, lng: Double)
}

@Dao
interface BookingDao {
    @Query("SELECT * FROM bookings ORDER BY timestamp DESC")
    fun getAllBookingsFlow(): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE customerPhone = :phone ORDER BY timestamp DESC")
    fun getCustomerBookingsFlow(phone: String): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE technicianPhone = :phone ORDER BY timestamp DESC")
    fun getTechnicianBookingsFlow(phone: String): Flow<List<Booking>>

    @Query("SELECT * FROM bookings WHERE id = :id LIMIT 1")
    suspend fun getBookingById(id: Long): Booking?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooking(booking: Booking): Long

    @Update
    suspend fun updateBooking(booking: Booking)

    @Query("UPDATE bookings SET status = :status WHERE id = :id")
    suspend fun updateBookingStatus(id: Long, status: String)

    @Query("UPDATE bookings SET techLatitude = :lat, techLongitude = :lng WHERE id = :id")
    suspend fun updateBookingTechCoordinates(id: Long, lat: Double, lng: Double)

    @Query("SELECT * FROM bookings WHERE status = 'Requested' AND isManualAssign = 0")
    fun getUnassignedBookingsFlow(): Flow<List<Booking>>
}

@Dao
interface EarningDao {
    @Query("SELECT * FROM earnings WHERE technicianPhone = :techPhone ORDER BY timestamp DESC")
    fun getEarningsFlow(techPhone: String): Flow<List<EarningRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEarning(earning: EarningRecord)

    @Query("SELECT SUM(amount) FROM earnings WHERE technicianPhone = :techPhone")
    suspend fun getTotalEarnings(techPhone: String): Double?
}
