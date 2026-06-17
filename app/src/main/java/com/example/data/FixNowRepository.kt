package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlin.math.pow
import kotlin.math.sqrt

class FixNowRepository(
    private val customerDao: CustomerDao,
    private val technicianDao: TechnicianDao,
    private val bookingDao: BookingDao,
    private val earningDao: EarningDao
) {
    // Flows
    val allBookings: Flow<List<Booking>> = bookingDao.getAllBookingsFlow()
    val allCustomers: Flow<List<CustomerProfile>> = customerDao.getAllCustomersFlow()
    val allTechnicians: Flow<List<TechnicianProfile>> = technicianDao.getAllTechniciansFlow()
    val unassignedBookings: Flow<List<Booking>> = bookingDao.getUnassignedBookingsFlow()

    fun getCustomerBookings(phone: String): Flow<List<Booking>> = bookingDao.getCustomerBookingsFlow(phone)
    fun getTechnicianBookings(phone: String): Flow<List<Booking>> = bookingDao.getTechnicianBookingsFlow(phone)
    fun getEarningsFlow(techPhone: String): Flow<List<EarningRecord>> = earningDao.getEarningsFlow(techPhone)

    // Customer operations
    suspend fun getCustomer(phone: String): CustomerProfile? = customerDao.getCustomerByPhone(phone)
    suspend fun registerCustomer(customer: CustomerProfile) = customerDao.insertCustomer(customer)

    // Technician operations
    suspend fun getTechnician(phone: String): TechnicianProfile? = technicianDao.getTechnicianByPhone(phone)
    suspend fun registerTechnician(technician: TechnicianProfile) = technicianDao.insertTechnician(technician)
    suspend fun updateTechnicianOnlineStatus(phone: String, isOnline: Boolean) = technicianDao.updateOnlineStatus(phone, isOnline)
    suspend fun updateTechnicianApproval(phone: String, isApproved: Boolean) = technicianDao.updateApprovalStatus(phone, isApproved)
    suspend fun updateTechnicianLocation(phone: String, lat: Double, lng: Double) = technicianDao.updateLocation(phone, lat, lng)

    // Booking actions
    suspend fun createBooking(booking: Booking): Long = bookingDao.insertBooking(booking)
    suspend fun getBooking(id: Long): Booking? = bookingDao.getBookingById(id)
    suspend fun updateBooking(booking: Booking) = bookingDao.updateBooking(booking)
    suspend fun updateBookingStatus(id: Long, status: String) = bookingDao.updateBookingStatus(id, status)
    suspend fun updateBookingTechCoordinates(id: Long, lat: Double, lng: Double) = bookingDao.updateBookingTechCoordinates(id, lat, lng)

    // Earnings
    suspend fun addEarning(earning: EarningRecord) = earningDao.insertEarning(earning)
    suspend fun getTotalEarnings(techPhone: String): Double = earningDao.getTotalEarnings(techPhone) ?: 0.0

    /**
     * Marketplace Logic: Match nearest qualified technician.
     * Factors: City, Service Category, Online, Approved, not inside declinedTechnicians.
     * Cascade Rules: Searches within 1 km, then 5 km, then 10 km, and finally throughout the entire city.
     */
    suspend fun findNearestQualifiedTechnician(booking: Booking): TechnicianProfile? {
        val eligibleTechs = technicianDao.getEligibleOnlineTechnicians(booking.customerCity, booking.serviceCategory)
        if (eligibleTechs.isEmpty()) return null

        val declinedList = booking.declinedTechnicians.split(",").map { it.trim() }.filter { it.isNotEmpty() }

        // Filter out techs who have already declined this booking
        val nonDeclined = eligibleTechs.filter { it.phone !in declinedList }
        if (nonDeclined.isEmpty()) return null

        // Calculate distances in KM (where 1 degree is roughly 100 KM in Lahore scale)
        val techsWithDistance = nonDeclined.map { tech ->
            val distKm = calculateDistanceInKm(tech.latitude, tech.longitude, booking.latitude, booking.longitude)
            Pair(tech, distKm)
        }

        // 1st Level: Within 1.0 KM
        val within1 = techsWithDistance.filter { it.second <= 1.0 }
        if (within1.isNotEmpty()) {
            return chooseBestOfCandidates(within1)
        }

        // 2nd Level: Within 5.0 KM
        val within5 = techsWithDistance.filter { it.second <= 5.0 }
        if (within5.isNotEmpty()) {
            return chooseBestOfCandidates(within5)
        }

        // 3rd Level: Within 10.0 KM
        val within10 = techsWithDistance.filter { it.second <= 10.0 }
        if (within10.isNotEmpty()) {
            return chooseBestOfCandidates(within10)
        }

        // 4th Level: Entire City
        return chooseBestOfCandidates(techsWithDistance)
    }

    private fun chooseBestOfCandidates(candidates: List<Pair<TechnicianProfile, Double>>): TechnicianProfile {
        return candidates.minBy { pair ->
            val tech = pair.first
            val dist = pair.second
            // Combine distance and rating (higher rating is better; weight 5.0 - rating heavily to balance distance)
            dist + (5.0 - tech.rating) * 2.0
        }.first
    }

    private fun calculateDistanceInKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // FIX: Use the Haversine formula instead of a flat Euclidean-degree
        // approximation (sqrt(dLat^2+dLon^2) * 100.0). The old approximation
        // is wildly wrong away from the equator and breaks down badly across
        // longer distances or near city boundaries — it doesn't account for
        // the fact that a degree of longitude shrinks as latitude increases.
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).pow(2.0) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).pow(2.0)
        val c = 2 * kotlin.math.atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Submit customer review feedback, update booking and update technician's average rating.
     */
    suspend fun submitReview(bookingId: Long, rating: Int, comment: String) {
        val booking = bookingDao.getBookingById(bookingId) ?: return
        val currentTechPhone = booking.technicianPhone ?: return

        // FIX: Detect re-review BEFORE overwriting the booking's rating field.
        // The booking is only "new" (not yet counted toward the technician's
        // totalJobs/rating average) if its rating was still the default 0.
        // Without this check, every re-submission of a review incremented
        // totalJobs again and skewed the rolling average — a customer who
        // edited their review from 3 stars to 5 stars would silently
        // double-count the job and corrupt the technician's stats.
        val isFirstReviewForThisBooking = booking.rating == 0

        // Update booking review
        val reviewedBooking = booking.copy(rating = rating, reviewComment = comment)
        bookingDao.updateBooking(reviewedBooking)

        val tech = technicianDao.getTechnicianByPhone(currentTechPhone) ?: return

        if (isFirstReviewForThisBooking) {
            // First time this booking has been rated: bump totalJobs and
            // recompute the moving average including the new rating.
            val newCount = tech.totalJobs + 1
            val newRating = ((tech.rating * tech.totalJobs) + rating) / newCount
            technicianDao.updateTechnicianRating(currentTechPhone, newRating, newCount)
        } else {
            // Re-review: replace the OLD rating's contribution with the NEW
            // one in the average, without changing totalJobs at all.
            val oldRating = booking.rating
            val count = tech.totalJobs.coerceAtLeast(1)
            val adjustedRating = ((tech.rating * count) - oldRating + rating) / count
            technicianDao.updateTechnicianRating(currentTechPhone, adjustedRating, tech.totalJobs)
        }

        // Add to earnings only on the first review for this booking, so
        // re-reviewing doesn't pay the technician twice for the same job.
        if (isFirstReviewForThisBooking) {
            val payoutPercent = 0.8 // 80% payout keeps 20% commission for FixNow platform
            val techEarning = booking.price * payoutPercent
            earningDao.insertEarning(
                EarningRecord(
                    technicianPhone = currentTechPhone,
                    bookingId = bookingId,
                    category = booking.serviceCategory,
                    amount = techEarning
                )
            )

            // Check for referral bonus (5% of peer earnings)
            val referredByCode = tech.referredByCode
            if (!referredByCode.isNullOrEmpty()) {
                val bonusAmount = techEarning * 0.05

                val referrerTech = technicianDao.getTechnicianByReferralCode(referredByCode)
                if (referrerTech != null) {
                    val updatedReferralTech = referrerTech.copy(
                        referralEarnings = referrerTech.referralEarnings + bonusAmount
                    )
                    technicianDao.insertTechnician(updatedReferralTech)

                    earningDao.insertEarning(
                        EarningRecord(
                            technicianPhone = referrerTech.phone,
                            bookingId = bookingId,
                            category = "Referral Bonus (from ${tech.name})",
                            amount = bonusAmount
                        )
                    )
                } else {
                    val referrerCust = customerDao.getCustomerByReferralCode(referredByCode)
                    if (referrerCust != null) {
                        val updatedReferrerCust = referrerCust.copy(
                            referralEarnings = referrerCust.referralEarnings + bonusAmount
                        )
                        customerDao.insertCustomer(updatedReferrerCust)
                    }
                }
            }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return sqrt((lat1 - lat2).pow(2.0) + (lon1 - lon2).pow(2.0))
    }
}
