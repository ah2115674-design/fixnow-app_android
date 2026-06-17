package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*

class FixNowViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = FixNowRepository(
        customerDao = database.customerDao(),
        technicianDao = database.technicianDao(),
        bookingDao = database.bookingDao(),
        earningDao = database.earningDao()
    )

    // NOTE: SupabaseClient.initialize(context) is called once in MainActivity.onCreate()
    // before this ViewModel is constructed, so AuthInterceptor is already wired up
    // by the time any apiService call happens here.

    // Helper to always get the current user JWT for explicit auth-header overrides
    private fun bearerToken(): String {
        val token = SecureAuthManager.getInstance(getApplication()).userToken.value
        return if (!token.isNullOrEmpty()) "Bearer $token"
        else "Bearer ${BuildConfig.SUPABASE_ANON_KEY}"
    }

    val searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val resolvedLocationLat = MutableStateFlow<Double?>(null)
    val resolvedLocationLng = MutableStateFlow<Double?>(null)
    val activeRoutePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val activeRouteDistance = MutableStateFlow("TBD")
    val activeRouteDuration = MutableStateFlow("Estimating...")

    private val _currentMode = MutableStateFlow("Onboarding")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _notifications = MutableStateFlow<List<String>>(emptyList())
    val notifications: StateFlow<List<String>> = _notifications.asStateFlow()

    val technicians: StateFlow<List<TechnicianProfile>> = repository.allTechnicians
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bookings: StateFlow<List<Booking>> = repository.allBookings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customers: StateFlow<List<CustomerProfile>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _customerPhone = MutableStateFlow("")
    val customerPhone: StateFlow<String> = _customerPhone.asStateFlow()

    private val _activeCustomer = MutableStateFlow<CustomerProfile?>(null)
    val activeCustomer: StateFlow<CustomerProfile?> = _activeCustomer.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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

    private val _techPhone = MutableStateFlow("")
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

    private val _currentSimulatedBooking = MutableStateFlow<Booking?>(null)
    val currentSimulatedBooking: StateFlow<Booking?> = _currentSimulatedBooking.asStateFlow()

    private val _adminTab = MutableStateFlow("Technicians")
    val adminTab: StateFlow<String> = _adminTab.asStateFlow()

    private val _isAdminAuthorized = MutableStateFlow(false)
    val isAdminAuthorized: StateFlow<Boolean> = _isAdminAuthorized.asStateFlow()

    private val _adminAuthError = MutableStateFlow<String?>(null)
    val adminAuthError: StateFlow<String?> = _adminAuthError.asStateFlow()

    val adminPasscodeInput = MutableStateFlow("")

    val googleSessionUid = MutableStateFlow<String?>(null)
    val googleSessionToken = MutableStateFlow<String?>(null)
    val googleSessionEmail = MutableStateFlow<String?>(null)
    val googleSessionName = MutableStateFlow<String?>(null)

    init {
        SupabaseRealtimeClient.setBookingCallback { bookingId, status, techLat, techLng ->
            viewModelScope.launch {
                try {
                    val existing = repository.allBookings.first().firstOrNull { it.supabaseId == bookingId }
                        ?: repository.getBooking(bookingId)
                    if (existing != null) {
                        repository.updateBooking(
                            existing.copy(
                                status = status,
                                techLatitude = techLat ?: existing.techLatitude,
                                techLongitude = techLng ?: existing.techLongitude
                            )
                        )
                        addPushNotification("🔔 Realtime update: Booking #${existing.id} → $status")
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        SupabaseRealtimeClient.setTechCallback { phone, lat, lng, isOnline ->
            viewModelScope.launch {
                try {
                    val existing = repository.getTechnician(phone)
                    if (existing != null) {
                        repository.registerTechnician(existing.copy(latitude = lat, longitude = lng, isOnline = isOnline))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        try { SupabaseRealtimeClient.connect() } catch (e: Exception) { e.printStackTrace() }

        loadCustomerInfo()
        loadTechnicianInfo()
        listenToActiveBookings()

        viewModelScope.launch {
            try {
                val authManager = SecureAuthManager.getInstance(application)
                val savedToken = authManager.userToken.value
                val savedRole = authManager.userRole.value
                val savedUid = authManager.userId.value

                if (!savedToken.isNullOrEmpty() && !savedRole.isNullOrEmpty() && !savedUid.isNullOrEmpty()) {
                    addPushNotification("🔒 Restoring session for $savedRole...")
                    when (savedRole) {
                        "customer" -> {
                            val profile = repository.allCustomers.first().firstOrNull { it.uuid == savedUid }
                                ?: repository.allCustomers.first().firstOrNull()
                            if (profile != null) {
                                _customerPhone.value = profile.phone
                                _activeCustomer.value = profile
                                _currentMode.value = "Customer"
                                addPushNotification("🔓 Welcome back, ${profile.name}.")
                            }
                        }
                        "technician" -> {
                            val profile = repository.allTechnicians.first().firstOrNull { it.uuid == savedUid }
                                ?: repository.allTechnicians.first().firstOrNull()
                            if (profile != null) {
                                _techPhone.value = profile.phone
                                _activeTechnician.value = profile
                                _currentMode.value = "Technician"
                                addPushNotification("🔓 Welcome back, ${profile.name}.")
                            }
                        }
                        "admin" -> {
                            _isAdminAuthorized.value = true
                            _currentMode.value = "Admin"
                            addPushNotification("🔓 Welcome back, Administrator.")
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun switchMode(mode: String) {
        _currentMode.value = mode
        if (mode == "Customer") loadCustomerInfo()
        else if (mode == "Technician") loadTechnicianInfo()
    }

    private fun loadCustomerInfo() {
        viewModelScope.launch {
            val phone = _customerPhone.value
            if (phone.isEmpty()) { _activeCustomer.value = null; return@launch }
            _activeCustomer.value = repository.getCustomer(phone)
        }
    }

    fun setCustomerPhone(phone: String) {
        _customerPhone.value = phone
        loadCustomerInfo()
    }

    // -------------------------------------------------------
    // CUSTOMER LOGIN / REGISTRATION
    // -------------------------------------------------------
    fun loginOrCreateCustomer(name: String, phone: String, city: String, referredBy: String = "", passwordEntered: String = "") {
        viewModelScope.launch {
            val cleanReferredBy = referredBy.trim().uppercase()
            var referredByCode: String? = null
            if (cleanReferredBy.isNotEmpty()) {
                val existsTech = technicians.value.any { it.referralCode == cleanReferredBy }
                val existsCust = repository.allCustomers.first().any { it.referralCode == cleanReferredBy }
                if (existsTech || existsCust) {
                    referredByCode = cleanReferredBy
                    addPushNotification("🎁 Referral code applied!")
                } else {
                    addPushNotification("⚠️ Referral code '$cleanReferredBy' not found.")
                }
            }

            val gUid = googleSessionUid.value
            val gToken = googleSessionToken.value
            val gEmail = googleSessionEmail.value

            if (!gUid.isNullOrEmpty() && !gToken.isNullOrEmpty() && !gEmail.isNullOrEmpty()) {
                try {
                    addPushNotification("🔗 Linking phone to your Google Account...")
                    // FIX: Use the Google token (real JWT) not the anon key
                    SecureAuthManager.getInstance(getApplication()).saveSession(gToken, "customer", gUid)
                    val updateResponse = SupabaseClient.apiService.updateCustomerProfile(
                        id = "eq.$gUid",
                        body = mapOf("phone" to phone.trim(), "city" to city),
                        apiKey = BuildConfig.SUPABASE_ANON_KEY
                    )
                    if (updateResponse.isSuccessful) {
                        val referral = generateReferralCode(name, phone, "CUST")
                        val customer = CustomerProfile(
                            phone = phone.trim(), uuid = gUid, name = name,
                            email = gEmail, city = city, referralCode = referral,
                            referredByCode = referredByCode
                        )
                        repository.registerCustomer(customer)
                        _customerPhone.value = phone.trim()
                        _activeCustomer.value = customer
                        addPushNotification("🟢 Phone linked! Welcome, $name!")
                        googleSessionUid.value = null
                        googleSessionToken.value = null
                        googleSessionEmail.value = null
                        googleSessionName.value = null
                    } else {
                        addPushNotification("❌ Failed to link phone on server.")
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
            var syncedName: String? = null
            var syncedCity: String? = null

            // 1. Try Supabase login
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
                    syncedName = meta?.get("name")
                    syncedCity = meta?.get("city")
                    // FIX: Save the real JWT immediately so all subsequent calls use it
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "customer", supabaseUserId)
                    addPushNotification("🟢 Supabase login successful!")
                } else {
                    // Try signup
                    addPushNotification("🔑 Not registered — creating account...")
                    val signUpResponse = SupabaseClient.apiService.customerSignUp(
                        SupabaseCustomerSignUpRequest(
                            email = emailStr,
                            password = finalPassword,
                            options = CustomerSignUpOptions(
                                // FIX: CustomerMetadata now has @JsonClass annotation so
                                // Moshi serializes it correctly in release builds
                                data = CustomerMetadata(name = name, phone = phone, city = city)
                            )
                        ),
                        BuildConfig.SUPABASE_ANON_KEY
                    )
                    if (signUpResponse.isSuccessful && signUpResponse.body()?.user != null) {
                        supabaseUserId = signUpResponse.body()?.user?.id ?: ""
                        token = signUpResponse.body()?.accessToken ?: ""
                        val meta = signUpResponse.body()?.user?.userMetadata
                        syncedName = meta?.get("name")
                        syncedCity = meta?.get("city")
                        if (token.isNotEmpty()) {
                            SecureAuthManager.getInstance(getApplication()).saveSession(token, "customer", supabaseUserId)
                        }
                        addPushNotification("🎉 Registered! Profile created via DB trigger.")
                    } else {
                        addPushNotification("⚠️ Supabase auth failed. Proceeding locally.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Connection timeout, running in offline mode.")
            }

            val finalName = if (!syncedName.isNullOrEmpty()) syncedName else name
            val finalCity = if (!syncedCity.isNullOrEmpty()) syncedCity else city

            var existing = repository.getCustomer(phone)

            // 2. Try to pull profile from Supabase table (JWT is now set so RLS allows it)
            if (existing == null) {
                try {
                    addPushNotification("🔍 Syncing profile from cloud...")
                    val matchedCusts = SupabaseClient.apiService.getCustomer(
                        phone = "eq.${phone.trim()}",
                        apiKey = BuildConfig.SUPABASE_ANON_KEY
                    )
                    if (matchedCusts.isNotEmpty()) {
                        val dto = matchedCusts[0]
                        val customer = CustomerProfile(
                            phone = dto.phone, uuid = dto.id, name = dto.name,
                            email = dto.email, city = dto.city, password = finalPassword
                        )
                        repository.registerCustomer(customer)
                        existing = customer
                        addPushNotification("🟢 Synced '${dto.name}' from cloud!")
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // 3. Build from auth metadata as last resort
            if (existing == null && supabaseUserId.isNotEmpty()) {
                val code = generateReferralCode(finalName, phone, "CUST")
                val customer = CustomerProfile(
                    phone = phone, uuid = supabaseUserId, name = finalName,
                    email = emailStr, city = finalCity, referralCode = code,
                    referredByCode = referredByCode,
                    password = if (rawPassword.isNotEmpty()) rawPassword else finalPassword
                )
                repository.registerCustomer(customer)
                existing = customer
                addPushNotification("🟢 Profile restored from auth metadata!")
            }

            if (existing != null) {
                val isPasswordCorrect = if (supabaseUserId.isNotEmpty()) true
                else if (existing.password.isEmpty()) true
                else existing.password == rawPassword || existing.password == finalPassword

                if (!isPasswordCorrect) {
                    addPushNotification("❌ Incorrect password for $phone.")
                    return@launch
                }

                val code = if (existing.referralCode.isEmpty()) generateReferralCode(finalName, phone, "CUST") else existing.referralCode
                val updatedPass = if (existing.password.isEmpty()) { if (rawPassword.isNotEmpty()) rawPassword else finalPassword } else existing.password
                val customer = existing.copy(
                    uuid = if (supabaseUserId.isNotEmpty()) supabaseUserId else existing.uuid,
                    name = if (finalName.startsWith("User_") && existing.name.isNotEmpty()) existing.name else finalName,
                    city = finalCity, referralCode = code,
                    referredByCode = if (existing.referredByCode.isNullOrEmpty()) referredByCode else existing.referredByCode,
                    password = updatedPass
                )
                repository.registerCustomer(customer)
                _customerPhone.value = phone
                _activeCustomer.value = customer
                addPushNotification("Welcome back, ${customer.name}!")
            } else {
                val code = generateReferralCode(finalName, phone, "CUST")
                val customer = CustomerProfile(
                    phone = phone, uuid = supabaseUserId, name = finalName,
                    email = emailStr, city = finalCity, referralCode = code,
                    referredByCode = referredByCode,
                    password = if (rawPassword.isNotEmpty()) rawPassword else finalPassword
                )
                repository.registerCustomer(customer)
                _customerPhone.value = phone
                _activeCustomer.value = customer
                addPushNotification("Welcome to FixNow, $finalName! Registered in $finalCity.")
            }
        }
    }

    // -------------------------------------------------------
    // TECHNICIAN INFO HELPERS
    // -------------------------------------------------------
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
            addPushNotification("Availability set to ${if (online) "Online" else "Offline"}.")
        }
    }

    fun selectCategory(category: String?) { _selectedCategory.value = category }
    fun selectSearchQuery(query: String) { _searchQuery.value = query }
    fun setDraftService(name: String, category: String, basePrice: Double) {
        _draftServiceName.value = name
        _selectedCategory.value = category
        _draftPrice.value = basePrice
    }

    // -------------------------------------------------------
    // TECHNICIAN REGISTRATION
    // -------------------------------------------------------
    fun registerNewTechnician() {
        viewModelScope.launch {
            if (regName.value.isEmpty() || regPhone.value.isEmpty() || regCNIC.value.isEmpty() || regPassword.value.isEmpty()) {
                addPushNotification("❌ Missing fields: Name, Phone, CNIC, Password required.")
                return@launch
            }
            if (regPassword.value.length < 4) {
                addPushNotification("❌ Password must be at least 4 characters.")
                return@launch
            }

            val cleanReferredBy = regReferredByCode.value.trim().uppercase()
            var referredByCode: String? = null
            if (cleanReferredBy.isNotEmpty()) {
                val existsTech = technicians.value.any { it.referralCode == cleanReferredBy }
                val existsCust = repository.allCustomers.first().any { it.referralCode == cleanReferredBy }
                referredByCode = if (existsTech || existsCust) cleanReferredBy else null
            }

            val isKarachi = regCity.value.contains("Karachi", ignoreCase = true)
            val isIslamabad = regCity.value.contains("Islamabad", ignoreCase = true) || regCity.value.contains("Rawalpindi", ignoreCase = true)
            val baseLat = if (isKarachi) 24.8607 else if (isIslamabad) 33.6844 else 31.5204
            val baseLng = if (isKarachi) 67.0011 else if (isIslamabad) 73.0479 else 74.3587

            val code = generateReferralCode(regName.value, regPhone.value, "TECH")
            val emailStr = "${regPhone.value.trim()}@fixnow.com"
            val finalPassword = regPassword.value.trim()
            var supabaseUserId = ""

            try {
                addPushNotification("🔒 Creating technician credentials in Supabase Auth...")
                val techResponse = SupabaseClient.apiService.techSignUp(
                    SupabaseTechSignUpRequest(
                        email = emailStr,
                        password = finalPassword,
                        options = TechSignUpOptions(
                            // FIX: TechMetadata now has @JsonClass so Moshi serializes correctly
                            data = TechMetadata(
                                name = regName.value, phone = regPhone.value,
                                city = regCity.value, category = regCategory.value,
                                cnic = regCNIC.value
                            )
                        )
                    ),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (techResponse.isSuccessful && techResponse.body()?.user != null) {
                    supabaseUserId = techResponse.body()?.user?.id ?: ""
                    val token = techResponse.body()?.accessToken ?: ""
                    // FIX: Save the signup token immediately
                    if (token.isNotEmpty()) {
                        SecureAuthManager.getInstance(getApplication()).saveSession(token, "technician", supabaseUserId)
                    }
                    addPushNotification("🟢 Registered on cloud! Awaiting CNIC & admin review.")
                } else {
                    addPushNotification("⚠️ Auth: ${techResponse.errorBody()?.string() ?: "Registration request failed"}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Auth server offline, queuing locally.")
            }

            val tech = TechnicianProfile(
                phone = regPhone.value, uuid = supabaseUserId,
                name = regName.value, category = regCategory.value,
                subCategory = regSubCat.value, city = regCity.value,
                cnic = regCNIC.value, selfieUrl = regSelfieUrl.value,
                bankDetails = regBankDetails.value,
                isApproved = false, // FIX: Always false — admin must approve
                isOnline = false, rating = 4.5, totalJobs = 0,
                latitude = baseLat, longitude = baseLng,
                referralCode = code, referredByCode = referredByCode,
                password = finalPassword
            )
            repository.registerTechnician(tech)
            _techPhone.value = tech.phone
            _activeTechnician.value = tech
            _isRegSuccess.value = true
            addPushNotification("✅ Application submitted! Awaiting admin approval. Code: $code")
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

    // -------------------------------------------------------
    // TECHNICIAN LOGIN
    // -------------------------------------------------------
    fun loginTechnician(phone: String, pin: String) {
        viewModelScope.launch {
            val normalizedPhone = phone.trim()
            val normalizedPin = pin.trim()
            if (normalizedPhone.isEmpty()) { _techLoginError.value = "⚠️ Enter a phone number."; return@launch }
            if (normalizedPin.isEmpty()) { _techLoginError.value = "⚠️ Enter your PIN."; return@launch }

            var supabaseUserId = ""
            var token = ""
            var syncedName: String? = null
            var syncedCategory: String? = null
            var syncedCity: String? = null
            var syncedCnic: String? = null

            val emailStr = "${normalizedPhone}@fixnow.com"
            // Derive the password from the PIN the user entered
            val passwordForSupabase = if (normalizedPin.length >= 6) normalizedPin else "FixNow_${normalizedPhone}_pass"

            try {
                addPushNotification("🔒 Verifying with Supabase Auth...")
                val loginResponse = SupabaseClient.apiService.signIn(
                    SupabaseSignInRequest(emailStr, passwordForSupabase),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (loginResponse.isSuccessful && loginResponse.body()?.accessToken != null) {
                    supabaseUserId = loginResponse.body()?.user?.id ?: ""
                    token = loginResponse.body()?.accessToken ?: ""
                    val meta = loginResponse.body()?.user?.userMetadata
                    syncedName = meta?.get("name")
                    syncedCategory = meta?.get("category")
                    syncedCity = meta?.get("city")
                    syncedCnic = meta?.get("cnic")
                    // FIX: Save real JWT immediately after successful Supabase auth
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "technician", supabaseUserId)
                    addPushNotification("🟢 Verified with Supabase!")
                } else {
                    addPushNotification("🔑 Supabase auth failed, trying local profile...")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Offline, checking local database...")
            }

            var profile = repository.getTechnician(normalizedPhone)

            // Pull from Supabase tables if not found locally (JWT now set, RLS allows it)
            if (profile == null && supabaseUserId.isNotEmpty()) {
                try {
                    addPushNotification("🔍 Syncing technician profile from cloud...")
                    val matchedTechs = SupabaseClient.apiService.getTechnician(
                        phone = "eq.$normalizedPhone",
                        apiKey = BuildConfig.SUPABASE_ANON_KEY
                    )
                    if (matchedTechs.isNotEmpty()) {
                        val dto = matchedTechs[0]
                        val tech = TechnicianProfile(
                            phone = dto.phone, uuid = dto.id, name = dto.name,
                            category = dto.category, subCategory = dto.subCategory ?: "",
                            city = dto.city, cnic = dto.cnic,
                            selfieUrl = dto.selfieUrl ?: "mock_selfie_url",
                            bankDetails = dto.bankDetails ?: "",
                            isApproved = dto.isApproved, isOnline = dto.isOnline,
                            rating = dto.rating, totalJobs = dto.totalJobs,
                            acceptanceRate = dto.acceptanceRate,
                            latitude = dto.latitude, longitude = dto.longitude,
                            password = normalizedPin
                        )
                        repository.registerTechnician(tech)
                        profile = tech
                        addPushNotification("🟢 Synced '${tech.name}' from cloud!")
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Restore from auth metadata as last resort
            if (profile == null && supabaseUserId.isNotEmpty()) {
                val tech = TechnicianProfile(
                    phone = normalizedPhone, uuid = supabaseUserId,
                    name = syncedName ?: "Restored Tech",
                    category = syncedCategory ?: "Electrical Services",
                    subCategory = "General Vetted Specialist",
                    city = syncedCity ?: "Lahore",
                    cnic = syncedCnic ?: "00000-0000000-0",
                    selfieUrl = "mock_selfie_url", bankDetails = "EasyPaisa",
                    // FIX: isApproved = false — admin must approve. Was hardcoded true.
                    isApproved = false,
                    isOnline = false, rating = 4.8, totalJobs = 0,
                    acceptanceRate = 1.0, latitude = 31.5204, longitude = 74.3587,
                    password = normalizedPin
                )
                repository.registerTechnician(tech)
                profile = tech
                addPushNotification("🟢 Profile restored from auth metadata. Pending admin approval.")
            }

            if (profile == null) {
                _techLoginError.value = "❌ Phone not registered. Please register first."
                return@launch
            }

            // Local PIN verification
            // FIX: No longer bypassing pin check. Only bypass if Supabase auth succeeded.
            val isPasswordCorrect = if (supabaseUserId.isNotEmpty()) {
                true // Supabase auth already verified the credential
            } else {
                normalizedPin == profile.password
            }

            if (!isPasswordCorrect) {
                _techLoginError.value = "❌ Incorrect PIN."
                addPushNotification("❌ Access Denied: Incorrect PIN for $normalizedPhone.")
                return@launch
            }

            val updatedProfile = if (supabaseUserId.isNotEmpty() && profile.uuid.isEmpty()) {
                val p = profile.copy(uuid = supabaseUserId)
                repository.registerTechnician(p)
                p
            } else profile

            _techPhone.value = normalizedPhone
            _activeTechnician.value = updatedProfile
            _techLoginError.value = null
            addPushNotification("🔓 Welcome, ${updatedProfile.name}!")
        }
    }

    fun logoutTechnician() {
        _techPhone.value = ""
        _activeTechnician.value = null
        _techLoginError.value = null
        _isRegSuccess.value = false
        viewModelScope.launch {
            try { SecureAuthManager.getInstance(getApplication()).clearSession() } catch (e: Exception) { e.printStackTrace() }
        }
        addPushNotification("Logged out from Technician Workbench.")
    }

    fun logoutCustomer() {
        _customerPhone.value = ""
        _activeCustomer.value = null
        viewModelScope.launch {
            try { SecureAuthManager.getInstance(getApplication()).clearSession() } catch (e: Exception) { e.printStackTrace() }
        }
        addPushNotification("Logged out from Customer Portal.")
    }

    fun loginWithGoogleToken(idToken: String, callback: (success: Boolean, requiresPhoneLinkage: Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                addPushNotification("🔒 Verifying Google credentials...")
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
                    // FIX: Save real JWT immediately
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "customer", uid)
                    addPushNotification("🟢 Authenticated with Google!")

                    var existingCustomer: CustomerProfile? = null
                    try {
                        val remoteRecords = SupabaseClient.apiService.getCustomerById(
                            id = "eq.$uid", apiKey = BuildConfig.SUPABASE_ANON_KEY
                        )
                        if (remoteRecords.isNotEmpty()) {
                            val dto = remoteRecords[0]
                            if (dto.phone.isNotEmpty() && dto.phone.startsWith("03")) {
                                existingCustomer = CustomerProfile(
                                    phone = dto.phone, uuid = dto.id, name = dto.name,
                                    email = dto.email, city = dto.city
                                )
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }

                    if (existingCustomer != null) {
                        repository.registerCustomer(existingCustomer)
                        _customerPhone.value = existingCustomer.phone
                        _activeCustomer.value = existingCustomer
                        addPushNotification("Welcome back, ${existingCustomer.name}!")
                        callback(true, false)
                    } else {
                        googleSessionUid.value = uid
                        googleSessionToken.value = token
                        googleSessionEmail.value = email
                        googleSessionName.value = name
                        callback(true, true)
                    }
                } else {
                    addPushNotification("❌ Google authentication failed.")
                    callback(false, false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Google auth timeout.")
                callback(false, false)
            }
        }
    }

    fun clearTechLoginError() { _techLoginError.value = null }

    // -------------------------------------------------------
    // ADMIN LOGIN
    // FIX: Removed hardcoded "admin" / "admin123" backdoor that granted
    // full admin access completely offline without any real credentials.
    // Now requires successful Supabase auth only. No fallback bypass.
    // -------------------------------------------------------
    fun loginAdmin(passcode: String) {
        val code = passcode.trim()
        if (code.isEmpty()) { _adminAuthError.value = "⚠️ Enter admin credentials."; return }
        viewModelScope.launch {
            val emailStr = if (code.contains("@")) code else "admin@fixnow.com"
            val passwordStr = code
            try {
                addPushNotification("🔒 Requesting admin authorization...")
                val loginResponse = SupabaseClient.apiService.signIn(
                    SupabaseSignInRequest(emailStr, passwordStr),
                    BuildConfig.SUPABASE_ANON_KEY
                )
                if (loginResponse.isSuccessful && loginResponse.body()?.accessToken != null) {
                    val token = loginResponse.body()?.accessToken ?: ""
                    val uid = loginResponse.body()?.user?.id ?: ""
                    SecureAuthManager.getInstance(getApplication()).saveSession(token, "admin", uid)
                    _isAdminAuthorized.value = true
                    _adminAuthError.value = null
                    addPushNotification("🔓 Admin authorized via Supabase.")
                } else {
                    _adminAuthError.value = "❌ Authentication failed. Incorrect admin passphrase."
                    addPushNotification("❌ Admin login rejected.")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _adminAuthError.value = "❌ Network timeout. Cannot authorize offline."
            }
        }
    }

    fun clearAdminAuthError() { _adminAuthError.value = null }
    fun logoutAdmin() {
        _isAdminAuthorized.value = false
        adminPasscodeInput.value = ""
        _adminAuthError.value = null
        addPushNotification("Admin console closed.")
    }

    // -------------------------------------------------------
    // BOOKING ENGINE
    // -------------------------------------------------------
    fun submitBooking() {
        viewModelScope.launch {
            _isSubmittingBooking.value = true
            val customer = _activeCustomer.value ?: CustomerProfile(
                phone = _customerPhone.value, name = "Guest User",
                email = "guest@example.com", city = "Lahore"
            )

            val isLahore = customer.city.contains("Lahore", ignoreCase = true)
            val isIslamabad = customer.city.contains("Islamabad", ignoreCase = true)
            val baseLat = if (isLahore) 31.5204 else if (isIslamabad) 33.6844 else 24.8607
            val baseLng = if (isLahore) 74.3587 else if (isIslamabad) 73.0479 else 67.0011

            var finalLat = resolvedLocationLat.value ?: baseLat
            var finalLng = resolvedLocationLng.value ?: baseLng

            if (!draftUseLiveLocation.value && resolvedLocationLat.value == null) {
                try {
                    val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                    if (token.isNotEmpty() && token != "your_token_here") {
                        val response = MapboxClient.apiService.searchPlaces(query = draftAddress.value, accessToken = token)
                        if (response.features.isNotEmpty()) {
                            finalLng = response.features[0].center[0]
                            finalLat = response.features[0].center[1]
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            // Sync customer UUID if missing
            var resolvedCustId = customer.uuid
            if (resolvedCustId.isEmpty()) {
                try {
                    val matchedCusts = SupabaseClient.apiService.getCustomer(
                        phone = "eq.${customer.phone}", apiKey = BuildConfig.SUPABASE_ANON_KEY
                    )
                    if (matchedCusts.isNotEmpty()) {
                        resolvedCustId = matchedCusts[0].id
                        val updatedCust = customer.copy(uuid = resolvedCustId)
                        repository.registerCustomer(updatedCust)
                        _activeCustomer.value = updatedCust
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            val category = _selectedCategory.value ?: "Electrical Services"
            val price = _draftPrice.value

            val bookingDto = BookingDto(
                serviceCategory = category,
                serviceName = _draftServiceName.value,
                issueDescription = draftDescription.value,
                customerId = if (resolvedCustId.isNotEmpty()) resolvedCustId else "00000000-0000-0000-0000-000000000000",
                customerPhone = customer.phone,
                customerName = customer.name,
                customerAddress = draftAddress.value,
                customerCity = customer.city,
                preferredTime = draftTimeSlot.value,
                paymentMethod = draftPaymentMethod.value,
                price = price,
                status = "Requested",
                latitude = finalLat,
                longitude = finalLng,
                // FIX: techLatitude/techLongitude start at 0.0, NOT the customer's location.
                // They were previously set to finalLat/finalLng, making the map show a
                // technician already at the customer's door before any tech accepted.
                techLatitude = 0.0,
                techLongitude = 0.0
            )

            var remoteBookingId: Long? = null
            try {
                addPushNotification("📡 Syncing booking to Supabase...")
                val resList = SupabaseClient.apiService.createBooking(
                    booking = bookingDto,
                    apiKey = BuildConfig.SUPABASE_ANON_KEY
                )
                if (resList.isNotEmpty()) {
                    remoteBookingId = resList[0].id
                    addPushNotification("🟢 Cloud sync OK! Booking ID: $remoteBookingId")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Offline — booking cached locally.")
            }

            val newBooking = Booking(
                supabaseId = remoteBookingId,
                serviceCategory = category,
                serviceName = _draftServiceName.value,
                issueDescription = draftDescription.value,
                customerId = resolvedCustId,
                customerPhone = customer.phone,
                customerName = customer.name,
                customerAddress = draftAddress.value,
                customerCity = customer.city,
                preferredTime = draftTimeSlot.value,
                paymentMethod = draftPaymentMethod.value,
                price = price,
                status = "Requested",
                declinedTechnicians = "",
                isManualAssign = false,
                latitude = finalLat,
                longitude = finalLng,
                techLatitude = 0.0, // FIX: start at 0.0
                techLongitude = 0.0  // FIX: start at 0.0
            )

            val bookingId = repository.createBooking(newBooking)
            _isSubmittingBooking.value = false
            addPushNotification("Booking created! Matching technician...")
            simulateMatchmaker(bookingId)
        }
    }

    private suspend fun simulateMatchmaker(bookingId: Long) {
        val booking = repository.getBooking(bookingId) ?: return
        if (booking.status != "Requested") return

        addPushNotification("🔍 Finding nearest ${booking.serviceCategory} expert in ${booking.customerCity}...")
        delay(1500)

        var matchedTech: TechnicianProfile? = null
        try {
            addPushNotification("🛰️ Querying PostGIS server-side discovery...")
            val response = SupabaseClient.apiService.findNearbyTechniciansRpc(
                TechDiscoveryParams(booking.latitude, booking.longitude, booking.customerCity, booking.serviceCategory),
                BuildConfig.SUPABASE_ANON_KEY
            )
            if (response.isSuccessful && !response.body().isNullOrEmpty()) {
                val discoveryList = response.body()!!
                addPushNotification("📡 Found ${discoveryList.size} nearby experts!")
                matchedTech = repository.getTechnician(discoveryList.first().phone)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            addPushNotification("❗ PostGIS offline, using local fallback.")
        }

        if (matchedTech == null) {
            matchedTech = repository.findNearestQualifiedTechnician(booking)
        }

        if (matchedTech != null) {
            val updatedBooking = booking.copy(
                status = "Requested",
                technicianId = matchedTech.uuid,
                technicianPhone = matchedTech.phone,
                technicianName = matchedTech.name,
                techLatitude = matchedTech.latitude,
                techLongitude = matchedTech.longitude
            )
            repository.updateBooking(updatedBooking)

            viewModelScope.launch {
                try {
                    val updateMap = mapOf(
                        "technician_id" to matchedTech.uuid,
                        "technician_phone" to matchedTech.phone,
                        "technician_name" to matchedTech.name,
                        "tech_latitude" to matchedTech.latitude,
                        "tech_longitude" to matchedTech.longitude
                    )
                    if (booking.supabaseId != null) {
                        SupabaseClient.apiService.updateBookingStatus(
                            id = "eq.${booking.supabaseId}",
                            body = updateMap,
                            apiKey = BuildConfig.SUPABASE_ANON_KEY
                        )
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            addPushNotification("🔔 Matching! Offered to ${matchedTech.name} (${haversineKm(matchedTech.latitude, matchedTech.longitude, booking.latitude, booking.longitude)} km away)")
            addWhatsAppUpdate(matchedTech.phone, "FixNow: New request! ${booking.serviceName} in ${booking.customerCity}. Earn Rs. ${booking.price.toInt()}. View in app.")
        } else {
            addPushNotification("⚠️ No online approved technicians in ${booking.customerCity} for ${booking.serviceCategory}.")
        }
    }

    private fun listenToActiveBookings() {
        viewModelScope.launch {
            bookings.collect { bookingList ->
                val active = bookingList.firstOrNull {
                    it.customerPhone == _customerPhone.value && it.status != "Completed" && it.status != "Cancelled"
                }
                _currentSimulatedBooking.value = active
            }
        }
    }

    // -------------------------------------------------------
    // TECHNICIAN ACTIONS
    // -------------------------------------------------------
    fun acceptBooking(bookingId: Long) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val tech = _activeTechnician.value
            if (tech == null) { addPushNotification("❌ Not logged in as technician."); return@launch }
            if (!tech.isApproved) { addPushNotification("⚠️ Not yet approved to accept jobs."); return@launch }

            val supabaseId = booking.supabaseId
            var success = false

            try {
                if (supabaseId != null) {
                    addPushNotification("🔒 Requesting exclusive lock from Supabase...")
                    val rpcResponse = SupabaseClient.apiService.acceptBookingRpc(
                        params = AcceptBookingParams(bookingId = supabaseId, technicianId = tech.uuid),
                        apiKey = BuildConfig.SUPABASE_ANON_KEY
                    )
                    success = if (rpcResponse.isSuccessful) {
                        (rpcResponse.body() ?: false).also {
                            if (it) addPushNotification("🟢 Locked & assigned to you!")
                            else addPushNotification("❌ Booking already assigned.")
                        }
                    } else {
                        addPushNotification("❌ Cloud lock failed: ${rpcResponse.errorBody()?.string() ?: rpcResponse.message()}")
                        false
                    }
                } else {
                    success = true // Offline fallback
                }
            } catch (e: Exception) {
                e.printStackTrace()
                addPushNotification("⚠️ Network error. Cannot verify exclusive lock.")
            }

            if (success) {
                repository.updateBooking(booking.copy(
                    status = "Assigned",
                    technicianPhone = tech.phone,
                    technicianName = tech.name,
                    technicianId = tech.uuid
                ))
                addPushNotification("✅ Job accepted for ${booking.customerName}!")
                addWhatsAppUpdate(booking.customerPhone, "FixNow: ${tech.name} is assigned! Contact: ${tech.phone}")
            } else {
                addPushNotification("❌ Job no longer available.")
            }
        }
    }

    fun declineBooking(bookingId: Long) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val updatedDeclined = if (booking.declinedTechnicians.isEmpty()) _techPhone.value
            else "${booking.declinedTechnicians},${_techPhone.value}"
            repository.updateBooking(booking.copy(
                status = "Requested", technicianPhone = null, technicianName = null,
                declinedTechnicians = updatedDeclined
            ))
            addPushNotification("Re-routing to next nearest expert...")
            delay(1000)
            simulateMatchmaker(bookingId)
        }
    }

    // -------------------------------------------------------
    // ADVANCE JOB STATUS
    // FIX: Now syncs status to Supabase on every advance, not just location tracking.
    // Previously only the location loop wrote to Supabase — the status field never
    // updated in the cloud so the customer on another device saw nothing change.
    // -------------------------------------------------------
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
            if (nextStatus == booking.status) return@launch

            if (nextStatus == "Technician En Route") startLocationTrackingSimulation(bookingId)

            repository.updateBookingStatus(bookingId, nextStatus)

            // FIX: Sync the status to Supabase cloud immediately on every state change
            viewModelScope.launch {
                try {
                    if (booking.supabaseId != null) {
                        SupabaseClient.apiService.updateBookingStatus(
                            id = "eq.${booking.supabaseId}",
                            body = mapOf("status" to nextStatus),
                            apiKey = BuildConfig.SUPABASE_ANON_KEY
                        )
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            val msg = when (nextStatus) {
                "Technician En Route" -> "🚀 ${_activeTechnician.value?.name ?: "Technician"} is en route!"
                "Arrived" -> "🔔 Technician arrived at ${booking.customerAddress}!"
                "In Progress" -> "🛠️ Work in progress!"
                "Completed" -> "🎉 Task completed! Please rate and pay."
                else -> "Status → $nextStatus"
            }
            addPushNotification(msg)
            addWhatsAppUpdate(booking.customerPhone, "FixNow: Booking is now '$nextStatus'.")
        }
    }

    fun submitCustomerReview(bookingId: Long, stars: Int, review: String) {
        viewModelScope.launch {
            // FIX: submitReview in repository now checks if rating was already set
            // before incrementing totalJobs, preventing double-counting on re-review.
            repository.submitReview(bookingId, stars, review)

            // Sync review to Supabase
            viewModelScope.launch {
                try {
                    val booking = repository.getBooking(bookingId) ?: return@launch
                    if (booking.supabaseId != null) {
                        SupabaseClient.apiService.updateBookingStatus(
                            id = "eq.${booking.supabaseId}",
                            body = mapOf("rating" to stars, "review_comment" to review),
                            apiKey = BuildConfig.SUPABASE_ANON_KEY
                        )
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            addPushNotification("🌟 Feedback recorded. Thank you!")
            _currentSimulatedBooking.value = null
        }
    }

    // -------------------------------------------------------
    // LIVE LOCATION TRACKING
    // -------------------------------------------------------
    private fun startLocationTrackingSimulation(bookingId: Long) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val techPhoneLoc = booking.technicianPhone ?: return@launch
            val startLat = booking.techLatitude
            val startLng = booking.techLongitude
            val destLat = booking.latitude
            val destLng = booking.longitude

            // Skip if tech coordinates not yet known
            if (startLat == 0.0 && startLng == 0.0) return@launch

            var routePoints: List<LatLng> = emptyList()
            try {
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                if (token.isNotEmpty() && token != "your_token_here") {
                    val response = MapboxClient.apiService.getDirections(
                        coords = "$startLng,$startLat;$destLng,$destLat",
                        accessToken = token, geometries = "polyline"
                    )
                    if (response.code == "Ok" && response.routes.isNotEmpty()) {
                        val geom = response.routes[0].geometry
                        if (geom != null) routePoints = MapboxPolylineDecoder.decode(geom)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (routePoints.isEmpty()) {
                routePoints = List(8) { step ->
                    val factor = step.toDouble() / 7.0
                    LatLng(startLat + (destLat - startLat) * factor, startLng + (destLng - startLng) * factor)
                }
            }

            activeRoutePoints.value = routePoints

            for (step in routePoints.indices) {
                val currentBooking = repository.getBooking(bookingId) ?: break
                if (currentBooking.status != "Technician En Route") break

                val crd = routePoints[step]
                repository.updateBookingTechCoordinates(bookingId, crd.latitude, crd.longitude)
                repository.updateTechnicianLocation(techPhoneLoc, crd.latitude, crd.longitude)

                viewModelScope.launch {
                    try {
                        if (currentBooking.supabaseId != null) {
                            SupabaseClient.apiService.updateBookingStatus(
                                id = "eq.${currentBooking.supabaseId}",
                                body = mapOf("tech_latitude" to crd.latitude, "tech_longitude" to crd.longitude),
                                apiKey = BuildConfig.SUPABASE_ANON_KEY
                            )
                        }
                        val matchedTech = repository.getTechnician(techPhoneLoc)
                        if (matchedTech != null && matchedTech.uuid.isNotEmpty()) {
                            SupabaseClient.apiService.updateTechnicianProfile(
                                id = "eq.${matchedTech.uuid}",
                                body = mapOf("latitude" to crd.latitude, "longitude" to crd.longitude),
                                apiKey = BuildConfig.SUPABASE_ANON_KEY
                            )
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // FIX: Use Haversine for distance, not Euclidean degrees × 100
                var currentDist = String.format("%.2f KM", haversineKm(crd.latitude, crd.longitude, destLat, destLng))
                var currentEta = "${(haversineKm(crd.latitude, crd.longitude, destLat, destLng) * 2).toInt().coerceAtLeast(1)} mins"

                try {
                    val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                    if (token.isNotEmpty() && token != "your_token_here") {
                        val response = MapboxClient.apiService.getDirections(
                            coords = "${crd.longitude},${crd.latitude};$destLng,$destLat",
                            accessToken = token, geometries = "polyline"
                        )
                        if (response.code == "Ok" && response.routes.isNotEmpty()) {
                            val route = response.routes[0]
                            currentDist = String.format("%.2f KM", route.distance / 1000.0)
                            currentEta = "${(route.duration / 60.0).toInt().coerceAtLeast(1)} mins"
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }

                activeRouteDistance.value = currentDist
                activeRouteDuration.value = currentEta
                addPushNotification("📍 Update: $currentDist remaining | ETA: $currentEta")
                delay(3000)
            }
        }
    }

    // -------------------------------------------------------
    // ADMIN FUNCTIONS
    // -------------------------------------------------------
    fun setAdminTab(tab: String) { _adminTab.value = tab }

    fun approveTechnician(phone: String) {
        viewModelScope.launch {
            repository.updateTechnicianApproval(phone, true)
            addPushNotification("👨‍🔧 Approved! Technician can now accept jobs.")
            try {
                val tech = repository.getTechnician(phone)
                if (tech != null && tech.uuid.isNotEmpty()) {
                    SupabaseClient.apiService.updateTechnicianProfile(
                        id = "eq.${tech.uuid}",
                        body = mapOf("is_approved" to true),
                        apiKey = BuildConfig.SUPABASE_ANON_KEY
                    )
                    addPushNotification("🟢 Approval synced to Supabase.")
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun rejectTechnician(phone: String) {
        viewModelScope.launch {
            repository.updateTechnicianApproval(phone, false)
            addPushNotification("👨‍🔧 Rejected due to CNIC validation mismatch.")
            try {
                val tech = repository.getTechnician(phone)
                if (tech != null && tech.uuid.isNotEmpty()) {
                    SupabaseClient.apiService.updateTechnicianProfile(
                        id = "eq.${tech.uuid}",
                        body = mapOf("is_approved" to false),
                        apiKey = BuildConfig.SUPABASE_ANON_KEY
                    )
                    addPushNotification("🔴 Rejection synced to Supabase.")
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun manualAssignTechnician(bookingId: Long, techPhone: String) {
        viewModelScope.launch {
            val booking = repository.getBooking(bookingId) ?: return@launch
            val tech = repository.getTechnician(techPhone) ?: return@launch
            repository.updateBooking(booking.copy(
                status = "Assigned", technicianPhone = tech.phone,
                technicianName = tech.name, isManualAssign = true,
                techLatitude = tech.latitude, techLongitude = tech.longitude
            ))
            addPushNotification("🛠️ Admin Override: Job #$bookingId assigned to ${tech.name}.")
        }
    }

    fun cancelActiveBooking(bookingId: Long) {
        viewModelScope.launch {
            repository.updateBookingStatus(bookingId, "Cancelled")
            viewModelScope.launch {
                try {
                    val booking = repository.getBooking(bookingId)
                    if (booking?.supabaseId != null) {
                        SupabaseClient.apiService.updateBookingStatus(
                            id = "eq.${booking.supabaseId}",
                            body = mapOf("status" to "Cancelled"),
                            apiKey = BuildConfig.SUPABASE_ANON_KEY
                        )
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            addPushNotification("❌ Booking #$bookingId cancelled.")
        }
    }

    fun blockTechnicianProfile(phone: String) {
        viewModelScope.launch {
            repository.updateTechnicianApproval(phone, false)
            repository.updateTechnicianOnlineStatus(phone, false)
            addPushNotification("🚨 Technician $phone suspended.")
        }
    }

    // -------------------------------------------------------
    // NOTIFICATIONS & HELPERS
    // -------------------------------------------------------
    fun addPushNotification(msg: String) {
        viewModelScope.launch {
            _notifications.update { current -> listOf(msg) + current.take(15) }
            try {
                val title = if (msg.contains("WhatsApp", ignoreCase = true)) "💬 WhatsApp Update" else "🔔 FixNow Update"
                val cleanMsg = msg.replace(Regex("[🔔🔒🔓🟢⚠️🎉❌🛰️📡🎁💬🔑🛠️👨‍🔧📍🚀🎫🎟️🛡️💸🚨🔴🟠]"), "").trim()
                com.example.NotificationHelper.showSystemNotification(getApplication(), title, cleanMsg)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun addWhatsAppUpdate(recipientPhone: String, text: String) {
        addPushNotification("💬 [WhatsApp to $recipientPhone]: \"$text\"")
    }

    // FIX: Use Haversine formula instead of Euclidean degrees × 100.
    // Euclidean gives wildly inaccurate distances especially at city scale.
    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return R * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    // -------------------------------------------------------
    // SUPPORT & COMPLAINTS
    // -------------------------------------------------------
    private val _supportTickets = MutableStateFlow<List<SupportTicket>>(listOf(
        SupportTicket(
            id = 101, customerPhone = "03001234567", customerName = "Ahmad Malik",
            bookingId = 1L, category = "Pricing Dispute",
            description = "The technician asked for extra material charges of Rs. 1000 without a receipt.",
            status = "Escalated", timestamp = System.currentTimeMillis() - 7200000,
            chatMessages = listOf(
                SupportMessage("Customer", "I want to file a complaint about my electrical booking."),
                SupportMessage("Support Agent", "Ahmad, your ticket has been escalated. A senior auditor is reviewing.")
            ), slaTimerMinutes = 12
        )
    ))
    val supportTickets: StateFlow<List<SupportTicket>> = _supportTickets.asStateFlow()

    private val _coupons = MutableStateFlow<List<Coupon>>(listOf(
        Coupon("FIXNOW10", 150.0, "Flat Rs. 150 off on your first service!"),
        Coupon("PAKISTAN50", 250.0, "National Discount - Flat Rs. 250 off."),
        Coupon("REFER500", 500.0, "Referral discount: Zainab's Invite", isReferral = true, refereeName = "Zainab")
    ))
    val coupons: StateFlow<List<Coupon>> = _coupons.asStateFlow()

    val activeDiscountCode = MutableStateFlow("")
    val appliedDiscountAmount = MutableStateFlow(0.0)

    fun createSupportTicket(category: String, description: String, bookingId: Long?) {
        val customer = _activeCustomer.value ?: return
        val newId = (_supportTickets.value.map { it.id }.maxOrNull() ?: 100) + 1
        _supportTickets.update { it + SupportTicket(
            id = newId, customerPhone = customer.phone, customerName = customer.name,
            bookingId = bookingId, category = category, description = description,
            status = "Open", timestamp = System.currentTimeMillis(),
            chatMessages = listOf(SupportMessage("Customer", description))
        )}
        addPushNotification("🎫 Ticket #$newId filed: '$category'. SLA: 15 mins.")
    }

    fun submitSupportAgentReply(ticketId: Long, reply: String) {
        _supportTickets.update { tickets ->
            tickets.map { if (it.id == ticketId) it.copy(
                chatMessages = it.chatMessages + SupportMessage("Support Agent", reply),
                status = "In Progress"
            ) else it }
        }
        val ticket = _supportTickets.value.firstOrNull { it.id == ticketId }
        if (ticket != null) {
            addPushNotification("💬 Support replied to ${ticket.customerName}.")
            addWhatsAppUpdate(ticket.customerPhone, "FixNow Support replied to Ticket #${ticket.id}. View in app.")
        }
    }

    fun submitCustomerSupportMessage(ticketId: Long, message: String) {
        _supportTickets.update { tickets ->
            tickets.map { if (it.id == ticketId) it.copy(
                chatMessages = it.chatMessages + SupportMessage("Customer", message)
            ) else it }
        }
    }

    fun updateTicketStatus(ticketId: Long, newStatus: String) {
        _supportTickets.update { tickets ->
            tickets.map { if (it.id == ticketId) it.copy(status = newStatus) else it }
        }
        addPushNotification("🚨 Ticket #$ticketId → $newStatus.")
    }

    fun validateAndApplyCoupon(code: String): Boolean {
        val cleaned = code.trim().uppercase()
        val match = _coupons.value.firstOrNull { it.code.uppercase() == cleaned }
        return if (match != null) {
            activeDiscountCode.value = match.code
            appliedDiscountAmount.value = match.discountAmount
            addPushNotification("🎟️ Promo applied! Rs. ${match.discountAmount.toInt()} discount.")
            true
        } else {
            addPushNotification("❌ Code '$code' is invalid or expired.")
            false
        }
    }

    fun createCoupon(code: String, discount: Double, desc: String) {
        val cleanCode = code.trim().uppercase()
        if (_coupons.value.any { it.code == cleanCode }) return
        _coupons.update { it + Coupon(cleanCode, discount, desc) }
        addPushNotification("🎟️ New promo code created: $cleanCode")
    }

    fun removeAppliedDiscount() {
        activeDiscountCode.value = ""
        appliedDiscountAmount.value = 0.0
    }

    // -------------------------------------------------------
    // ENTERPRISE ADMIN WORKFLOWS
    // -------------------------------------------------------
    private val _fraudAlerts = MutableStateFlow<List<FraudAlert>>(listOf(
        FraudAlert(1, "Suspect GPS Relocation", "HIGH", "Rizwan's GPS updated from Gulberg to F-7 Islamabad in 4 seconds.", System.currentTimeMillis() - 1000 * 60 * 18, "03009988771"),
        FraudAlert(2, "Duplicate IP Sign-In", "LOW", "Tech & Customer with overlapping referral codes from identical fingerprints.", System.currentTimeMillis() - 1000 * 60 * 45, "03001122334"),
        FraudAlert(3, "Substandard Rate Gouging", "CRITICAL", "Bill totaled Rs. 14,000 on category estimated at max Rs. 3,500.", System.currentTimeMillis() - 1000 * 60 * 120, "03125559092")
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
        AnnouncementLog("MASS_A1", "Global System Update", "All technicians: run NADRA facial match upgrades.", "All Techs", System.currentTimeMillis() - 1000 * 60 * 1400),
        AnnouncementLog("MASS_A2", "Rain Emergency Alert", "Heavy rain in Karachi. Shift margins by Rs. 300.", "Karachi Only", System.currentTimeMillis() - 1000 * 60 * 900)
    ))
    val announcementsLogs: StateFlow<List<AnnouncementLog>> = _announcementsLogs.asStateFlow()

    fun sendGlobalPushAnnouncement(target: String, title: String, content: String) {
        _announcementsLogs.update { listOf(AnnouncementLog("MASS_${System.currentTimeMillis().toString().takeLast(6)}", title, content, target, System.currentTimeMillis())) + it }
        addPushNotification("📣 BROADCAST to [$target] → $title: $content")
    }

    fun resolveFraudAlert(alertId: Int) {
        _fraudAlerts.update { alerts -> alerts.map { if (it.id == alertId) it.copy(isResolved = true) else it } }
        addPushNotification("🛡️ Incident #$alertId archived.")
    }

    fun releasePayoutWithdrawal(txnId: String) {
        _paymentWithdrawals.update { txns -> txns.map { if (it.id == txnId) it.copy(status = "Released & Processed") else it } }
        val txn = _paymentWithdrawals.value.firstOrNull { it.id == txnId }
        if (txn != null) {
            addPushNotification("💸 Released Rs. ${txn.amount.toInt()} to ${txn.paymentDetails}")
            addWhatsAppUpdate(txn.paymentDetails.substringAfter("- ").trim(), "FixNow Payout: Rs. ${txn.amount.toInt()} credited to your account!")
        }
    }

    fun rejectPayoutWithdrawal(txnId: String) {
        _paymentWithdrawals.update { txns -> txns.map { if (it.id == txnId) it.copy(status = "Declined (Fraud / CNIC Audit)") else it } }
        addPushNotification("🛑 Payout DENIED: Txn #$txnId flagged under compliance review.")
    }

    // -------------------------------------------------------
    // MAPBOX ADDRESS & ROUTE
    // -------------------------------------------------------
    fun updateLiveLocation(lat: Double, lng: Double, address: String = "") {
        resolvedLocationLat.value = lat
        resolvedLocationLng.value = lng
        if (address.isNotEmpty()) draftAddress.value = address
    }

    fun searchAddressSuggestions(query: String) {
        if (query.trim().isEmpty()) { searchSuggestions.value = emptyList(); return }
        viewModelScope.launch {
            try {
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                if (token.isEmpty() || token == "your_token_here") {
                    searchSuggestions.value = listOf("${query.trim()}, Block H, Gulberg III, Lahore", "${query.trim()}, Phase 5, DHA, Lahore", "${query.trim()}, Model Town, Lahore")
                    return@launch
                }
                val response = MapboxClient.apiService.searchPlaces(query = query, accessToken = token)
                searchSuggestions.value = if (response.features.isNotEmpty()) response.features.map { it.placeName }
                else listOf("${query.trim()}, Gulberg III, Lahore", "${query.trim()}, DHA Phase 5, Lahore")
            } catch (e: Exception) {
                e.printStackTrace()
                searchSuggestions.value = listOf("${query.trim()}, Gulberg III, Lahore")
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
                    val coords = response.features[0].center
                    resolvedLocationLng.value = coords[0]
                    resolvedLocationLat.value = coords[1]
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun refreshActiveGoogleRoute(booking: Booking) {
        val techLat = booking.techLatitude; val techLng = booking.techLongitude
        val custLat = booking.latitude; val custLng = booking.longitude
        if (techLat == 0.0 || custLat == 0.0) return
        viewModelScope.launch {
            try {
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                if (token.isEmpty() || token == "your_token_here") {
                    activeRoutePoints.value = listOf(LatLng(techLat, techLng), LatLng(custLat, custLng)); return@launch
                }
                val response = MapboxClient.apiService.getDirections(
                    coords = "$techLng,$techLat;$custLng,$custLat", accessToken = token, geometries = "polyline"
                )
                if (response.code == "Ok" && response.routes.isNotEmpty()) {
                    val route = response.routes[0]
                    activeRouteDistance.value = String.format("%.2f KM", route.distance / 1000.0)
                    activeRouteDuration.value = "${(route.duration / 60.0).toInt().coerceAtLeast(1)} mins"
                    val geom = route.geometry
                    activeRoutePoints.value = if (geom != null) MapboxPolylineDecoder.decode(geom)
                    else listOf(LatLng(techLat, techLng), LatLng(custLat, custLng))
                } else {
                    activeRoutePoints.value = listOf(LatLng(techLat, techLng), LatLng(custLat, custLng))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                activeRoutePoints.value = listOf(LatLng(techLat, techLng), LatLng(custLat, custLng))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try { SupabaseRealtimeClient.clearCallbacks(); SupabaseRealtimeClient.disconnect() } catch (e: Exception) { e.printStackTrace() }
    }
}

// -------------------------------------------------------
// SUPPORTING DATA CLASSES (non-Room, non-Supabase)
// -------------------------------------------------------
data class FraudAlert(
    val id: Int, val title: String, val severity: String,
    val description: String, val timestamp: Long,
    val associatedPhone: String, val isResolved: Boolean = false
) : java.io.Serializable

data class PaymentWithdrawal(
    val id: String, val name: String, val type: String,
    val amount: Double, val paymentDetails: String,
    val status: String, val timestamp: Long
) : java.io.Serializable

data class AnnouncementLog(
    val id: String, val title: String, val content: String,
    val targetGroup: String, val timestamp: Long
) : java.io.Serializable

data class SupportTicket(
    val id: Long, val customerPhone: String, val customerName: String,
    val bookingId: Long?, val category: String, val description: String,
    val status: String, val timestamp: Long,
    val chatMessages: List<SupportMessage> = emptyList(),
    val slaTimerMinutes: Int = 15
) : java.io.Serializable

data class SupportMessage(
    val sender: String, val message: String,
    val timestamp: Long = System.currentTimeMillis()
) : java.io.Serializable

data class Coupon(
    val code: String, val discountAmount: Double, val description: String,
    val isReferral: Boolean = false, val refereeName: String? = null
) : java.io.Serializable
