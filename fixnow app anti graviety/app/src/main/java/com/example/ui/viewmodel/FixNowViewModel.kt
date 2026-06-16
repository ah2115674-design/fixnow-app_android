package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class FixNowViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = FixNowRepository(
        customerDao = database.customerDao(),
        technicianDao = database.technicianDao(),
        bookingDao = database.bookingDao(),
        earningDao = database.earningDao()
    )

    // Google Maps API Live State Properties for routing and geocoding
    val searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val resolvedLocationLat = MutableStateFlow<Double?>(null)
    val resolvedLocationLng = MutableStateFlow<Double?>(null)

    val activeRoutePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val activeRouteDistance = MutableStateFlow("TBD")
    val activeRouteDuration = MutableStateFlow("Estimating...")

    // Mode: "Onboarding", "Customer", "Technician", "Admin"
    private val _currentMode = MutableStateFlow("Onboarding")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    // Active notifications stream simulating FCM & WhatsApp push messages
    private val _notifications = MutableStateFlow<List<String>>(emptyList())
    val notifications: StateFlow<List<String>> = _notifications.asStateFlow()

    // Shared State & Seeding Logs
    val technicians: StateFlow<List<TechnicianProfile>> = repository.allTechnicians
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookings: StateFlow<List<Booking>> = repository.allBookings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerProfile>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ----------------------------------------------------
    // CUSTOMER MODE STATE
    // ----------------------------------------------------
    private val _customerPhone = MutableStateFlow("") // Starts empty so user logs in/registers first
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    private val _activeCustomer = MutableStateFlow<CustomerProfile?>(null)
    val activeCustomer: StateFlow<CustomerProfile?> = _activeCustomer.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Booking Wizard Draft
    private val _draftServiceName = MutableStateFlow("")
    val draftServiceName: StateFlow<String> = _draftServiceName.asStateFlow()

    private val _draftPrice = MutableStateFlow(1200.0)
    val draftPrice: StateFlow<Double> = _draftPrice.asStateFlow()

    val draftDescription = MutableStateFlow("")
    val draftAddress = MutableStateFlow("House 12-A, Block H, Gulberg III, Lahore")
    val draftUseLiveLocation = MutableStateFlow(false)
    val draftCity = MutableStateFlow("Lahore")
    val draftTimeSlot = MutableStateFlow("Immediate (30-45 mins)")
    val draftPaymentMethod = MutableStateFlow("EasyPaisa")

    private val _isSubmittingBooking = MutableStateFlow(false)
    val isSubmittingBooking: StateFlow<Boolean> = _isSubmittingBooking.asStateFlow()

    // ----------------------------------------------------
    // TECHNICIAN MODE STATE & LOGIN FLOW
    // ----------------------------------------------------
    private val _techPhone = MutableStateFlow("") // Starts empty to force login first
    val techPhone: StateFlow<String> = _techPhone.asStateFlow()

    private val _techLoginError = MutableStateFlow<String?>(null)
    val techLoginError: StateFlow<String?> = _techLoginError.asStateFlow()

    private val _activeTechnician = MutableStateFlow<TechnicianProfile?>(null)
    val activeTechnician: StateFlow<TechnicianProfile?> = _activeTechnician.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val techEarnings: StateFlow<List<EarningRecord>> = _techPhone
        .flatMapLatest { phone ->
            if (phone.isEmpty()) flowOf(emptyList())
            else repository.getEarningsFlow(phone)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Registration Form Inputs
    val regName = MutableStateFlow("")
    val regPhone = MutableStateFlow("")
    val regCNIC = MutableStateFlow("")
    val regPassword = MutableStateFlow("")
    val regCity = MutableStateFlow("Lahore")
    val regCategory = MutableStateFlow("Electrical Services")
    val regSubCat = MutableStateFlow("Wiring & Rewiring")
    val regBankDetails = MutableStateFlow("EasyPaisa - ")
    val regSelfieUrl = MutableStateFlow("mock_selfie_url")
    val regReferredByCode = MutableStateFlow("")

    private fun generateReferralCode(name: String, phone: String, prefix: String): String {
        val cleanName = name.trim().filter { it.isLetter() }.uppercase()
        val displayName = if (cleanName.isEmpty()) "MEMBER" else cleanName.take(6)
        val cleanPhone = phone.trim().filter { it.isDigit() }
        val suffix = if (cleanPhone.length >= 4) cleanPhone.takeLast(4) else "4321"
        return "$prefix-$displayName-$suffix"
    }

    private val _isRegSuccess = MutableStateFlow(false)
    val isRegSuccess: StateFlow<Boolean> = _isRegSuccess.asStateFlow()

    // Coordinates increment simulation for live tracking map
    private val _currentSimulatedBooking = MutableStateFlow<Booking?>(null)
    val currentSimulatedBooking: StateFlow<Booking?> = _currentSimulatedBooking.asStateFlow()

    // ----------------------------------------------------
    // ADMIN PANEL CONTROL & AUTHENTICATION SPACE
    // ----------------------------------------------------
    private val _adminTab = MutableStateFlow("Technicians")
    val adminTab: StateFlow<String> = _adminTab.asStateFlow()

    private val _isAdminAuthorized = MutableStateFlow(false)
    val isAdminAuthorized: StateFlow<Boolean> = _isAdminAuthorized.asStateFlow()

    private val _adminAuthError = MutableStateFlow<String?>(null)
    val adminAuthError: StateFlow<String?> = _adminAuthError.asStateFlow()

    val adminPasscodeInput = MutableStateFlow("")

    init {
        // Setup Realtime websocket callbacks
        SupabaseRealtimeClient.setBookingCallback { bookingId, status, techLat, techLng ->
            viewModelScope.launch {
                try {
                    val existing = repository.allBookings.first().firstOrNull { it.supabaseId == bookingId } ?: repository.getBooking(bookingId)
                    if (existing != null) {
                        val updated = existing.copy(
                            status = status,
                            techLatitude = techLat ?: existing.techLatitude,
                            techLongitude = techLng ?: existing.techLongitude
                        )
                        repository.updateBooking(updated)
                        addPushNotification("🔔 Realtime update: Booking #${existing.id} set to $status")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        SupabaseRealtimeClient.setTechCallback { phone, lat, lng, isOnline ->
            viewModelScope.launch {
                try {
                    val existing = repository.getTechnician(phone)
                    if (existing != null) {
                        val updated = existing.copy(
                            latitude = lat,
                            longitude = lng,
                            isOnline = isOnline
                        )
                        repository.registerTechnician(updated)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Connect WebSocket. Use the restored session's JWT (if any) so Realtime's
        // postgres_changes subscriptions evaluate RLS as this authenticated user
        // rather than the anon role (which has no row-level visibility).
        try {
            val restoredToken = SecureAuthManager.getInstance(application).userToken.value
            SupabaseRealtimeClient.connect(restoredToken)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Automatically check if Ahmad Malik is pre-filled on startup
        loadCustomerInfo()
        loadTechnicianInfo()
        listenToActiveBookings()

        // Secure Session Restoration Startup Logic
        viewModelScope.launch {
            try {
                val authManager = SecureAuthManager.getInstance(application)
                val savedToken = authManager.userToken.value
                val savedRole = authManager.userRole.value
                val savedUid = authManager.userId.value

                if (!savedToken.isNullOrEmpty() && !savedRole.isNullOrEmpty() && !savedUid.isNullOrEmpty()) {
                    addPushNotification("🔒 Securing connection... Restoring credentials for $savedRole")
                    if (savedRole == "customer") {
                        val profile = repository.allCustomers.first().firstOrNull { it.uuid == savedUid }
                        if (profile != null) {
                            _customerPhone.value = profile.phone
                            _activeCustomer.value = profile
                            _currentMode.value = "Customer"
                            addPushNotification("🔓 Session restored! Welcome back, ${profile.name}.")
                        } else {
                            val localMatch = repository.allCustomers.first().firstOrNull()
                            if (localMatch != null) {
                                _customerPhone.value = localMatch.phone
                                _activeCustomer.value = localMatch
                                _currentMode.value = "Customer"
                                addPushNotification("🔓 Session restored! Welcome back, ${localMatch.name}.")
                            }
                        }
                    } else if (savedRole == "technician") {
                        val profile = repository.allTechnicians.first().firstOrNull { it.uuid == savedUid }
                        if (profile != null) {
                            _techPhone.value = profile.phone
                            _activeTechnician.value = profile
                            _currentMode.value = "Technician"
                            addPushNotification("🔓 Session restored! Welcome back, ${profile.name}.")
                        } else {
                            val localMatch = repository.allTechnicians.first().firstOrNull()
                            if (localMatch != null) {
                                _techPhone.value = localMatch.phone
                                _activeTechnician.value = localMatch
                                _currentMode.value = "Technician"
                                addPushNotification("🔓 Session restored! Welcome back, ${localMatch.name}.")
                            }
                        }
                    } else if (savedRole == "admin") {
                        _isAdminAuthorized.value = true
                        _currentMode.value = "Admin"
                        addPushNotification("🔓 Session restored! Welcome back, Administrator.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun switchMode(mode: String) {
        _currentMode.value = mode
        // Reload context profile when mode switching
        if (mode == "Customer") {
            loadCustomerInfo()
        } else if (mode == "Technician") {
            loadTechnicianInfo()
        }
    }

    private fun loadCustomerInfo() {
        viewModelScope.launch {
            val phone = _customerPhone.value
            if (phone.isEmpty()) {
                _activeCustomer.value = null
                return@launch
            }
            val profile = repository.getCustomer(phone)
            _activeCustomer.value = profile
        }
    }

    fun setCustomerPhone(phone: String) {
        _customerPhone.value = phone
        loadCustomerInfo()
    }

    val googleSessionUid = MutableStateFlow<String?>(null)
    val googleSessionToken = MutableStateFlow<String?>(null)
    val googleSessionEmail = MutableStateFlow<String?>(null)
    val googleSessionName = MutableStateFlow<String?>(null)

    fun loginOrCreateCustomer(name: String, phone: String, city: String, referredBy: String = "", passwordEntered: String = "") {
        viewModelScope.launch {
            val cleanReferredBy = referredBy.trim().uppercase()
            var referredByCode: String? = null
            if (cleanReferredBy.isNotEmpty()) {
                val existsTech = technicians.value.any { it.referralCode == cleanReferredBy }
                val existsCust = repository.allCustomers.first().any { it.referralCode == cleanReferredBy }
                if (existsTech || existsCust) {
                    referredByCode = cleanReferredBy
                    addPushNotification("🎁 Referral Code applied! Both you and referrer are now linked.")
                } else {
                    addPushNotification("⚠️ Referral code '$cleanReferredBy' was not found, starting standard account.")
                }
            }

            val gUid = googleSessionUid.value
            val gToken = googleSessionToken.value
            val gEmail = googleSessionEmail.value

            if (!gUid.isNullOrEmpty() && !gToken.isNullOrEmpty() && !gEmail.isNullOrEmpty()) {
                // Google Linked Account Setup Complete! Update profile in Supabase & Room.
                try {
                    addPushNotification("🔗 Linking mobile number to your Google Account on server...")
                    val bearerHeader = "Bearer $gToken"
                    val updateMap = mapOf(
                        "phone" to phone.trim(),
                        "city" to city
                    )
                    val updateResponse = SupabaseClient.apiService.updateCustomerProfile(
                        id = "eq.$gUid",
                        body = updateMap,
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        authHeader = bearerHeader
                    )
                    
                    if (updateResponse.isSuccessful) {
                        val referral = generateReferralCode(name, phone, "CUST")
                        val customer = CustomerProfile(
                            phone = phone.trim(),
                            uuid = gUid,
                            name = name,
                            email = gEmail,
                            city = city,
                            referralCode = referral,
                            referredByCode = referredByCode
                        )
                        // Save to Room local DB
                        repository.registerCustomer(customer)
                        _customerPhone.value = phone.trim()
                        _activeCustomer.value = customer
                        addPushNotification("🟢 Phone linked! Welcome to FixNow, $name!")
                        
                        // Clear temp google states
                        googleSessionUid.value = null
                        googleSessionToken.value = null
                        googleSessionEmail.value = null
                        googleSessionName.value = null
                    } else {
                        addPushNotification("❌ Failed to link phone number on server.")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    addPushNotification("⚠️ Connection failed during phone linkage.")
                }
                return@launch
            }

            val emailStr = if (name.contains("@")) name.trim() else "${phone.trim()}@fixnow.com"
            val rawPassword = passwordEntered.trim()
            val finalPassword = if (rawPassword.length >= 6) rawPassword else "FixNow_${phone.trim()}_pass"

            var supabaseUserId = ""
            var token = ""
            var syncedNameFromAuth: String? = null
            var syncedCityFromAuth: String? = null

            // 1. Try to login
            try {
                addPushNotification("🔒 Connecting to Supabase Auth...")
                val loginResponse = SupabaseClient.apiService.signIn(
                    SupabaseSignInRequest(emailStr, finalPassword),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (loginResponse.isSuccessful && loginResponse.body()?.accessToken != null) {
                    supabaseUserId = loginResponse.body()?.user?.id ?: ""
                    token = loginResponse.body()?.accessToken ?: ""
                    
                    val meta = loginResponse.body()?.user?.userMetadata
                    syncedNameFromAuth = meta?.get("name")
                    syncedCityFromAuth = meta?.get("city")
                    
                    addPushNotification("🟢 Supabase Login successful! Authenticated.")
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "customer", supabaseUserId)
                    SupabaseRealtimeClient.connect(token)
                } else {
                    // Try code-based fallback or signup
                    addPushNotification("🔑 User not registered on Supabase, creating auth credentials...")
                    val signUpResponse = SupabaseClient.apiService.customerSignUp(
                        SupabaseCustomerSignUpRequest(
                            email = emailStr,
                            password = finalPassword,
                            options = CustomerSignUpOptions(
                                data = CustomerMetadata(name = name, phone = phone, city = city)
                            )
                        ),
                        BuildConfig.SUPABASE_ANON_KEY
                    )
                    
                    if (signUpResponse.isSuccessful && signUpResponse.body()?.user != null) {
                        supabaseUserId = signUpResponse.body()?.user?.id ?: ""
                        token = signUpResponse.body()?.accessToken ?: ""
                        
                        val meta = signUpResponse.body()?.user?.userMetadata
                        syncedNameFromAuth = meta?.get("name")
                        syncedCityFromAuth = meta?.get("city")
                        
                        addPushNotification("🎉 Account created on Supabase Auth!")
                        if (token.isNotEmpty()) {
                            SecureAuthManager.getInstance(getApplication()).saveSession(token, "customer", supabaseUserId)
                            SupabaseRealtimeClient.connect(token)
                            // Now insert the profile row into the customers table
                            try {
                                val bearerHeader = "Bearer $token"
                                val code = generateReferralCode(name, phone, "CUST")
                                val insertDto = CustomerInsertDto(
                                    id = supabaseUserId,
                                    phone = phone.trim(),
                                    name = name,
                                    email = emailStr,
                                    city = city,
                                    referralCode = code,
                                    referredByCode = referredByCode
                                )
                                val insertResp = SupabaseClient.apiService.createCustomer(
                                    customer = insertDto,
                                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                    authHeader = bearerHeader
                                )
                                if (insertResp.isSuccessful) {
                                    addPushNotification("🟢 Customer profile saved to Supabase database!")
                                } else {
                                    addPushNotification("⚠️ Profile row insert failed: ${insertResp.errorBody()?.string()}")
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                addPushNotification("⚠️ Network error saving profile: ${e.message}")
                            }
                        }
                    } else {
                        addPushNotification("⚠️ Supabase Auth offline or failed. Proceeding locally.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Connection timed out, running in Room SQLite offline mode.")
            }

            var existing = repository.getCustomer(phone)
            val finalName = if (!syncedNameFromAuth.isNullOrEmpty()) syncedNameFromAuth else name
            val finalCity = if (!syncedCityFromAuth.isNullOrEmpty()) syncedCityFromAuth else city

            if (existing == null) {
                try {
                    addPushNotification("🔍 Scanning cloud for Customer profile sync...")
                    val bearerToUse = if (token.isNotEmpty()) "Bearer $token" else "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                    val matchedCusts = SupabaseClient.apiService.getCustomer(
                        phone = "eq.${phone.trim()}",
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        authHeader = bearerToUse
                    )
                    if (matchedCusts.isNotEmpty()) {
                        val dto = matchedCusts[0]
                        val customer = CustomerProfile(
                            phone = dto.phone,
                            uuid = dto.id,
                            name = dto.name,
                            email = dto.email,
                            city = dto.city,
                            password = finalPassword
                        )
                        repository.registerCustomer(customer)
                        existing = customer
                        addPushNotification("🟢 Synced profile for '${dto.name}' from cloud!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (existing == null && supabaseUserId.isNotEmpty()) {
                val code = generateReferralCode(finalName, phone, "CUST")
                val customer = CustomerProfile(
                    phone = phone,
                    uuid = supabaseUserId,
                    name = finalName,
                    email = emailStr,
                    city = finalCity,
                    referralCode = code,
                    referredByCode = referredByCode,
                    password = if (rawPassword.isNotEmpty()) rawPassword else finalPassword
                )
                repository.registerCustomer(customer)
                existing = customer
                addPushNotification("🟢 Restored profile for '$finalName' from secure auth metadata!")
            }

            if (existing != null) {
                // If the existing account has a password, enforce verification
                val isPasswordCorrect = if (supabaseUserId.isNotEmpty()) {
                    true // If successfully logged in via Supabase Auth, we can trust it
                } else if (existing.password.isEmpty()) {
                    true
                } else {
                    existing.password == rawPassword || existing.password == finalPassword
                }

                if (!isPasswordCorrect) {
                     addPushNotification("❌ Access Denied: Incorrect password specified for phone $phone.")
                     return@launch
                }
                
                val code = if (existing.referralCode.isEmpty()) generateReferralCode(finalName, phone, "CUST") else existing.referralCode
                val refBy = if (existing.referredByCode.isNullOrEmpty()) referredByCode else existing.referredByCode
                // Update/Preserve password
                val updatedPass = if (existing.password.isEmpty()) {
                    if (rawPassword.isNotEmpty()) rawPassword else finalPassword
                } else {
                    existing.password
                }
                val customer = existing.copy(
                    uuid = if (supabaseUserId.isNotEmpty()) supabaseUserId else existing.uuid,
                    name = if (finalName.startsWith("User_") && existing.name.isNotEmpty()) existing.name else finalName,
                    city = finalCity,
                    referralCode = code,
                    referredByCode = refBy,
                    password = updatedPass
                )
                repository.registerCustomer(customer)
                _customerPhone.value = phone
                _activeCustomer.value = customer
                addPushNotification("Welcome back, ${customer.name}! Access approved.")
            } else {
                val code = generateReferralCode(finalName, phone, "CUST")
                val customer = CustomerProfile(
                    phone = phone,
                    uuid = supabaseUserId,
                    name = finalName,
                    email = emailStr,
                    city = finalCity,
                    referralCode = code,
                    referredByCode = referredByCode,
                    password = if (rawPassword.isNotEmpty()) rawPassword else finalPassword
                )
                repository.registerCustomer(customer)
                _customerPhone.value = phone
                _activeCustomer.value = customer
                addPushNotification("Welcome to FixNow, ${customer.name}! Registered in ${customer.city}. Profile secured with password.")
            }
        }
    }

    private fun loadTechnicianInfo() {
        viewModelScope.launch {
            val profile = repository.getTechnician(_techPhone.value)
            _activeTechnician.value = profile
        }
    }

    fun setTechnicianPhone(phone: String) {
        _techPhone.value = phone
        loadTechnicianInfo()
    }

    fun toggleTechnicianOnline(online: Boolean) {
        viewModelScope.launch {
            val phone = _techPhone.value
            repository.updateTechnicianOnlineStatus(phone, online)
            loadTechnicianInfo()
            val statusStr = if (online) "Online" else "Offline"
            addPushNotification("Availability status set to $statusStr.")
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun selectSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDraftService(name: String, category: String, basePrice: Double) {
        _draftServiceName.value = name
        _selectedCategory.value = category
        _draftPrice.value = basePrice
    }

    fun registerNewTechnician() {
        viewModelScope.launch {
            if (regName.value.isEmpty() || regPhone.value.isEmpty() || regCNIC.value.isEmpty() || regPassword.value.isEmpty()) {
                addPushNotification("❌ Missing fields: Name, Phone, CNIC, or Password required.")
                return@launch
            }

            if (regPassword.value.length < 4) {
                addPushNotification("❌ Password must be at least 4 characters long.")
                return@launch
            }

            val cleanReferredBy = regReferredByCode.value.trim().uppercase()
            var referredByCode: String? = null
            if (cleanReferredBy.isNotEmpty()) {
                val existsTech = technicians.value.any { it.referralCode == cleanReferredBy }
                val existsCust = repository.allCustomers.first().any { it.referralCode == cleanReferredBy }
                if (existsTech || existsCust) {
                    referredByCode = cleanReferredBy
                    addPushNotification("🎁 Referral Code applied! Both you and referrer are now linked.")
                } else {
                    addPushNotification("⚠️ Referral code '$cleanReferredBy' was not found, starting standard technician application.")
                }
            }
            
            // Resolve baseline city coordinates for accurate map plotting
            val isKarachi = regCity.value.contains("Karachi", ignoreCase = true)
            val isIslamabad = regCity.value.contains("Islamabad", ignoreCase = true) || regCity.value.contains("Rawalpindi", ignoreCase = true)
            val baseLat = if (isKarachi) 24.8607 else if (isIslamabad) 33.6844 else 31.5204
            val baseLng = if (isKarachi) 67.0011 else if (isIslamabad) 73.0479 else 74.3587

            val code = generateReferralCode(regName.value, regPhone.value, "TECH")
            val emailStr = "${regPhone.value.trim()}@fixnow.com"
            val finalPassword = regPassword.value.trim()

            var supabaseUserId = ""

            // Register Auth in Supabase
            try {
                addPushNotification("🔒 Creating secure technician credentials in Supabase Auth...")
                val techResponse = SupabaseClient.apiService.techSignUp(
                    SupabaseTechSignUpRequest(
                        email = emailStr,
                        password = finalPassword,
                        options = TechSignUpOptions(
                            data = TechMetadata(
                                name = regName.value,
                                phone = regPhone.value,
                                city = regCity.value,
                                category = regCategory.value,
                                cnic = regCNIC.value
                            )
                        )
                    ),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (techResponse.isSuccessful && techResponse.body()?.user != null) {
                    supabaseUserId = techResponse.body()?.user?.id ?: ""
                    val techToken = techResponse.body()?.accessToken ?: ""
                    addPushNotification("🟢 Registered on Supabase Auth!")
                    if (techToken.isNotEmpty()) {
                        SecureAuthManager.getInstance(getApplication()).saveSession(techToken, "technician", supabaseUserId)
                        SupabaseRealtimeClient.connect(techToken)
                        // Insert technician profile row into Supabase technicians table
                        try {
                            val bearerHeader = "Bearer $techToken"
                            val code = generateReferralCode(regName.value, regPhone.value, "TECH")
                            val cleanReferredByForInsert = regReferredByCode.value.trim().uppercase().ifEmpty { null }
                            val insertDto = TechnicianInsertDto(
                                id = supabaseUserId,
                                phone = regPhone.value.trim(),
                                name = regName.value.trim(),
                                category = regCategory.value,
                                subCategory = regSubCat.value,
                                city = regCity.value,
                                cnic = regCNIC.value.trim(),
                                selfieUrl = regSelfieUrl.value,
                                bankDetails = regBankDetails.value.trim(),
                                isApproved = false,
                                isOnline = false,
                                rating = 4.5,
                                totalJobs = 0,
                                acceptanceRate = 1.0,
                                latitude = baseLat,
                                longitude = baseLng,
                                referralCode = code,
                                referredByCode = cleanReferredByForInsert
                            )
                            val insertResp = SupabaseClient.apiService.createTechnician(
                                technician = insertDto,
                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                authHeader = bearerHeader
                            )
                            if (insertResp.isSuccessful) {
                                addPushNotification("🟢 Technician profile saved to Supabase database! Awaiting admin approval.")
                            } else {
                                addPushNotification("⚠️ Profile row insert failed: ${insertResp.errorBody()?.string()}")
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            addPushNotification("⚠️ Network error saving technician profile: ${e.message}")
                        }
                    }
                } else {
                    addPushNotification("⚠️ Auth warning: ${techResponse.errorBody()?.string() ?: "Register request failed"}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Auth server offline, queueing application locally on device.")
            }

            val tech = TechnicianProfile(
                phone = regPhone.value,
                uuid = supabaseUserId,
                name = regName.value,
                category = regCategory.value,
                subCategory = regSubCat.value,
                city = regCity.value,
                cnic = regCNIC.value,
                selfieUrl = regSelfieUrl.value,
                bankDetails = regBankDetails.value,
                isApproved = false, // Admin must approve
                isOnline = false,
                rating = 4.5,
                totalJobs = 0,
                latitude = baseLat,
                longitude = baseLng,
                referralCode = code,
                referredByCode = referredByCode,
                password = finalPassword
            )
            repository.registerTechnician(tech)
            _techPhone.value = tech.phone
            _activeTechnician.value = tech
            _isRegSuccess.value = true
            addPushNotification("✅ Submissions uploaded! Awaiting CNIC selfie verification from Admin dashboard. Your referral code: $code")
        }
    }

    fun resetRegSuccess() {
        _isRegSuccess.value = false
        regName.value = ""
        regPhone.value = ""
        regCNIC.value = ""
        regPassword.value = ""
        regReferredByCode.value = ""
    }

    // Tech database verification and login functions
    fun loginTechnician(phone: String, pin: String = "1234") {
        viewModelScope.launch {
            val normalizedPhone = phone.trim()
            val normalizedPin = pin.trim()
            if (normalizedPhone.isEmpty()) {
                _techLoginError.value = "⚠️ Please enter a Pakistan phone number."
                return@launch
            }
            if (normalizedPin.isEmpty()) {
                _techLoginError.value = "⚠️ Please enter your secure credentials."
                return@launch
            }

            // Verify with Supabase Auth first to obtain session details for lookup/creation
            var supabaseUserId = ""
            var token = ""
            var syncedNameFromAuth: String? = null
            var syncedCategoryFromAuth: String? = null
            var syncedCityFromAuth: String? = null
            var syncedCnicFromAuth: String? = null

            val emailStr = "${normalizedPhone}@fixnow.com"
            // Use the PIN the user entered as the Supabase password (matches what was set at registration)
            val finalPasswordForSupabase = normalizedPin

            try {
                addPushNotification("🔒 Requesting technician token from Supabase Auth...")
                val loginResponse = SupabaseClient.apiService.signIn(
                    SupabaseSignInRequest(emailStr, finalPasswordForSupabase),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (loginResponse.isSuccessful && loginResponse.body()?.accessToken != null) {
                    supabaseUserId = loginResponse.body()?.user?.id ?: ""
                    token = loginResponse.body()?.accessToken ?: ""
                    val meta = loginResponse.body()?.user?.userMetadata
                    syncedNameFromAuth = meta?.get("name")
                    syncedCategoryFromAuth = meta?.get("category")
                    syncedCityFromAuth = meta?.get("city")
                    syncedCnicFromAuth = meta?.get("cnic")
                    addPushNotification("🟢 Verified with Supabase Auth!")
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "technician", supabaseUserId)
                } else {
                    addPushNotification("🔑 Local lookup mode activated for sandbox technician profiles.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Local offline database check.")
            }

            var profile = repository.getTechnician(normalizedPhone)

            if (profile == null) {
                try {
                    addPushNotification("🔍 Scanning cloud for Technician profile sync...")
                    val bearerToUse = if (token.isNotEmpty()) "Bearer $token" else "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                    val matchedTechs = SupabaseClient.apiService.getTechnician(
                        phone = "eq.${normalizedPhone}",
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        authHeader = bearerToUse
                    )
                    if (matchedTechs.isNotEmpty()) {
                        val dto = matchedTechs[0]
                        val tech = TechnicianProfile(
                            phone = dto.phone,
                            uuid = dto.id,
                            name = dto.name,
                            category = dto.category,
                            subCategory = dto.subCategory ?: "",
                            city = dto.city,
                            cnic = dto.cnic,
                            selfieUrl = dto.selfieUrl ?: "mock_selfie_url",
                            bankDetails = dto.bankDetails ?: "",
                            isApproved = dto.isApproved,
                            isOnline = dto.isOnline,
                            rating = dto.rating,
                            totalJobs = dto.totalJobs,
                            acceptanceRate = dto.acceptanceRate,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            password = normalizedPin
                        )
                        repository.registerTechnician(tech)
                        profile = tech
                        addPushNotification("🟢 Synced profile for '${tech.name}' from cloud!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Restore from auth metadata if cloud table query didn't yield anything or failed
            if (profile == null && supabaseUserId.isNotEmpty()) {
                val finalName = syncedNameFromAuth ?: "Restored Tech"
                val finalCategory = syncedCategoryFromAuth ?: "Electrical Services"
                val finalCity = syncedCityFromAuth ?: "Lahore"
                val finalCnic = syncedCnicFromAuth ?: "00000-0000000-0"
                val tech = TechnicianProfile(
                    phone = normalizedPhone,
                    uuid = supabaseUserId,
                    name = finalName,
                    category = finalCategory,
                    subCategory = "General Vetted Specialist",
                    city = finalCity,
                    cnic = finalCnic,
                    selfieUrl = "mock_selfie_url",
                    bankDetails = "EasyPaisa",
                    isApproved = true, // Auto-approve restored profiles for frictionless multi-device experience
                    isOnline = false,
                    rating = 4.8,
                    totalJobs = 0,
                    acceptanceRate = 1.0,
                    latitude = 31.5204,
                    longitude = 74.3587,
                    password = normalizedPin
                )
                repository.registerTechnician(tech)
                profile = tech
                addPushNotification("🟢 Restored technician profile for '$finalName' from secure auth metadata!")
            }

            val expectedPassword = profile?.password ?: "password123"

            // Local credentials security check
            val isPasswordCorrect = if (profile != null) {
                if (supabaseUserId.isNotEmpty()) {
                    true // Successfully verified with Supabase Auth, bypass local password check
                } else if (profile.password == "password123") {
                    normalizedPin == "1234" || normalizedPin == "tech123" || normalizedPin == "password123"
                } else {
                    normalizedPin == profile.password
                }
            } else {
                false
            }

            if (profile != null && !isPasswordCorrect) {
                _techLoginError.value = "❌ Incorrect password or Security PIN specified."
                addPushNotification("❌ Access Denied: Incorrect password specified for phone $normalizedPhone.")
                return@launch
            }

            if (profile != null) {
                val updatedProfile = if (supabaseUserId.isNotEmpty() && profile.uuid.isEmpty()) {
                    val p = profile.copy(uuid = supabaseUserId)
                    repository.registerTechnician(p)
                    p
                } else {
                    profile
                }
                _techPhone.value = normalizedPhone
                _activeTechnician.value = updatedProfile
                _techLoginError.value = null
                addPushNotification("🔓 Technician logged in successfully: ${updatedProfile.name}.")
            } else {
                _techLoginError.value = "❌ Number is not registered on FixNow system. Try registration or sandbox shortcuts."
            }
        }
    }

    fun logoutTechnician() {
        _techPhone.value = ""
        _activeTechnician.value = null
        _techLoginError.value = null
        _isRegSuccess.value = false
        viewModelScope.launch {
            try {
                SecureAuthManager.getInstance(getApplication()).clearSession()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        addPushNotification("Logged out from Technician Workbench.")
    }

    fun logoutCustomer() {
        _customerPhone.value = ""
        _activeCustomer.value = null
        viewModelScope.launch {
            try {
                SecureAuthManager.getInstance(getApplication()).clearSession()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        addPushNotification("Logged out from Customer Portal.")
    }

    fun loginWithGoogleToken(idToken: String, callback: (success: Boolean, requiresPhoneLinkage: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                addPushNotification("🔒 Verifying Google account credentials...")
                val response = SupabaseClient.apiService.signInWithGoogle(
                    SupabaseGoogleSignInRequest(idToken = idToken),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (response.isSuccessful && response.body()?.accessToken != null) {
                    val token = response.body()?.accessToken ?: ""
                    val uid = response.body()?.user?.id ?: ""
                    val email = response.body()?.user?.email ?: ""
                    val userMeta = response.body()?.user?.userMetadata
                    val name = userMeta?.get("name") ?: userMeta?.get("full_name") ?: "Google User"

                    addPushNotification("🟢 Authenticated with Google!")

                    // Fetch remote profile to see if phone is already linked
                    val bearerHeader = "Bearer $token"
                    var existingCustomer: CustomerProfile? = null

                    try {
                        val remoteRecords = SupabaseClient.apiService.getCustomerById(
                            id = "eq.$uid",
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            authHeader = bearerHeader
                        )
                        if (remoteRecords.isNotEmpty()) {
                            val dto = remoteRecords[0]
                            // In Pakistan, mobile numbers start with '03' (e.g. 03001234567)
                            if (dto.phone.isNotEmpty() && dto.phone.startsWith("03")) {
                                existingCustomer = CustomerProfile(
                                    phone = dto.phone,
                                    uuid = dto.id,
                                    name = dto.name,
                                    email = dto.email,
                                    city = dto.city
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Save session securely
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "customer", uid)

                    if (existingCustomer != null) {
                        // Already fully registered and linked. Save locally and log in!
                        repository.registerCustomer(existingCustomer)
                        _customerPhone.value = existingCustomer.phone
                        _activeCustomer.value = existingCustomer
                        addPushNotification("Welcome back, ${existingCustomer.name}!")
                        callback(true, false) // Success, no linkage needed
                    } else {
                        // Set temporary Google session states for linkage
                        googleSessionUid.value = uid
                        googleSessionToken.value = token
                        googleSessionEmail.value = email
                        googleSessionName.value = name
                        callback(true, true) // Success, requires phone linkage
                    }
                } else {
                    addPushNotification("❌ Google authentication failed on Supabase backend.")
                    callback(false, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Connection timed out during Google authentication.")
                callback(false, false)
            }
        }
    }

    fun clearTechLoginError() {
        _techLoginError.value = null
    }

    // Admin secure credential checker
    fun loginAdmin(passcode: String) {
        val code = passcode.trim()
        if (code.isEmpty()) {
            _adminAuthError.value = "⚠️ Please enter an admin credential."
            return
        }
        viewModelScope.launch {
            val emailStr = if (code.contains("@")) code else "admin@fixnow.com"
            val passwordStr = if (code == "admin" || code == "admin123") "admin_secure_pass" else code
            try {
                addPushNotification("🔒 Requesting administrative authorization...")
                val loginResponse = SupabaseClient.apiService.signIn(
                    SupabaseSignInRequest(emailStr, passwordStr),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (loginResponse.isSuccessful && loginResponse.body()?.accessToken != null) {
                    val token = loginResponse.body()?.accessToken ?: ""
                    val uid = loginResponse.body()?.user?.id ?: ""
                    
                    _isAdminAuthorized.value = true
                    _adminAuthError.value = null
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "admin", uid)
                    addPushNotification("🔓 System Administration authorized successfully via Supabase.")
                } else {
                    // Sandbox fallback to allow testing, but with dynamic warning
                    if (code == "admin" || code == "admin123") {
                        _isAdminAuthorized.value = true
                        _adminAuthError.value = null
                        addPushNotification("❗ Sandbox warning: Admin access granted via system fallback.")
                    } else {
                        _adminAuthError.value = "❌ Authentication failed. Incorrect admin passphrase."
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                if (code == "admin" || code == "admin123") {
                    _isAdminAuthorized.value = true
                    _adminAuthError.value = null
                    addPushNotification("⚠️ Sandbox offline fallback: Admin authorized.")
                } else {
                    _adminAuthError.value = "❌ Network connection timeout. Offline admin authorization disabled."
                }
            }
        }
    }

    fun clearAdminAuthError() {
        _adminAuthError.value = null
    }

    fun logoutAdmin() {
        _isAdminAuthorized.value = false
        adminPasscodeInput.value = ""
        _adminAuthError.value = null
        addPushNotification("Admin console closed securely.")
    }

    // ----------------------------------------------------
    // BOOKING ENGINE & DISPATCH MATCHING
    // ----------------------------------------------------
    fun submitBooking() {
        viewModelScope.launch {
            _isSubmittingBooking.value = true
            val customer = _activeCustomer.value ?: CustomerProfile(
                phone = _customerPhone.value,
                name = "Guest User",
                email = "guest@example.com",
                city = "Lahore"
            )

            // Setup basic coordinates for Lahore, Karachi, or Islamabad
            val isLahore = customer.city.contains("Lahore", ignoreCase = true)
            val isIslamabad = customer.city.contains("Islamabad", ignoreCase = true)
            val baseLat = if (isLahore) 31.5204 else if (isIslamabad) 33.6844 else 24.8607
            val baseLng = if (isLahore) 74.3587 else if (isIslamabad) 73.0479 else 67.0011

            // Exact location if using live GPS, otherwise resolve address with Google Maps Geocoding API
            var finalLat = resolvedLocationLat.value ?: baseLat
            var finalLng = resolvedLocationLng.value ?: baseLng

            if (!draftUseLiveLocation.value && resolvedLocationLat.value == null) {
                try {
                    val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                    if (token.isNotEmpty() && token != "your_token_here") {
                        val response = MapboxClient.apiService.searchPlaces(query = draftAddress.value, accessToken = token)
                        if (response.features.isNotEmpty()) {
                            val coord = response.features[0].center
                            finalLng = coord[0]
                            finalLat = coord[1]
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Sync Customer UUID from Supabase by phone prefix filter if empty
            var resolvedCustId = customer.uuid
            if (resolvedCustId.isEmpty()) {
                try {
                    addPushNotification("🔍 Scanning cloud for Customer profile sync...")
                    val matchedCusts = SupabaseClient.apiService.getCustomer(
                        phone = "eq.${customer.phone}",
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        authHeader = getBearerToken()
                    )
                    if (matchedCusts.isNotEmpty()) {
                        resolvedCustId = matchedCusts[0].id
                        val updatedCust = customer.copy(uuid = resolvedCustId)
                        repository.registerCustomer(updatedCust)
                        _activeCustomer.value = updatedCust
                        addPushNotification("🟢 Customer Cloud Sync complete!")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val category = _selectedCategory.value ?: "Electrical Services"
            val service = _draftServiceName.value
            val desc = draftDescription.value
            val address = draftAddress.value
            val city = customer.city
            val preferred = draftTimeSlot.value
            val payment = draftPaymentMethod.value
            val price = _draftPrice.value

            val bookingDto = BookingDto(
                serviceCategory = category,
                serviceName = service,
                issueDescription = desc,
                customerId = if (resolvedCustId.isNotEmpty()) resolvedCustId else "00000000-0000-0000-0000-000000000000",
                customerPhone = customer.phone,
                customerName = customer.name,
                customerAddress = address,
                customerCity = city,
                preferredTime = preferred,
                paymentMethod = payment,
                price = price,
                status = "Requested",
                latitude = finalLat,
                longitude = finalLng,
                techLatitude = finalLat,
                techLongitude = finalLng
            )

            var remoteBookingId: Long? = null
            try {
                addPushNotification("📡 Synching booking request to Supabase Cloud...")
                val resList = SupabaseClient.apiService.createBooking(
                    booking = bookingDto,
                    apiKey = BuildConfig.SUPABASE_ANON_KEY,
                    authHeader = getBearerToken()
                )
                if (resList.isNotEmpty()) {
                    remoteBookingId = resList[0].id
                    addPushNotification("🟢 Supabase synced successfully! Cloud key: $remoteBookingId")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Switched to offline cache. Will sync when network returns.")
            }

            val newBooking = Booking(
                supabaseId = remoteBookingId,
                serviceCategory = category,
                serviceName = service,
                issueDescription = desc,
                customerId = resolvedCustId,
                customerPhone = customer.phone,
                customerName = customer.name,
                customerAddress = address,
                customerCity = city,
                preferredTime = preferred,
                paymentMethod = payment,
                price = price,
                status = "Requested",
                declinedTechnicians = "",
                isManualAssign = false,
                latitude = finalLat,
                longitude = finalLng,
                techLatitude = finalLat,
                techLongitude = finalLng
            )

            val bookingId = repository.createBooking(newBooking)
            _isSubmittingBooking.value = false
            addPushNotification("Booking created for ${newBooking.serviceName}! Simulating tech matching...")

            // Trigger Matchmaker Engine
            simulateMatchmaker(bookingId)
        }
    }

    private suspend fun simulateMatchmaker(bookingId: Long) {
        val booking = repository.getBooking(bookingId) ?: return
        if (booking.status != "Requested") return

        addPushNotification("🔍 Matching: Finding nearest online ${booking.serviceCategory} expert in ${booking.customerCity}...")
        
        delay(1500) // Delay to show scanning UX

        var matchedTech: TechnicianProfile? = null
        try {
            addPushNotification("🛰️ Querying PostGIS server-side discovery RPC...")
            val response = SupabaseClient.apiService.findNearbyTechniciansRpc(
                TechDiscoveryParams(booking.latitude, booking.longitude, booking.customerCity, booking.serviceCategory),
                BuildConfig.SUPABASE_ANON_KEY,
                "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
            )
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                val discoveryList = response.body()!!
                addPushNotification("📡 PostGIS server-side discovery found ${discoveryList.size} nearby experts!")
                val bestRemoteTech = discoveryList.first()
                addPushNotification("🟢 Best Map match: ${bestRemoteTech.name} (${String.format("%.2f", bestRemoteTech.distance)} km away)")
                matchedTech = repository.getTechnician(bestRemoteTech.phone)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            addPushNotification("❗ Server-side spatial query offline. Falling back to local device calculation.")
        }

        if (matchedTech == null) {
            matchedTech = repository.findNearestQualifiedTechnician(booking)
        }

        if (matchedTech != null) {
            val updatedBooking = booking.copy(
                status = "Requested", // remains requested until tech clicks "Accept"
                technicianId = matchedTech.uuid,
                technicianPhone = matchedTech.phone,
                technicianName = matchedTech.name,
                techLatitude = matchedTech.latitude,
                techLongitude = matchedTech.longitude
            )
            repository.updateBooking(updatedBooking)

            // Update on Supabase as well
            viewModelScope.launch {
                try {
                    val updateMap = mapOf(
                        "technician_id" to (matchedTech.uuid.ifEmpty { null } ?: ""),
                        "technician_phone" to matchedTech.phone,
                        "technician_name" to matchedTech.name,
                        "tech_latitude" to matchedTech.latitude,
                        "tech_longitude" to matchedTech.longitude
                    ).filterValues { it != "" }
                    if (booking.supabaseId != null) {
                        SupabaseClient.apiService.updateBookingStatus(
                            id = "eq.${booking.supabaseId}",
                            body = updateMap,
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            authHeader = getBearerToken()
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // Send Push / WhatsApp notification mockups
            addPushNotification("🔔 Matching! Booking offered to ${matchedTech.name} (${calculateDistanceString(matchedTech.latitude, matchedTech.longitude, booking.latitude, booking.longitude)} away)")
            addWhatsAppUpdate(matchedTech.phone, "FixNow: New service request alert! ${booking.serviceName} in ${booking.customerCity}. Earn Rs. ${booking.price}. View options in app.")
        } else {
            // No matching tech found
            addPushNotification("⚠️ No online & approved technicians found currently for ${booking.serviceCategory} in ${booking.customerCity}. You can assign via Admin web portal.")
        }
    }

    private fun listenToActiveBookings() {
        // Reactively monitor if any of the active user's bookings are progressing
        viewModelScope.launch {
            bookings.collect { bookingList ->
                // Look for current customer's booking that is active
                val activeForCust = bookingList.firstOrNull { 
                    it.customerPhone == _customerPhone.value && it.status != "Completed" && it.status != "Cancelled" 
                }
                _currentSimulatedBooking.value = activeForCust
            }
        }
    }

    // ----------------------------------------------------
    // TECHNICIAN ACTIONS
    // ----------------------------------------------------
    fun acceptBooking(bookingId: Long) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val tech = _activeTechnician.value
            if (tech == null) {
                addPushNotification("❌ Error: Not logged in as a technician.")
                return@launch
            }
            if (!tech.isApproved) {
                addPushNotification("⚠️ Vetting Process Incomplete. You are not approved to accept jobs yet.")
                return@launch
            }

            val supabaseId = booking.supabaseId
            var success = false

            try {
                if (supabaseId != null) {
                    addPushNotification("🔒 Requesting exclusive lock from Supabase cloud...")
                    val rpcResponse = SupabaseClient.apiService.acceptBookingRpc(
                        params = AcceptBookingParams(
                            bookingId = supabaseId,
                            technicianId = tech.uuid
                        ),
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        bearerToken = "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
                    )
                    
                    if (rpcResponse.isSuccessful) {
                        success = rpcResponse.body() ?: false
                        if (success) {
                            addPushNotification("🟢 Supabase locked & assigned to you!")
                        } else {
                            addPushNotification("❌ Booking already assigned or unavailable.")
                        }
                    } else {
                        addPushNotification("❌ Cloud verification failed: ${rpcResponse.errorBody()?.string() ?: rpcResponse.message()}")
                    }
                } else {
                    // Fallback to local success if missing database ID (e.g. local offline demo or test case)
                    success = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Network / Connection Error. Cannot verify exclusive lock on cloud.")
                success = false
            }

            if (success) {
                val updated = booking.copy(
                    status = "Assigned",
                    technicianPhone = tech.phone,
                    technicianName = tech.name,
                    technicianId = tech.uuid
                )
                repository.updateBooking(updated)
                addPushNotification("✅ Match Successful! Approved for ${booking.customerName}. Booking accepted.")
                addWhatsAppUpdate(booking.customerPhone, "FixNow: Your technician ${tech.name} is assigned! Contact: ${tech.phone}")
            } else {
                addPushNotification("❌ Booking already assigned. This job is no longer available.")
            }
        }
    }

    fun declineBooking(bookingId: Long) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            // Append technician phone to declined list
            val currentDeclined = booking.declinedTechnicians
            val updatedDeclined = if (currentDeclined.isEmpty()) _techPhone.value else "$currentDeclined,${_techPhone.value}"
            
            val updated = booking.copy(
                status = "Requested",
                technicianPhone = null,
                technicianName = null,
                declinedTechnicians = updatedDeclined
            )
            repository.updateBooking(updated)
            addPushNotification("Technician declined request. Re-routing dispatch to the next nearest expert...")

            // Cascading Dispatch: Immediately search for the next nearest qualified tech!
            delay(1000)
            simulateMatchmaker(bookingId)
        }
    }

    fun advanceJobStatus(bookingId: Long) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val nextStatus = when (booking.status) {
                "Assigned" -> "Technician En Route"
                "Technician En Route" -> "Arrived"
                "Arrived" -> "In Progress"
                "In Progress" -> "Completed"
                else -> booking.status
            }

            if (nextStatus == "Technician En Route") {
                // Trigger live location GPS sim shifting coordinates
                startLocationTrackingSimulation(bookingId)
            }

            repository.updateBookingStatus(bookingId, nextStatus)
            
            val notificationMsg = when (nextStatus) {
                "Technician En Route" -> "🚀 ${_activeTechnician.value?.name ?: "Technician"} is now en route to your household in ${booking.customerCity}!"
                "Arrived" -> "🔔 Knock knock! Technician has arrived at your address: ${booking.customerAddress}!"
                "In Progress" -> "🛠️ Work in progress: Service is being rendered. Safety first!"
                "Completed" -> "🎉 Task completed! Please select payment method to complete invoice and leave feedback rating."
                else -> "Job status updated to $nextStatus"
            }
            addPushNotification(notificationMsg)

            // Auto-send WhatsApp mock
            addWhatsAppUpdate(booking.customerPhone, "FixNow Alert: Your booking is in state '$nextStatus'.")
        }
    }

    fun submitCustomerReview(bookingId: Long, stars: Int, review: String) {
        viewModelScope.launch {
            repository.submitReview(bookingId, stars, review)
            addPushNotification("🌟 Thank you! Feedback has been recorded. Ratings updated.")
            _currentSimulatedBooking.value = null
        }
    }

    // ----------------------------------------------------
    // MAPBOX TRACKING & LOCATION CONTROLLER
    // ----------------------------------------------------
    private fun startLocationTrackingSimulation(bookingId: Long) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val techPhoneLoc = booking.technicianPhone ?: return@launch

            val startLat = booking.techLatitude
            val startLng = booking.techLongitude
            val destLat = booking.latitude
            val destLng = booking.longitude

            // 1. Fetch real Mapbox route polyline first!
            var routePoints: List<LatLng> = emptyList()
            try {
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                if (token.isNotEmpty() && token != "your_token_here") {
                    val coordsPath = "$startLng,$startLat;$destLng,$destLat"
                    val response = MapboxClient.apiService.getDirections(
                        coords = coordsPath,
                        accessToken = token,
                        geometries = "polyline"
                    )
                    if (response.code == "Ok" && response.routes.isNotEmpty()) {
                        val geom = response.routes[0].geometry
                        if (geom != null) {
                            routePoints = MapboxPolylineDecoder.decode(geom)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Fallback if no points resolved
            if (routePoints.isEmpty()) {
                routePoints = List(8) { step ->
                    val factor = step.toDouble() / 7.0
                    LatLng(
                        startLat + (destLat - startLat) * factor,
                        startLng + (destLng - startLng) * factor
                    )
                }
            }

            activeRoutePoints.value = routePoints

            // 2. Animate technician step-by-step moving along the real polyline route coordinates!
            val totalSteps = routePoints.size
            for (step in 0 until totalSteps) {
                val currentBooking = repository.getBooking(bookingId) ?: break
                if (currentBooking.status != "Technician En Route") break

                val crd = routePoints[step]
                repository.updateBookingTechCoordinates(bookingId, crd.latitude, crd.longitude)
                repository.updateTechnicianLocation(techPhoneLoc, crd.latitude, crd.longitude)

                // Dual write live tracking coordinates online to Supabase
                viewModelScope.launch {
                    try {
                        if (currentBooking.supabaseId != null) {
                            SupabaseClient.apiService.updateBookingStatus(
                                id = "eq.${currentBooking.supabaseId}",
                                body = mapOf(
                                    "tech_latitude" to crd.latitude,
                                    "tech_longitude" to crd.longitude
                                ),
                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                authHeader = getBearerToken()
                            )
                        }
                        
                        val matchedTech = repository.getTechnician(techPhoneLoc)
                        if (matchedTech != null && matchedTech.uuid.isNotEmpty()) {
                            SupabaseClient.apiService.updateTechnicianProfile(
                                id = "eq.${matchedTech.uuid}",
                                body = mapOf(
                                    "latitude" to crd.latitude,
                                    "longitude" to crd.longitude
                                ),
                                apiKey = BuildConfig.SUPABASE_ANON_KEY,
                                authHeader = getBearerToken()
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Query Directions API dynamically to obtain updated ETA and distance
                var currentEta = "Calculating..."
                var currentDist = "Estimating..."
                try {
                    val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                    if (token.isNotEmpty() && token != "your_token_here") {
                        val coordsPath = "${crd.longitude},${crd.latitude};$destLng,$destLat"
                        val response = MapboxClient.apiService.getDirections(
                            coords = coordsPath,
                            accessToken = token,
                            geometries = "polyline"
                        )
                        if (response.code == "Ok" && response.routes.isNotEmpty()) {
                            val route = response.routes[0]
                            val distanceKm = route.distance / 1000.0
                            val estMin = (route.duration / 60.0).toInt().coerceAtLeast(1)
                            currentDist = String.format("%.2f KM", distanceKm)
                            currentEta = "$estMin mins"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (currentDist == "Estimating...") {
                    val degDistance = sqrt((crd.latitude - destLat).pow(2.0) + (crd.longitude - destLng).pow(2.0)) * 111.0
                    currentDist = String.format("%.2f KM", degDistance)
                    val estMin = (degDistance * 2).toInt().coerceAtLeast(1)
                    currentEta = "$estMin mins"
                }

                activeRouteDistance.value = currentDist
                activeRouteDuration.value = currentEta

                addPushNotification("📍 Rider update: remaining $currentDist | ETA: $currentEta")
                delay(3000) // Coordinate update ticks
            }
        }
    }

    // ----------------------------------------------------
    // ADMIN FUNCTIONS
    // ----------------------------------------------------
    fun setAdminTab(tab: String) {
        _adminTab.value = tab
    }

    fun approveTechnician(phone: String) {
        viewModelScope.launch {
            repository.updateTechnicianApproval(phone, true)
            addPushNotification("👨‍🔧 Profile approved! Technician is now authorized to take customer requests.")
            
            // Sync approval online
            try {
                val matchedTech = repository.getTechnician(phone)
                if (matchedTech != null && matchedTech.uuid.isNotEmpty()) {
                    SupabaseClient.apiService.updateTechnicianProfile(
                        id = "eq.${matchedTech.uuid}",
                        body = mapOf("is_approved" to true),
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        authHeader = getBearerToken()
                    )
                    addPushNotification("🟢 Synced technician approval to Supabase cloud.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun rejectTechnician(phone: String) {
        viewModelScope.launch {
            repository.updateTechnicianApproval(phone, false)
            addPushNotification("👨‍🔧 Profile registration rejected due to CNIC validation mismatch.")
            
            // Sync rejection online
            try {
                val matchedTech = repository.getTechnician(phone)
                if (matchedTech != null && matchedTech.uuid.isNotEmpty()) {
                    SupabaseClient.apiService.updateTechnicianProfile(
                        id = "eq.${matchedTech.uuid}",
                        body = mapOf("is_approved" to false),
                        apiKey = BuildConfig.SUPABASE_ANON_KEY,
                        authHeader = getBearerToken()
                    )
                    addPushNotification("🔴 Synced technician rejection status to Supabase cloud.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun manualAssignTechnician(bookingId: Long, techPhone: String) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val tech = repository.getTechnician(techPhone) ?: return@launch
            val updated = booking.copy(
                status = "Assigned",
                technicianPhone = tech.phone,
                technicianName = tech.name,
                isManualAssign = true,
                techLatitude = tech.latitude,
                techLongitude = tech.longitude
            )
            repository.updateBooking(updated)
            addPushNotification("🛠️ Admin Override: Manually assigned job #$bookingId to ${tech.name}!")
        }
    }

    fun cancelActiveBooking(bookingId: Long) {
        viewModelScope.launch {
            repository.updateBookingStatus(bookingId, "Cancelled")
            addPushNotification("❌ Booking #$bookingId has been cancelled by customer/administrator.")
        }
    }

    // Helper functions for notification messages
    fun addPushNotification(msg: String) {
        viewModelScope.launch {
            _notifications.update { current -> listOf(msg) + current.take(15) }
            try {
                val title = if (msg.contains("WhatsApp", ignoreCase = true)) "💬 WhatsApp Update" else "🔔 FixNow Update"
                val cleanMsg = msg
                    .replace("💬", "")
                    .replace("🔔", "")
                    .replace("🔒", "")
                    .replace("🔓", "")
                    .replace("🟢", "")
                    .replace("⚠️", "")
                    .replace("🎉", "")
                    .replace("❌", "")
                    .replace("🛰️", "")
                    .replace("📡", "")
                    .replace("🎁", "")
                    .trim()
                com.example.NotificationHelper.showSystemNotification(getApplication(), title, cleanMsg)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addWhatsAppUpdate(recipientPhone: String, text: String) {
        // Appends to push notification simulator mimicking system operations
        addPushNotification("💬 [WhatsApp to $recipientPhone]: \"$text\"")
    }

    private fun calculateDistanceString(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val distance = sqrt((lat1 - lat2).pow(2.0) + (lon1 - lon2).pow(2.0)) * 100
        return String.format("%.1f KM", distance)
    }

    // ----------------------------------------------------
    // SUPPORT & COMPLAINTS WORKFLOWS
    // ----------------------------------------------------
    private val _supportTickets = MutableStateFlow<List<SupportTicket>>(listOf(
        SupportTicket(
            id = 101,
            customerPhone = "03001234567",
            customerName = "Ahmad Malik",
            bookingId = 1L,
            category = "Pricing Dispute",
            description = "The technician Muhammad Rizwan asked for extra material charges of Rs. 1000 without sharing a receipt.",
            status = "Escalated",
            timestamp = System.currentTimeMillis() - 7200000,
            chatMessages = listOf(
                SupportMessage("Customer", "I would like to file a formal complaint regarding my electrical booking."),
                SupportMessage("Support Agent", "Ahmad, we have received your complaint. Your ticket has been escalated to our Audit department and a senior auditor is looking into Muhammad Rizwan's profile.")
            ),
            slaTimerMinutes = 12
        )
    ))
    val supportTickets: StateFlow<List<SupportTicket>> = _supportTickets.asStateFlow()

    private val _coupons = MutableStateFlow<List<Coupon>>(listOf(
        Coupon("FIXNOW10", 150.0, "Get flat Rs. 150 off on your first service booking!"),
        Coupon("PAKISTAN50", 250.0, "Special National Discount - Flat Rs. 250 off."),
        Coupon("REFER500", 500.0, "Referral discount: Zainab's Invite", isReferral = true, refereeName = "Zainab")
    ))
    val coupons: StateFlow<List<Coupon>> = _coupons.asStateFlow()

    val activeDiscountCode = MutableStateFlow("")
    val appliedDiscountAmount = MutableStateFlow(0.0)

    fun createSupportTicket(category: String, description: String, bookingId: Long?) {
        val customer = _activeCustomer.value ?: return
        val newId = (_supportTickets.value.map { it.id }.maxOrNull() ?: 100) + 1
        val newTicket = SupportTicket(
            id = newId,
            customerPhone = customer.phone,
            customerName = customer.name,
            bookingId = bookingId,
            category = category,
            description = description,
            status = "Open",
            timestamp = System.currentTimeMillis(),
            chatMessages = listOf(
                SupportMessage("Customer", description)
            )
        )
        _supportTickets.update { it + newTicket }
        addPushNotification("🎫 Ticket #${newId} filed on category '$category'. Response SLA: 15 mins.")
    }

    fun submitSupportAgentReply(ticketId: Long, reply: String) {
        _supportTickets.update { tickets ->
            tickets.map { ticket ->
                if (ticket.id == ticketId) {
                    val updatedMessages = ticket.chatMessages + SupportMessage("Support Agent", reply)
                    ticket.copy(chatMessages = updatedMessages, status = "In Progress")
                } else ticket
            }
        }
        val ticket = _supportTickets.value.firstOrNull { it.id == ticketId }
        if (ticket != null) {
            addPushNotification("💬 Support Desk: Sent message to ${ticket.customerName}.")
            addWhatsAppUpdate(ticket.customerPhone, "FixNow Support: A support companion has replied to your Ticket #${ticket.id}. Please view details in-app.")
        }
    }

    fun submitCustomerSupportMessage(ticketId: Long, message: String) {
        _supportTickets.update { tickets ->
            tickets.map { ticket ->
                if (ticket.id == ticketId) {
                    val updatedMessages = ticket.chatMessages + SupportMessage("Customer", message)
                    ticket.copy(chatMessages = updatedMessages)
                } else ticket
            }
        }
    }

    fun updateTicketStatus(ticketId: Long, newStatus: String) {
        _supportTickets.update { tickets ->
            tickets.map { ticket ->
                if (ticket.id == ticketId) {
                    ticket.copy(status = newStatus)
                } else ticket
            }
        }
        val ticket = _supportTickets.value.firstOrNull { it.id == ticketId }
        if (ticket != null) {
            addPushNotification("🚨 Ticket #${ticketId} status changed to $newStatus.")
        }
    }

    fun validateAndApplyCoupon(code: String): Boolean {
        val cleaned = code.trim().uppercase()
        val match = _coupons.value.firstOrNull { it.code.uppercase() == cleaned }
        return if (match != null) {
            activeDiscountCode.value = match.code
            appliedDiscountAmount.value = match.discountAmount
            addPushNotification("🎟️ Promo applied successfully! Discount of Rs. ${match.discountAmount.toInt()} has been subtracted.")
            true
        } else {
            addPushNotification("❌ Code '$code' is not valid or has expired.")
            false
        }
    }

    fun createCoupon(code: String, discount: Double, desc: String) {
        val cleanCode = code.trim().uppercase()
        if (_coupons.value.any { it.code == cleanCode }) return
        val newCoupon = Coupon(cleanCode, discount, desc)
        _coupons.update { it + newCoupon }
        addPushNotification("🎟️ New promotional code generated: $cleanCode")
    }

    fun removeAppliedDiscount() {
        activeDiscountCode.value = ""
        appliedDiscountAmount.value = 0.0
    }

    // ----------------------------------------------------
    // ENTERPRISE ADMIN EXTRA WORKFLOWS (FRAUD, PAYMENTS, BROADCAST)
    // ----------------------------------------------------
    private val _fraudAlerts = MutableStateFlow<List<FraudAlert>>(listOf(
        FraudAlert(1, "Suspect GPS Relocation", "HIGH", "Muhammad Rizwan's logged GPS location updated directly from Gulberg Lahore to F-7 Islamabad within 4 seconds.", System.currentTimeMillis() - 1000 * 60 * 18, "03009988771"),
        FraudAlert(2, "Duplicate IP Sign-In", "LOW", "Technician and Customer accounts with overlapping referral codes logged from identical terminal fingerprints.", System.currentTimeMillis() - 1000 * 60 * 45, "03001122334"),
        FraudAlert(3, "Substandard Rate Gouging", "CRITICAL", "Main service bill totaled Rs. 14,000 on category estimated at max Rs. 3,500. Suspicion of offline extortion billing.", System.currentTimeMillis() - 1000 * 60 * 120, "03125559092")
    ))
    val fraudAlerts: StateFlow<List<FraudAlert>> = _fraudAlerts.asStateFlow()

    private val _paymentWithdrawals = MutableStateFlow<List<PaymentWithdrawal>>(listOf(
        PaymentWithdrawal("TXN-3019A", "Zahid Iqbal", "JazzCash Payout", 4200.0, "JazzCash - 03001234411", "Pending Approved", System.currentTimeMillis() - 1000 * 60 * 30),
        PaymentWithdrawal("TXN-3023M", "Farhan Saeed", "EasyPaisa Cash-Out", 8400.0, "EasyPaisa - 03217744112", "Pending Approved", System.currentTimeMillis() - 1000 * 60 * 65),
        PaymentWithdrawal("TXN-1092Q", "Muhammad Rizwan", "Bank Transfer RELEASE", 32000.0, "HBL Bank - PK00UNIL01920392819", "Under Compliance Hold", System.currentTimeMillis() - 1000 * 60 * 180),
        PaymentWithdrawal("TXN-0892B", "Yasir Rasheed", "JazzCash Payout", 1500.0, "JazzCash - 03009988112", "Released & Processed", System.currentTimeMillis() - 1000 * 60 * 300)
    ))
    val paymentWithdrawals: StateFlow<List<PaymentWithdrawal>> = _paymentWithdrawals.asStateFlow()

    private val _announcementsLogs = MutableStateFlow<List<AnnouncementLog>>(listOf(
        AnnouncementLog("MASS_A1", "Global System Core Update", "All field technicians are advised to run NADRA facial match upgrades directly.", "All Techs", System.currentTimeMillis() - 1000 * 60 * 1400),
        AnnouncementLog("MASS_A2", "Rain Emergency Alert", "Heavy rain downpours predicted in Karachi. Shift margins by Rs. 300.", "Karachi Only", System.currentTimeMillis() - 1000 * 60 * 900)
    ))
    val announcementsLogs: StateFlow<List<AnnouncementLog>> = _announcementsLogs.asStateFlow()

    fun sendGlobalPushAnnouncement(target: String, title: String, content: String) {
        val newAnn = AnnouncementLog(
            id = "MASS_" + System.currentTimeMillis().toString().takeLast(6),
            title = title,
            content = content,
            targetGroup = target,
            timestamp = System.currentTimeMillis()
        )
        _announcementsLogs.update { listOf(newAnn) + it }
        addPushNotification("📣 BROADCAST ISSUED to [$target] -> $title: $content")
    }

    fun resolveFraudAlert(alertId: Int) {
        _fraudAlerts.update { alerts ->
            alerts.map { if (it.id == alertId) it.copy(isResolved = true) else it }
        }
        addPushNotification("🛡️ Incident #$alertId successfully cataloged and filed to archives.")
    }

    fun blockTechnicianProfile(phone: String) {
        viewModelScope.launch {
            repository.updateTechnicianApproval(phone, false)
            repository.updateTechnicianOnlineStatus(phone, false)
            addPushNotification("🚨 STRICT ACTION: Technician profile tied to $phone suspended immediately from active routing.")
        }
    }

    fun releasePayoutWithdrawal(txnId: String) {
        _paymentWithdrawals.update { txns ->
            txns.map { if (it.id == txnId) it.copy(status = "Released & Processed") else it }
        }
        val txn = _paymentWithdrawals.value.firstOrNull { it.id == txnId }
        if (txn != null) {
            val phoneOnly = txn.paymentDetails.substringAfter("- ").trim()
            addPushNotification("💸 Payout APPROVED: released Rs. ${txn.amount.toInt()} directly to ${txn.paymentDetails}")
            addWhatsAppUpdate(phoneOnly, "FixNow Payout Alert: Your withdrawal Rs. ${txn.amount.toInt()} has been processed and credited to your account!")
        }
    }

    fun rejectPayoutWithdrawal(txnId: String) {
        _paymentWithdrawals.update { txns ->
            txns.map { if (it.id == txnId) it.copy(status = "Declined (Fraud / CNIC Audit)") else it }
        }
        addPushNotification("🛑 Payout DENIED: Txn #$txnId flagged under compliance review.")
    }

    // Mapbox App API Implementations
    fun updateLiveLocation(lat: Double, lng: Double, address: String = "") {
        resolvedLocationLat.value = lat
        resolvedLocationLng.value = lng
        if (address.isNotEmpty()) {
            draftAddress.value = address
        }
    }

    fun searchAddressSuggestions(query: String) {
        if (query.trim().isEmpty()) {
            searchSuggestions.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                if (token.isEmpty() || token == "your_token_here") {
                    val pCity = _activeCustomer.value?.city ?: "Lahore"
                    val isKarachi = pCity.contains("Karachi", ignoreCase = true)
                    val isIslamabad = pCity.contains("Islamabad", ignoreCase = true)
                    val citySuggestions = if (isKarachi) {
                        listOf(
                            "${query.trim()}, Clifton Block 5, Karachi",
                            "${query.trim()}, DHA Phase 6, Karachi",
                            "${query.trim()}, KDA Scheme 1, Karimabad, Karachi",
                            "${query.trim()}, Gulshan-e-Iqbal Block 3, Karachi",
                            "${query.trim()}, Saddar Commercial Area, Karachi"
                        )
                    } else if (isIslamabad) {
                        listOf(
                            "${query.trim()}, Sector F-6/2 Markaz, Islamabad",
                            "${query.trim()}, Jinnah Avenue, Blue Area, Islamabad",
                            "${query.trim()}, Sector G-11 Markaz, Islamabad",
                            "${query.trim()}, Sector I-8/4 Residential Area, Islamabad",
                            "${query.trim()}, Bahria Town Phase 4, Islamabad"
                        )
                    } else { // Lahore
                        listOf(
                            "${query.trim()}, Block H, Gulberg III, Lahore",
                            "${query.trim()}, Sector CCA, Phase 5, DHA, Lahore",
                            "${query.trim()}, Main Boulevard, Model Town, Lahore",
                            "${query.trim()}, Block C, Garden Town, Lahore",
                            "${query.trim()}, Emporium Lane, Johar Town, Lahore"
                        )
                    }
                    searchSuggestions.value = citySuggestions
                    return@launch
                }
                val response = MapboxClient.apiService.searchPlaces(query = query, accessToken = token)
                if (response.features.isNotEmpty()) {
                    searchSuggestions.value = response.features.map { it.placeName }
                } else {
                    val pCity = _activeCustomer.value?.city ?: "Lahore"
                    searchSuggestions.value = listOf(
                        "$query, Block H, Gulberg III, $pCity",
                        "$query, Phase 5, DHA, $pCity",
                        "$query, Saddar, $pCity"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val pCity = _activeCustomer.value?.city ?: "Lahore"
                searchSuggestions.value = listOf(
                    "$query, Block H, Gulberg III, $pCity",
                    "$query, Phase 5, DHA, $pCity",
                    "$query, Saddar, $pCity"
                )
            }
        }
    }

    fun selectAddressAndGeocode(address: String) {
        draftAddress.value = address
        viewModelScope.launch {
            try {
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                if (token.isEmpty() || token == "your_token_here") {
                    val isKarachi = address.contains("Karachi", ignoreCase = true)
                    val isIslamabad = address.contains("Islamabad", ignoreCase = true)
                    resolvedLocationLat.value = if (isKarachi) 24.8607 else if (isIslamabad) 33.6844 else 31.5204
                    resolvedLocationLng.value = if (isKarachi) 67.0011 else if (isIslamabad) 73.0479 else 74.3587
                    return@launch
                }
                val response = MapboxClient.apiService.searchPlaces(query = address, accessToken = token)
                if (response.features.isNotEmpty()) {
                    val feature = response.features[0]
                    val coordinates = feature.center
                    resolvedLocationLng.value = coordinates[0]
                    resolvedLocationLat.value = coordinates[1]
                    addPushNotification("📍 Resolved address location from Mapbox: (${coordinates[1]}, ${coordinates[0]})")
                } else {
                    val isKarachi = address.contains("Karachi", ignoreCase = true)
                    val isIslamabad = address.contains("Islamabad", ignoreCase = true)
                    resolvedLocationLat.value = if (isKarachi) 24.8607 else if (isIslamabad) 33.6844 else 31.5204
                    resolvedLocationLng.value = if (isKarachi) 67.0011 else if (isIslamabad) 73.0479 else 74.3587
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val isKarachi = address.contains("Karachi", ignoreCase = true)
                val isIslamabad = address.contains("Islamabad", ignoreCase = true)
                resolvedLocationLat.value = if (isKarachi) 24.8607 else if (isIslamabad) 33.6844 else 31.5204
                resolvedLocationLng.value = if (isKarachi) 67.0011 else if (isIslamabad) 73.0479 else 74.3587
            }
        }
    }

    fun refreshActiveGoogleRoute(booking: Booking) {
        val techLat = booking.techLatitude
        val techLng = booking.techLongitude
        val custLat = booking.latitude
        val custLng = booking.longitude

        if (techLat == 0.0 || custLat == 0.0) return

        viewModelScope.launch {
            try {
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                if (token.isEmpty() || token == "your_token_here") {
                    activeRoutePoints.value = listOf(LatLng(techLat, techLng), LatLng(custLat, custLng))
                    return@launch
                }
                val coordsPath = "$techLng,$techLat;$custLng,$custLat"
                val response = MapboxClient.apiService.getDirections(
                    coords = coordsPath,
                    accessToken = token,
                    geometries = "polyline"
                )
                if (response.code == "Ok" && response.routes.isNotEmpty()) {
                    val route = response.routes[0]
                    val distanceKm = route.distance / 1000.0
                    val estMin = (route.duration / 60.0).toInt().coerceAtLeast(1)
                    activeRouteDistance.value = String.format("%.2f KM", distanceKm)
                    activeRouteDuration.value = "$estMin mins"

                    val geom = route.geometry
                    if (geom != null) {
                        activeRoutePoints.value = MapboxPolylineDecoder.decode(geom)
                    } else {
                        activeRoutePoints.value = listOf(LatLng(techLat, techLng), LatLng(custLat, custLng))
                    }
                } else {
                    activeRoutePoints.value = listOf(LatLng(techLat, techLng), LatLng(custLat, custLng))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activeRoutePoints.value = listOf(LatLng(techLat, techLng), LatLng(custLat, custLng))
            }
        }
    }


    /** Returns the current user's Bearer token, or falls back to anon key if not logged in. */
    private fun getBearerToken(): String {
        val stored = SecureAuthManager.getInstance(getApplication()).userToken.value
        return if (!stored.isNullOrEmpty()) "Bearer $stored" else "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
    }

    override fun onCleared() {
        super.onCleared()
        try {
            SupabaseRealtimeClient.clearCallbacks()
            SupabaseRealtimeClient.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

data class FraudAlert(
    val id: Int,
    val title: String,
    val severity: String, // CRITICAL, HIGH, LOW
    val description: String,
    val timestamp: Long,
    val associatedPhone: String,
    val isResolved: Boolean = false
) : java.io.Serializable

data class PaymentWithdrawal(
    val id: String,
    val name: String,
    val type: String, // JazzCash, EasyPaisa, Bank Transfer
    val amount: Double,
    val paymentDetails: String,
    val status: String, // Pending Approved, Under Compliance Hold, Released & Processed, Declined
    val timestamp: Long
) : java.io.Serializable

data class AnnouncementLog(
    val id: String,
    val title: String,
    val content: String,
    val targetGroup: String, // All Techs, All Customers, Karachi Only, Lahore Only
    val timestamp: Long
) : java.io.Serializable

data class SupportTicket(
    val id: Long,
    val customerPhone: String,
    val customerName: String,
    val bookingId: Long?,
    val category: String, // Delayed Arrival, Technician Behavior, Pricing Dispute, Substandard Quality, App Issue
    val description: String,
    val status: String, // Open, In Progress, Escalated, Resolved
    val timestamp: Long,
    val chatMessages: List<SupportMessage> = emptyList(),
    val slaTimerMinutes: Int = 15
) : java.io.Serializable

data class SupportMessage(
    val sender: String, // Customer, Support Agent
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) : java.io.Serializable

data class Coupon(
    val code: String,
    val discountAmount: Double,
    val description: String,
    val isReferral: Boolean = false,
    val refereeName: String? = null
) : java.io.Serializable
