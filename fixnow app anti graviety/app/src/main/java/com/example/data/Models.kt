package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "customers")
data class CustomerProfile(
    @PrimaryKey val phone: String,
    val uuid: String = "", // Supabase Auth UUID
    val name: String,
    val email: String,
    val city: String,
    val referralCode: String = "",
    val referredByCode: String? = null,
    val referralEarnings: Double = 0.0,
    val password: String = ""
) : Serializable

@Entity(tableName = "technicians")
data class TechnicianProfile(
    @PrimaryKey val phone: String,
    val uuid: String = "", // Supabase Auth UUID
    val name: String,
    val category: String, // e.g., "Electrical Services", "AC Services"
    val subCategory: String, // e.g., "Emergency Electrician"
    val city: String, // Lahore, Karachi, Islamabad
    val cnic: String,
    val selfieUrl: String, // Simulated selfie path/uri
    val bankDetails: String, // EasyPaisa or JazzCash account info
    val isApproved: Boolean = false,
    val isOnline: Boolean = false,
    val rating: Double = 4.5,
    val totalJobs: Int = 0,
    val acceptanceRate: Double = 1.0,
    val latitude: Double = 31.5204, // Default Lahore coords
    val longitude: Double = 74.3587,
    val timestamp: Long = System.currentTimeMillis(),
    val referralCode: String = "",
    val referredByCode: String? = null,
    val referralEarnings: Double = 0.0,
    val password: String = "password123"
) : Serializable

@Entity(tableName = "bookings")
data class Booking(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supabaseId: Long? = null, // Remote Supabase id
    val serviceCategory: String,
    val serviceName: String,
    val issueDescription: String,
    val customerId: String = "", // Customer UUID in Supabase
    val customerPhone: String,
    val customerName: String,
    val customerAddress: String,
    val customerCity: String,
    val preferredTime: String,
    val paymentMethod: String, // EasyPaisa, JazzCash, Cash
    val price: Double,
    val status: String, // Requested, Assigned, Technician En Route, Arrived, In Progress, Completed, Cancelled
    val technicianId: String? = null, // Technician UUID in Supabase
    val technicianPhone: String? = null,
    val technicianName: String? = null,
    val rating: Int = 0, // 1 to 5 stars
    val reviewComment: String? = null,
    val latitude: Double = 31.5204, // Customer lat
    val longitude: Double = 74.3587, // Customer long
    val techLatitude: Double = 31.5204, // Live moving coordinates of technician
    val techLongitude: Double = 74.3587,
    val declinedTechnicians: String = "", // Comma-separated list of technician phones who declined
    val isManualAssign: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "earnings")
data class EarningRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val technicianPhone: String,
    val bookingId: Long,
    val category: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

// Simple helper class representing service items for display matching
data class ServiceItem(
    val category: String,
    val name: String,
    val price: Double,
    val iconName: String,
    val description: String
)
