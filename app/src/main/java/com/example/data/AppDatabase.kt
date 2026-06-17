package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomerProfile::class,
        TechnicianProfile::class,
        Booking::class,
        EarningRecord::class
    ],
    version = 7, // FIX: bumped from 6 → 7 to include MIGRATION_6_7
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun technicianDao(): TechnicianDao
    abstract fun bookingDao(): BookingDao
    abstract fun earningDao(): EarningDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE technicians ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN supabaseId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE bookings ADD COLUMN customerId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE bookings ADD COLUMN technicianId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE bookings ADD COLUMN techLatitude REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE bookings ADD COLUMN techLongitude REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe no-op between v4 and v5
            }
        }

        // FIX: Added MIGRATION_5_6 — was completely missing, causing crash on
        // any device upgrading from DB v5. Adds columns that exist in entities
        // but were never ALTER TABLE'd (referredByCode, referralEarnings,
        // acceptanceRate, password).
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN referredByCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE customers ADD COLUMN referralEarnings REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE customers ADD COLUMN password TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE technicians ADD COLUMN referredByCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE technicians ADD COLUMN referralEarnings REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE technicians ADD COLUMN acceptanceRate REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE technicians ADD COLUMN password TEXT NOT NULL DEFAULT ''")
            }
        }

        // FIX: MIGRATION_6_7 — resets techLatitude/techLongitude default to 0.0
        // (was 31.5204/74.3587) so new bookings don't show a technician
        // pre-positioned at the customer's door before any tech accepts the job.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQLite doesn't support ALTER COLUMN; update defaults via a temp table rebuild
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS bookings_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        supabaseId INTEGER,
                        serviceCategory TEXT NOT NULL,
                        serviceName TEXT NOT NULL,
                        issueDescription TEXT NOT NULL,
                        customerId TEXT NOT NULL DEFAULT '',
                        customerPhone TEXT NOT NULL,
                        customerName TEXT NOT NULL,
                        customerAddress TEXT NOT NULL,
                        customerCity TEXT NOT NULL,
                        preferredTime TEXT NOT NULL,
                        paymentMethod TEXT NOT NULL,
                        price REAL NOT NULL,
                        status TEXT NOT NULL,
                        technicianId TEXT,
                        technicianPhone TEXT,
                        technicianName TEXT,
                        rating INTEGER NOT NULL DEFAULT 0,
                        reviewComment TEXT,
                        latitude REAL NOT NULL DEFAULT 0.0,
                        longitude REAL NOT NULL DEFAULT 0.0,
                        techLatitude REAL NOT NULL DEFAULT 0.0,
                        techLongitude REAL NOT NULL DEFAULT 0.0,
                        declinedTechnicians TEXT NOT NULL DEFAULT '',
                        isManualAssign INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO bookings_new SELECT
                        id, supabaseId, serviceCategory, serviceName, issueDescription,
                        customerId, customerPhone, customerName, customerAddress, customerCity,
                        preferredTime, paymentMethod, price, status,
                        technicianId, technicianPhone, technicianName, rating, reviewComment,
                        latitude, longitude,
                        0.0, 0.0,
                        declinedTechnicians, isManualAssign
                    FROM bookings
                """.trimIndent())
                db.execSQL("DROP TABLE bookings")
                db.execSQL("ALTER TABLE bookings_new RENAME TO bookings")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbContext = context.applicationContext
                val instance = Room.databaseBuilder(
                    dbContext,
                    AppDatabase::class.java,
                    "fixnow_database"
                )
                // FIX: All 4 migrations registered. Removed fallbackToDestructiveMigration()
                // which was silently wiping all user data on any migration gap.
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()

                INSTANCE = instance

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val customerDao = instance.customerDao()
                        val hasCustomer = customerDao.getCustomerByPhone("03001234567")
                        if (hasCustomer == null) {
                            seedDatabase(instance)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                instance
            }
        }

        private suspend fun seedDatabase(db: AppDatabase) {
            val customerDao = db.customerDao()
            val techDao = db.technicianDao()
            val earningDao = db.earningDao()

            customerDao.insertCustomer(
                CustomerProfile(
                    phone = "03001234567",
                    name = "Ahmad Malik",
                    email = "ahmad.malik@gmail.com",
                    city = "Lahore",
                    referralCode = "AHMAD-4567",
                    password = "password123"
                )
            )

            val initialTechs = listOf(
                TechnicianProfile(
                    phone = "03214567890",
                    name = "Muhammad Rizwan",
                    category = "Electrical Services",
                    subCategory = "Emergency Electrician (24/7)",
                    city = "Lahore",
                    cnic = "35201-1234567-1",
                    selfieUrl = "selfie_rizwan",
                    bankDetails = "EasyPaisa - 03214567890",
                    isApproved = true,
                    isOnline = true,
                    rating = 4.8,
                    totalJobs = 42,
                    latitude = 31.5204,
                    longitude = 74.3587,
                    referralCode = "RIZWAN-7890"
                ),
                TechnicianProfile(
                    phone = "03339876543",
                    name = "Kamran Khan",
                    category = "AC Services",
                    subCategory = "AC Repair & Servicing",
                    city = "Lahore",
                    cnic = "35202-9876543-3",
                    selfieUrl = "selfie_kamran",
                    bankDetails = "JazzCash - 03339876543",
                    isApproved = true,
                    isOnline = true,
                    rating = 4.7,
                    totalJobs = 29,
                    latitude = 31.5580,
                    longitude = 74.3800,
                    referralCode = "KAMRAN-6543"
                ),
                TechnicianProfile(
                    phone = "03123456789",
                    name = "Zahid Hussain",
                    category = "Plumbing Services",
                    subCategory = "Drain Blockage & Pipe Leak",
                    city = "Karachi",
                    cnic = "42201-5554321-5",
                    selfieUrl = "selfie_zahid",
                    bankDetails = "EasyPaisa - 03123456789",
                    isApproved = true,
                    isOnline = true,
                    rating = 4.6,
                    totalJobs = 33,
                    latitude = 24.8607,
                    longitude = 67.0011,
                    referralCode = "ZAHID-6789"
                ),
                TechnicianProfile(
                    phone = "03451112222",
                    name = "Sajid Mahmood",
                    category = "Generator & UPS Services",
                    subCategory = "UPS / Inverter Installation",
                    city = "Lahore",
                    cnic = "34101-7778889-1",
                    selfieUrl = "selfie_sajid",
                    bankDetails = "UBL Bank - 00213344",
                    isApproved = false,
                    isOnline = false,
                    rating = 4.5,
                    totalJobs = 15,
                    latitude = 31.4800,
                    longitude = 74.3200,
                    referralCode = "SAJID-2222"
                ),
                TechnicianProfile(
                    phone = "03009998888",
                    name = "Bilal Siddiqui",
                    category = "Cleaning & Maintenance",
                    subCategory = "Sofa & Carpet Deep Cleaning",
                    city = "Islamabad",
                    cnic = "37405-1212121-7",
                    selfieUrl = "selfie_bilal",
                    bankDetails = "EasyPaisa - 03009998888",
                    isApproved = true,
                    isOnline = true,
                    rating = 4.9,
                    totalJobs = 50,
                    latitude = 33.6844,
                    longitude = 73.0479,
                    referralCode = "BILAL-8888"
                )
            )

            for (tech in initialTechs) {
                techDao.insertTechnician(tech)
            }

            val initialEarnings = listOf(
                EarningRecord(
                    technicianPhone = "03339876543",
                    bookingId = 9001,
                    category = "AC Split Master Servicing",
                    amount = 2400.0,
                    timestamp = System.currentTimeMillis() - 86400000L * 2
                ),
                EarningRecord(
                    technicianPhone = "03339876543",
                    bookingId = 9002,
                    category = "Inverter Leakage Gas Refill",
                    amount = 3800.0,
                    timestamp = System.currentTimeMillis() - 86400000L
                ),
                EarningRecord(
                    technicianPhone = "03339876543",
                    bookingId = 9003,
                    category = "Compressor Capacitor Swap",
                    amount = 1850.0,
                    timestamp = System.currentTimeMillis()
                ),
                EarningRecord(
                    technicianPhone = "03214567890",
                    bookingId = 9004,
                    category = "Emergency Power Outage Line Fix",
                    amount = 1500.0,
                    timestamp = System.currentTimeMillis() - 86400000L
                ),
                EarningRecord(
                    technicianPhone = "03123456789",
                    bookingId = 9005,
                    category = "Master Bathroom Drain Flow Clear",
                    amount = 2200.0,
                    timestamp = System.currentTimeMillis() - 86400000L
                )
            )
            for (earning in initialEarnings) {
                earningDao.insertEarning(earning)
            }
        }
    }
}
