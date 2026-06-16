package com.example

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Booking
import com.example.data.CustomerProfile
import com.example.data.EarningRecord
import com.example.data.TechnicianProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FixNow", appName)
  }

  // ----------------------------------------------------
  // ROBUST ROOM MIGRATION VERIFICATION TEST SUITE
  // ----------------------------------------------------

  @Test
  fun testRoomDatabaseMigrationFromVersion3To5() {
    runBlocking {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val testDbName = "test_migration_suite.db"

    // Delete existing test DB to ensure clean state
    context.deleteDatabase(testDbName)

    // 1. Create SQLite DB helper configured to Version 3 schema
    val dbHelper = object : SQLiteOpenHelper(context, testDbName, null, 3) {
      override fun onCreate(db: SQLiteDatabase) {
        // customers table at Version 3
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS `customers` (
            `phone` TEXT NOT NULL, 
            `name` TEXT NOT NULL, 
            `email` TEXT NOT NULL, 
            `city` TEXT NOT NULL, 
            `referralCode` TEXT NOT NULL, 
            `referredByCode` TEXT, 
            `referralEarnings` REAL NOT NULL, 
            `password` TEXT NOT NULL, 
            PRIMARY KEY(`phone`)
          )
        """)

        // technicians table at Version 3
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS `technicians` (
            `phone` TEXT NOT NULL, 
            `name` TEXT NOT NULL, 
            `category` TEXT NOT NULL, 
            `subCategory` TEXT NOT NULL, 
            `city` TEXT NOT NULL, 
            `cnic` TEXT NOT NULL, 
            `selfieUrl` TEXT NOT NULL, 
            `bankDetails` TEXT NOT NULL, 
            `isApproved` INTEGER NOT NULL, 
            `isOnline` INTEGER NOT NULL, 
            `rating` REAL NOT NULL, 
            `totalJobs` INTEGER NOT NULL, 
            `acceptanceRate` REAL NOT NULL, 
            `latitude` REAL NOT NULL, 
            `longitude` REAL NOT NULL, 
            `timestamp` INTEGER NOT NULL, 
            `referralCode` TEXT NOT NULL, 
            `referredByCode` TEXT, 
            `referralEarnings` REAL NOT NULL, 
            PRIMARY KEY(`phone`)
          )
        """)

        // bookings table at Version 3
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS `bookings` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
            `serviceCategory` TEXT NOT NULL, 
            `serviceName` TEXT NOT NULL, 
            `issueDescription` TEXT NOT NULL, 
            `customerPhone` TEXT NOT NULL, 
            `customerName` TEXT NOT NULL, 
            `customerAddress` TEXT NOT NULL, 
            `customerCity` TEXT NOT NULL, 
            `preferredTime` TEXT NOT NULL, 
            `paymentMethod` TEXT NOT NULL, 
            `price` REAL NOT NULL, 
            `status` TEXT NOT NULL, 
            `technicianPhone` TEXT, 
            `technicianName` TEXT, 
            `rating` INTEGER NOT NULL, 
            `reviewComment` TEXT, 
            `latitude` REAL NOT NULL, 
            `longitude` REAL NOT NULL, 
            `declinedTechnicians` TEXT NOT NULL, 
            `isManualAssign` INTEGER NOT NULL, 
            `timestamp` INTEGER NOT NULL
          )
        """)

        // earnings table at Version 3 (identical columns with v5)
        db.execSQL("""
          CREATE TABLE IF NOT EXISTS `earnings` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
            `technicianPhone` TEXT NOT NULL, 
            `bookingId` INTEGER NOT NULL, 
            `category` TEXT NOT NULL, 
            `amount` REAL NOT NULL, 
            `timestamp` INTEGER NOT NULL
          )
        """)
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    // 2. Insert Version 3 test data to replicate an existing, in-use installation
    val sqliteDb = dbHelper.writableDatabase

    // Insert customer row
    val custValues = ContentValues().apply {
      put("phone", "03001234567")
      put("name", "Syed Kamal")
      put("email", "syed.kamal@gmail.com")
      put("city", "Karachi")
      put("referralCode", "KAMAL-1122")
      put("referredByCode", null as String?)
      put("referralEarnings", 0.0)
      put("password", "safePass")
    }
    sqliteDb.insert("customers", null, custValues)

    // Insert technician row
    val techValues = ContentValues().apply {
      put("phone", "03339876543")
      put("name", "Kamran Khan")
      put("category", "AC Services")
      put("subCategory", "AC Repair & Servicing")
      put("city", "Lahore")
      put("cnic", "35202-9876543-3")
      put("selfieUrl", "selfie_kamran")
      put("bankDetails", "JazzCash - 03339876543")
      put("isApproved", 1)
      put("isOnline", 1)
      put("rating", 4.7)
      put("totalJobs", 29)
      put("acceptanceRate", 1.0)
      put("latitude", 31.5580)
      put("longitude", 74.3800)
      put("timestamp", System.currentTimeMillis())
      put("referralCode", "KAMRAN-6543")
      put("referredByCode", null as String?)
      put("referralEarnings", 0.0)
    }
    sqliteDb.insert("technicians", null, techValues)

    // Insert booking row
    val bookingValues = ContentValues().apply {
      put("id", 101L)
      put("serviceCategory", "AC Services")
      put("serviceName", "AC Split Master Servicing")
      put("issueDescription", "Needs deep cleaning and general checkup before summer.")
      put("customerPhone", "03001234567")
      put("customerName", "Syed Kamal")
      put("customerAddress", "Flat 4B, Clifton Apartments")
      put("customerCity", "Karachi")
      put("preferredTime", "15:00")
      put("paymentMethod", "Cash")
      put("price", 2400.0)
      put("status", "Requested")
      put("technicianPhone", "03339876543")
      put("technicianName", "Kamran Khan")
      put("rating", 0)
      put("reviewComment", null as String?)
      put("latitude", 24.8607)
      put("longitude", 67.0011)
      put("declinedTechnicians", "")
      put("isManualAssign", 0)
      put("timestamp", System.currentTimeMillis())
    }
    sqliteDb.insert("bookings", null, bookingValues)

    // Insert earning row
    val earningValues = ContentValues().apply {
      put("id", 801L)
      put("technicianPhone", "03339876543")
      put("bookingId", 101L)
      put("category", "AC Split Master Servicing")
      put("amount", 2400.0)
      put("timestamp", System.currentTimeMillis())
    }
    sqliteDb.insert("earnings", null, earningValues)

    // Verify raw count
    val count = sqliteDb.compileStatement("SELECT count(*) FROM customers").simpleQueryForLong()
    assertEquals(1L, count)

    // Close SQLiteDatabase
    sqliteDb.close()
    dbHelper.close()

    // 3. Open Database with Room at version 5, applying migrations MIGRATION_3_4 & MIGRATION_4_5
    val roomDatabase = Room.databaseBuilder(
      context,
      AppDatabase::class.java,
      testDbName
    )
    .addMigrations(AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
    .build()

    // 4. Query & Validate via Room DAOs to verify all data has been perfectly migrated and preserved!
    val migratedCustomer = roomDatabase.customerDao().getCustomerByPhone("03001234567")
    assertNotNull(migratedCustomer)
    assertEquals("Syed Kamal", migratedCustomer?.name)
    assertEquals("03001234567", migratedCustomer?.phone)
    assertEquals("Karachi", migratedCustomer?.city)
    assertEquals("safePass", migratedCustomer?.password)
    // Verify default value of the brand new `uuid` column from Migrations is correctly empty string
    assertEquals("", migratedCustomer?.uuid)

    val migratedTechnician = roomDatabase.technicianDao().getTechnicianByPhone("03339876543")
    assertNotNull(migratedTechnician)
    assertEquals("Kamran Khan", migratedTechnician?.name)
    assertEquals("AC Services", migratedTechnician?.category)
    assertEquals("Lahore", migratedTechnician?.city)
    // Verify default value of the brand new `uuid` column from Migrations is correctly empty string
    assertEquals("", migratedTechnician?.uuid)

    val migratedBooking = roomDatabase.bookingDao().getBookingById(101L)
    assertNotNull(migratedBooking)
    assertEquals("AC Split Master Servicing", migratedBooking?.serviceName)
    assertEquals("03001234567", migratedBooking?.customerPhone)
    assertEquals("Cash", migratedBooking?.paymentMethod)
    // Verify migrating new columns carry correct defaults
    assertEquals(null, migratedBooking?.supabaseId)
    assertEquals("", migratedBooking?.customerId)
    assertEquals(null, migratedBooking?.technicianId)
    assertEquals(31.5204, migratedBooking?.techLatitude ?: 0.0, 0.0001)
    assertEquals(74.3587, migratedBooking?.techLongitude ?: 0.0, 0.0001)

    // Earnings check
    val earningsList = roomDatabase.earningDao().getEarningsFlow("03339876543").first()
    assertEquals(1, earningsList.size)
    val migratedEarning = earningsList[0]
    assertEquals(801L, migratedEarning.id)
    assertEquals(101L, migratedEarning.bookingId)
    assertEquals(2400.0, migratedEarning.amount, 0.01)

    // 5. Cleanup the database
    roomDatabase.close()
    context.deleteDatabase(testDbName)
    }
  }
}

