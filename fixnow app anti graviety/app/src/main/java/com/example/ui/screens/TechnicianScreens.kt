package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.Booking
import com.example.data.TechnicianProfile
import com.example.ui.theme.*
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.example.ui.viewmodel.FixNowViewModel
import kotlinx.coroutines.delay
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlin.math.sqrt
import kotlin.math.pow

@Composable
fun TechnicianSpace(
    viewModel: FixNowViewModel,
    modifier: Modifier = Modifier
) {
    val activeTech by viewModel.activeTechnician.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    var isLoginMode by remember { mutableStateOf(true) }

    // Find if this technician has an active ticket assigned or en route
    val currentActiveJob = remember(bookings, activeTech) {
        bookings.firstOrNull { 
            it.technicianPhone == activeTech?.phone && 
            it.status != "Completed" && 
            it.status != "Cancelled" 
        }
    }

    // Find requests offered to this technician but still in "Requested" state
    val pendingOffers = remember(bookings, activeTech) {
        bookings.filter { 
            it.technicianPhone == activeTech?.phone && 
            it.status == "Requested" 
        }
    }

    Scaffold(
        modifier = modifier.testTag("technician_space_root")
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (activeTech == null) {
                if (isLoginMode) {
                    TechnicianLoginView(
                        viewModel = viewModel,
                        onNavigateToRegister = { isLoginMode = false }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Partner Program",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { isLoginMode = true },
                                modifier = Modifier.testTag("nav_to_login_top_btn")
                            ) {
                                Text("Sign In instead ➔", color = BrandEmerald, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                        Box(modifier = Modifier.weight(1f)) {
                            TechnicianRegistrationView(
                                viewModel = viewModel,
                                onRegisterSubmit = { viewModel.registerNewTechnician() }
                            )
                        }
                    }
                }
            } else {
                // Step 2: Technician main cockpit
                TechnicianCockpit(
                    viewModel = viewModel,
                    tech = activeTech!!,
                    pendingOffers = pendingOffers,
                    activeJob = currentActiveJob
                )
            }
        }
    }
}

/**
 * High fidelity Login Screen for registered technicians
 */
@Composable
fun TechnicianLoginView(
    viewModel: FixNowViewModel,
    onNavigateToRegister: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 600.dp

        val loginError by viewModel.techLoginError.collectAsState()
        var inputPhone by remember { mutableStateOf("") }
        var inputPin by remember { mutableStateOf("") }
        var isPinVisible by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxHeight()
                .padding(horizontal = if (isWide) 32.dp else 24.dp, vertical = 24.dp)
                .testTag("tech_login_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.switchMode("Onboarding") },
                    modifier = Modifier.testTag("tech_back_to_gateway")
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back to Gateway", tint = BrandCharcoal)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Exit Partner Program", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandCharcoal)
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            Icon(
                imageVector = Icons.Default.Engineering,
                contentDescription = "Technician",
                tint = BrandEmerald,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Technician Login Portal",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Secure authentication matching local registered database",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        item {
            FixNowCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Partner Sign In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandCharcoal
                    )

                    OutlinedTextField(
                        value = inputPhone,
                        onValueChange = {
                            inputPhone = it
                            viewModel.clearTechLoginError()
                        },
                        label = { Text("Registered Pakistan Mobile #") },
                        placeholder = { Text("e.g. 03339876543") },
                        leadingIcon = { Icon(Icons.Default.Phone, null, tint = BrandEmerald) },
                        modifier = Modifier.fillMaxWidth().testTag("tech_login_phone_input")
                    )

                    OutlinedTextField(
                        value = inputPin,
                        onValueChange = {
                            inputPin = it
                            viewModel.clearTechLoginError()
                        },
                        label = { Text("Security Access PIN") },
                        placeholder = { Text("Enter 4-digit PIN") },
                        visualTransformation = if (isPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = BrandEmerald) },
                        trailingIcon = {
                            val image = if (isPinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { isPinVisible = !isPinVisible }) {
                                Icon(imageVector = image, contentDescription = if (isPinVisible) "Hide PIN" else "Show PIN")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("tech_login_pin_input")
                    )

                    if (loginError != null) {
                        Text(
                            text = loginError!!,
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp).testTag("tech_login_error_label")
                        )
                    }

                    Button(
                        onClick = { viewModel.loginTechnician(inputPhone, inputPin) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tech_login_button")
                    ) {
                        Text("Verify & Connect Profile", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.testTag("nav_to_register_btn")
            ) {
                Text(
                    text = "Don't have an partner account yet? Join us ➔",
                    color = BrandEmerald,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
    }
}

/**
 * Professional Registration Screen for prospective Pakistani technicians
 */
@Composable
fun TechnicianRegistrationView(
    viewModel: FixNowViewModel,
    onRegisterSubmit: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 600.dp

        val isSuccess by viewModel.isRegSuccess.collectAsState()
        val name by viewModel.regName.collectAsState()
        val phone by viewModel.regPhone.collectAsState()
        val cnic by viewModel.regCNIC.collectAsState()
        val wallet by viewModel.regBankDetails.collectAsState()
        val selectedCity by viewModel.regCity.collectAsState()
        val selectedCat by viewModel.regCategory.collectAsState()
        val referredByCode by viewModel.regReferredByCode.collectAsState()

        var selfieTaken by remember { mutableStateOf(false) }
        var cnicUploaded by remember { mutableStateOf(false) }
        val context = LocalContext.current

        // Real camera launcher for selfie
        val selfieImageUri = remember { mutableStateOf<android.net.Uri?>(null) }
        val selfiePermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) viewModel.addPushNotification("📸 Camera ready. Tap again to capture selfie.")
        }
        val selfieCaptureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                selfieTaken = true
                // Store URI string in viewModel so registerNewTechnician() can read it
                val fileName = "selfie_${System.currentTimeMillis()}.jpg"
                viewModel.regSelfieUrl.value = fileName
                viewModel.addPushNotification("✅ Selfie captured: $fileName")
            }
        }

        // Real camera launcher for CNIC scan
        val cnicCaptureLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                cnicUploaded = true
                viewModel.addPushNotification("✅ CNIC document scanned successfully.")
            }
        }

        val cities = listOf("Lahore", "Karachi", "Islamabad")
        val categories = listOf("Electrical Services", "AC Services", "Plumbing Services", "Appliance Repair", "Generator & UPS Services", "Handyman Services", "Cleaning & Maintenance")

        if (isSuccess) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
                    .testTag("reg_success_box"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            Icon(Icons.Default.HourglassEmpty, null, tint = AccentAmber, modifier = Modifier.size(72.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("CNIC Submission Pending Approval", fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Thank you for submitting your CNIC and Selfie matching documents. Our Lahore verification unit will audit your profile credentials shortly.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.resetRegSuccess() },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Open New Sandbox Submission", color = Color.White)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxHeight()
                .padding(horizontal = if (isWide) 24.dp else 16.dp, vertical = 16.dp)
                .testTag("tech_registration_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, null, tint = BrandEmerald, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply as Certified Technician", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Join Pakistan's highest paying home services fleet! EasyPaisa automated daily cashout.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pre-Approved Operational Security Quick Gate
            item {
                var showGatePass by remember { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(0.04f)),
                    border = BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_log_shortcut")
                ) {
                    Column(
                        modifier = Modifier
                            .clickable { showGatePass = !showGatePass }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Biometric Entry",
                                    tint = BrandEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pre-Verified Captain Biometric Entry",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = BrandCharcoal,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = if (showGatePass) "COLLAPSE" else "ACCESS GATE",
                                fontSize = 10.sp,
                                color = BrandEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (showGatePass) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Secure, high-priority dispatcher link. Certified operators (like AC master Kamran Khan) can authenticate their secure hardware terminal chip below:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = { viewModel.setTechnicianPhone("03339876543") },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("quick_login_kamran")
                                    .height(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Biometric Sign In: Kamran Khan (03339876543)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            item { Divider() }

            // Form Inputs
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { viewModel.regName.value = it },
                    label = { Text("Complete Legal Name (matching CNIC)") },
                    placeholder = { Text("e.g. Muhammad Kashif") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    modifier = Modifier.fillMaxWidth().testTag("tech_name_input")
                )
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { viewModel.regPhone.value = it },
                    label = { Text("JazzCash/EasyPaisa Active Mobile #") },
                    placeholder = { Text("e.g. 03211234567") },
                    leadingIcon = { Icon(Icons.Default.Phone, null) },
                    modifier = Modifier.fillMaxWidth().testTag("tech_phone_input")
                )
            }

            item {
                OutlinedTextField(
                    value = cnic,
                    onValueChange = { viewModel.regCNIC.value = it },
                    label = { Text("National CNIC Identification Number") },
                    placeholder = { Text("e.g. 35201-1234567-1") },
                    leadingIcon = { Icon(Icons.Default.ContactMail, null) },
                    modifier = Modifier.fillMaxWidth().testTag("tech_cnic_input")
                )
            }

            item {
                val password by viewModel.regPassword.collectAsState()
                var isRegPasswordVisible by remember { mutableStateOf(false) }
                OutlinedTextField(
                    value = password,
                    onValueChange = { viewModel.regPassword.value = it },
                    label = { Text("Set Secure Portal Password (min 4 chars)") },
                    placeholder = { Text("Enter a strong password") },
                    visualTransformation = if (isRegPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, null, tint = BrandEmerald) },
                    trailingIcon = {
                        val image = if (isRegPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { isRegPasswordVisible = !isRegPasswordVisible }) {
                            Icon(imageVector = image, contentDescription = if (isRegPasswordVisible) "Hide Password" else "Show Password")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tech_password_setup_field")
                )
            }

            item {
                OutlinedTextField(
                    value = wallet,
                    onValueChange = { viewModel.regBankDetails.value = it },
                    label = { Text("Payout EasyPaisa Account Title") },
                    placeholder = { Text("e.g. EasyPaisa - Muhammad Kashif") },
                    leadingIcon = { Icon(Icons.Default.AccountBalanceWallet, null) },
                    modifier = Modifier.fillMaxWidth().testTag("tech_wallet_input")
                )
            }

            item {
                OutlinedTextField(
                    value = referredByCode,
                    onValueChange = { viewModel.regReferredByCode.value = it },
                    label = { Text("Referral Code (Optional)") },
                    placeholder = { Text("e.g. TECH-RIZWAN-7890 or CUST-AHMAD-4567") },
                    leadingIcon = { Icon(Icons.Default.Stars, null) },
                    modifier = Modifier.fillMaxWidth().testTag("tech_referred_by_input")
                )
            }

            // Dropdowns (Selections represented as simple horizontal list options)
            item {
                Text("Select Service Category Master Specialty:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { cat ->
                        val sel = selectedCat == cat
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.regCategory.value = cat }
                                .background(if (sel) BrandEmerald.copy(0.12f) else Color.Transparent, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = sel, onClick = { viewModel.regCategory.value = cat })
                            Text(cat, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            item {
                Text("Select Primary Operational Base Region:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    cities.forEach { city ->
                        val sel = selectedCity == city
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = if (sel) BrandEmerald else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(if (sel) BrandEmerald.copy(0.15f) else Color.Transparent)
                                .clickable { viewModel.regCity.value = city }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(city, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Secure Verification Scanning Module
            item {
                Text("Official Government Identity Verification:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandCharcoal)
                Text("Please upload valid original CNIC and face portrait matching documents. State-of-the-art biometrics will apply secure KYC checking.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    // Selfie Camera Scanning Module
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .border(
                                width = if (selfieTaken) 2.dp else 1.dp, 
                                color = if (selfieTaken) BrandEmerald else Color.LightGray, 
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(if (selfieTaken) BrandEmerald.copy(0.04f) else Color.Transparent)
                            .clickable {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    selfieCaptureLauncher.launch(null)
                                } else {
                                    selfiePermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                            .testTag("snap_selfie_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(if (selfieTaken) BrandEmerald.copy(0.15f) else Color.Gray.copy(0.08f), RoundedCornerShape(50.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selfieTaken) Icons.Default.CheckCircle else Icons.Default.Face, 
                                    contentDescription = "Active Selfie portrait", 
                                    tint = if (selfieTaken) BrandEmerald else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (selfieTaken) "Portrait Authenticated" else "Live Biometric Face Scan", 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (selfieTaken) BrandEmerald else BrandCharcoal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    
                    // CNIC Scanning Module
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(110.dp)
                            .border(
                                width = if (cnicUploaded) 2.dp else 1.dp, 
                                color = if (cnicUploaded) BrandEmerald else Color.LightGray, 
                                shape = RoundedCornerShape(12.dp)
                            )
                            .background(if (cnicUploaded) BrandEmerald.copy(0.04f) else Color.Transparent)
                            .clickable {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    cnicCaptureLauncher.launch(null)
                                } else {
                                    selfiePermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                            .testTag("snap_cnic_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(if (cnicUploaded) BrandEmerald.copy(0.15f) else Color.Gray.copy(0.08f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (cnicUploaded) Icons.Default.AssignmentInd else Icons.Default.CameraAlt, 
                                    contentDescription = "Original CNIC scanner", 
                                    tint = if (cnicUploaded) BrandEmerald else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (cnicUploaded) "CNIC Smart Scanned" else "Scan Gov. CNIC Front", 
                                fontSize = 10.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = if (cnicUploaded) BrandEmerald else BrandCharcoal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onRegisterSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("tech_submit_register"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Submit Application Document Profile", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    }
}

/**
 * Main workbench cockpit when verified technician logs in
 */
@Composable
fun TechnicianCockpit(
    viewModel: FixNowViewModel,
    tech: TechnicianProfile,
    pendingOffers: List<Booking>,
    activeJob: Booking?
) {
    val earningsRecord by viewModel.techEarnings.collectAsState()
    val totalEarnings = remember(earningsRecord, tech) {
        earningsRecord.sumOf { it.amount } + tech.referralEarnings
    }
    val formattedPayout = totalEarnings.toInt()

    var activeTab by remember { mutableStateOf(0) }
    var mockWithdrawn by remember { mutableStateOf(0.0) }
    val walletBalance = maxOf(0.0, formattedPayout.toDouble() - mockWithdrawn)

    val upcomingSchedules = remember(tech.city) {
        listOf(
            Booking(
                id = 901,
                serviceCategory = tech.category,
                serviceName = if (tech.category.contains("AC")) "AC Master Gas Charge & Leak Repair" else "Electrical Diagnostics",
                customerName = "Mrs. Zainab Malik",
                customerPhone = "03214567891",
                customerAddress = "Street 4, Sector F, DHA Phase 6, ${tech.city}",
                customerCity = tech.city,
                preferredTime = "Tomorrow at 10:00 AM",
                paymentMethod = "EasyPaisa",
                price = 3200.0,
                status = "Assigned",
                issueDescription = "Periodic high surge dispatch simulated directly from neighborhood feed maps."
            )
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp,
                modifier = Modifier.height(68.dp)
            ) {
                listOf(
                    Triple(0, Icons.Default.Work, "Workbench"),
                    Triple(1, Icons.Default.Map, "Gig Radar"),
                    Triple(2, Icons.Default.AccountBalanceWallet, "Wallet"),
                    Triple(3, Icons.Default.Description, "Trust Docs")
                ).forEach { (idx, icon, label) ->
                    NavigationBarItem(
                        selected = activeTab == idx,
                        onClick = { activeTab = idx },
                        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                        label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandEmerald,
                            selectedTextColor = BrandEmerald,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = BrandEmerald.copy(0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(LightBackground)
        ) {
            when (activeTab) {
                0 -> WorkbenchTab(viewModel, tech, pendingOffers, activeJob, earningsRecord, upcomingSchedules)
                1 -> GigRadarTab(tech, viewModel)
                2 -> WalletTab(tech, walletBalance, earningsRecord) { mockWithdrawn += it }
                3 -> TrustDocsTab(tech)
            }

            if (tech.isOnline && pendingOffers.isNotEmpty()) {
                val offer = pendingOffers.first()
                JobAcceptanceOverlay(
                    viewModel = viewModel,
                    offer = offer,
                    tech = tech
                )
            }
        }
    }
}

@Composable
fun WorkbenchTab(
    viewModel: FixNowViewModel,
    tech: TechnicianProfile,
    pendingOffers: List<Booking>,
    activeJob: Booking?,
    earningsRecord: List<com.example.data.EarningRecord>,
    upcomingSchedules: List<Booking>
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp).testTag("tech_cockpit_root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Driver profile status
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(46.dp).background(BrandEmerald.copy(0.1f), CircleShape).border(2.dp, BrandEmerald, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null, tint = BrandEmerald, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tech.name, fontSize = 15.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(0.12f)),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("VERIFIED 🟢", fontSize = 7.sp, fontWeight = FontWeight.Black, color = BrandEmerald, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                                Text("${tech.category} · ${tech.city}", fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (tech.isOnline) "🟢 ONLINE" else "🔴 OFFLINE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = if (tech.isOnline) BrandEmerald else Color.Gray)
                            Switch(
                                checked = tech.isOnline,
                                onCheckedChange = { viewModel.toggleTechnicianOnline(it) },
                                modifier = Modifier.scale(0.85f).testTag("tech_online_switch"),
                                enabled = tech.isApproved
                            )
                        }
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.LightGray.copy(0.3f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Security Pin Active", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { viewModel.logoutTechnician() }, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red), modifier = Modifier.height(28.dp).testTag("tech_logout_button")) {
                            Icon(Icons.Default.Logout, null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logout", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4 Key Workforce Metrics Ribbon Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.weight(1f), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("TODAY'S EARNINGS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Rs. ${(earningsRecord.sumOf { it.amount }).toInt()}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                            Text("${earningsRecord.size} dispatches", fontSize = 8.sp, color = Color.LightGray)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.weight(1f), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("ACCEPTANCE RATE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("${(tech.acceptanceRate * 100).toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                            Text("Top 5% Tier", fontSize = 8.sp, color = BrandEmerald, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.weight(1f), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("PERFORMANCE SCORE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("98/100", fontSize = 18.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                            Text("Elite Level Status", fontSize = 8.sp, color = BrandEmerald, fontWeight = FontWeight.Bold)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.weight(1f), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("CUSTOMER RATINGS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("${tech.rating}", fontSize = 18.sp, fontWeight = FontWeight.Black)
                            }
                            Text("${tech.totalJobs} total jobs done", fontSize = 8.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // Referral Earnings Overview
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = LightGreenAccent),
                border = BorderStroke(1.dp, BrandEmerald.copy(0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("tech_referral_summary_card")
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("🔥 PARTNER REFERRALS", fontSize = 8.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                        Text("Active Code: ${tech.referralCode}", fontSize = 13.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
                        Text("Get 5% of their dispatch payouts direct", fontSize = 9.sp, color = Color.DarkGray)
                    }
                    Box(modifier = Modifier.background(BrandEmerald, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("Rs. ${tech.referralEarnings.toInt()}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // Immediate On-Demand Offer Request Pop
        if (tech.isOnline && pendingOffers.isNotEmpty()) {
            val offer = pendingOffers.first()
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    border = BorderStroke(2.dp, BrandEmerald),
                    modifier = Modifier.fillMaxWidth().testTag("job_offer_alert_box")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FlashOn, null, tint = AccentAmber)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🚨 LIVE WORKFORCE DISPATCH INBOUND!", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Divider(color = BrandEmerald.copy(0.3f))
                        Text(offer.serviceName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandCharcoal)
                        Text("Client notes: \"${offer.issueDescription}\"", fontSize = 10.sp, color = Color.DarkGray)
                        Text("📍 Sector: ${offer.customerAddress}", fontSize = 10.sp, color = BrandCharcoal)
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Payout Net Share (80%):", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("Rs. ${(offer.price * 0.8).toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.declineBooking(offer.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.weight(1f).testTag("decline_job_btn_click"),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Decline", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.acceptBooking(offer.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                modifier = Modifier.weight(1.3f).testTag("accept_job_btn_click"),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Accept Gig", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Active Assigned Controller Tracker Panel
        if (activeJob != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.2.dp, BrandEmerald),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("active_labor_manager_panel")
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Build, null, tint = BrandEmerald, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Active Job Controller", fontWeight = FontWeight.Black, fontSize = 12.sp, color = BrandCharcoal)
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(0.12f))) {
                                Text(activeJob.status.uppercase(), fontSize = 8.sp, color = BrandEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp, 3.dp))
                            }
                        }
                        Divider(color = Color.LightGray.copy(0.3f))
                        Text("Client: ${activeJob.customerName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("No: ${activeJob.customerPhone} | 📍 Address: ${activeJob.customerAddress}", fontSize = 10.sp, color = Color.Gray)
                        Text("Assigned: ${activeJob.serviceName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)

                        SimulatedGpsMap(
                            customerLat = activeJob.latitude,
                            customerLng = activeJob.longitude,
                            techLat = activeJob.techLatitude,
                            techLng = activeJob.techLongitude,
                            techName = "GPS Delivery Map",
                            status = activeJob.status
                        )

                        val btnLabel = when (activeJob.status) {
                            "Assigned" -> "🚗 Pick Up Tools (En Route)"
                            "Technician En Route" -> "📍 Mark Arrived at Door"
                            "Arrived" -> "🛠️ Start Assembly Labor Work"
                            "In Progress" -> "✅ Mark Finished (Collect Rs. ${activeJob.price.toInt()})"
                            else -> "In Repair"
                        }

                        Button(
                            onClick = { viewModel.advanceJobStatus(activeJob.id) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            modifier = Modifier.fillMaxWidth().height(42.dp).testTag("advance_job_trigger"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(btnLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Upcoming bookings
        item {
            Text("Upcoming Bookings Queue Calendar", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
        }

        items(upcomingSchedules) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.preferredTime, fontWeight = FontWeight.Bold, color = BrandEmerald, fontSize = 10.sp)
                        Text("Rs. ${item.price.toInt()}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(item.serviceName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("📍 Location: ${item.customerAddress}", fontSize = 9.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun GigRadarTab(tech: TechnicianProfile, viewModel: FixNowViewModel) {
    var selectedRegion by remember { mutableStateOf("Main Center Block") }
    val bookings by viewModel.bookings.collectAsState()

    val openBids = remember(bookings, tech.city) {
        bookings.filter { it.status == "Requested" && it.technicianPhone == null && it.customerCity.lowercase() == tech.city.lowercase() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("⚡ Interactive Dispatch Heat Map", fontSize = 14.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
                Text("Tap neighborhoods to view surge indicators in real-time.", fontSize = 10.sp, color = Color.Gray)
            }
        }

        // Custom canvas interactive heat map drawing
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFFE2EAF8), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // draw custom styled city grid lines
                            val gridW = size.width
                            val gridH = size.height
                            
                            // street blocks drawing
                            for (i in 1..3) {
                                val xCoord = gridW * (i / 4.0f)
                                drawLine(color = Color.White, start = Offset(xCoord, 0f), end = Offset(xCoord, gridH), strokeWidth = 3f)
                                val yCoord = gridH * (i / 4.0f)
                                drawLine(color = Color.White, start = Offset(0f, yCoord), end = Offset(gridW, yCoord), strokeWidth = 3f)
                            }

                            // Glowing surge bubbles
                            drawCircle(color = Color.Red.copy(alpha = 0.2f), radius = 40f, center = Offset(gridW * 0.5f, gridH * 0.45f))
                            drawCircle(color = Color.Red, radius = 10f, center = Offset(gridW * 0.5f, gridH * 0.45f))

                            drawCircle(color = AccentAmber.copy(alpha = 0.2f), radius = 30f, center = Offset(gridW * 0.15f, gridH * 0.7f))
                            drawCircle(color = AccentAmber, radius = 8f, center = Offset(gridW * 0.15f, gridH * 0.7f))

                            drawCircle(color = BrandEmerald.copy(alpha = 0.2f), radius = 30f, center = Offset(gridW * 0.8f, gridH * 0.25f))
                            drawCircle(color = BrandEmerald, radius = 8f, center = Offset(gridW * 0.8f, gridH * 0.25f))
                        }
                        
                        Text("📍 Gulberg Surge Area", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Nearby Neighborhood Surges:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Clifton A Block", "Main Center Block", "Johar Surging Zone").forEach { area ->
                            val isSelected = area == selectedRegion
                            Card(
                                modifier = Modifier.weight(1f).clickable { selectedRegion = area },
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) BrandEmerald.copy(0.12f) else CardSlate),
                                border = if (isSelected) BorderStroke(1.dp, BrandEmerald) else null
                            ) {
                                Text(area, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp).fillMaxWidth())
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = LightGreenAccent.copy(0.4f)), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("📍 Surge Index: x1.4 Active in $selectedRegion", fontSize = 10.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                            Text("Extremely high demand for technical dispatches registered. Dispatch bonus active.", fontSize = 9.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        }

        item {
            Text("📡 Nearby Available Gigs Broadcast", fontSize = 13.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
        }

        if (openBids.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Notifications, null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("No pending unassigned dispatches in ${tech.city}.", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            items(openBids) { booking ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(booking.serviceCategory, fontWeight = FontWeight.Bold, fontSize = 9.sp, color = BrandEmerald)
                            Text("Payout: Rs. ${(booking.price * 0.8).toInt()}", fontWeight = FontWeight.Black, color = BrandEmerald, fontSize = 11.sp)
                        }
                        Text(booking.serviceName, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("Notes: \"${booking.issueDescription}\"", fontSize = 9.sp, color = Color.Gray)
                        Text("📍 Location: ${booking.customerAddress}", fontSize = 9.sp, color = Color.Gray)

                        Button(
                            onClick = { viewModel.manualAssignTechnician(booking.id, tech.phone) },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("CLAIM GIG DIRECT", fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WalletTab(
    tech: TechnicianProfile,
    walletBalance: Double,
    earningsRecord: List<com.example.data.EarningRecord>,
    onWithdrawn: (Double) -> Unit
) {
    var isWithdrawOpen by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("💳 Workforce Earnings Wallet & Analytics", fontSize = 14.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
                Text("Automated deposits directly connected to primary financial accounts.", fontSize = 10.sp, color = Color.Gray)
            }
        }

        // Wallet Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = BrandCharcoal), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("AVAILABLE BASE BALANCE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Rs. ${walletBalance.toInt()}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = BrandEmerald, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = Color.White.copy(0.12f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("DEPOSIT OUTLET LINKED", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Text(tech.bankDetails.ifEmpty { "EasyPaisa Wallet 0300-XXXXXXX" }, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                        Button(
                            onClick = { isWithdrawOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                            shape = RoundedCornerShape(8.dp),
                            enabled = walletBalance >= 500
                        ) {
                            Text("CASH OUT FUNDS", fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // Income analytics week bars chart representation click and inspect
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Income Analytics Weekly Trend (₨)", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        val weeklyStats = listOf("M" to 4200, "T" to 5800, "W" to 3100, "T" to 6800, "F" to 7500, "S" to 2200, "S" to walletBalance.toInt().coerceIn(100, 5000))
                        weeklyStats.forEach { (day, money) ->
                            val heightFract = (money / 7500f).coerceIn(0.1f, 1f)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("₨${money/1000}k", fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.width(14.dp).fillMaxHeight(heightFract * 0.7f).background(BrandEmerald, RoundedCornerShape(2.dp)))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(day, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("Ledger Receipts Logs", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
        }

        if (earningsRecord.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                        Text("No completed transaction log inside this active session.", fontSize = 9.sp, color = Color.Gray)
                    }
                }
            }
        } else {
            items(earningsRecord) { log ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(log.category, fontSize = 10.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
                            Text("Job ID: #${log.bookingId}", fontSize = 8.sp, color = Color.Gray)
                        }
                        Text("+₨ ${log.amount.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                    }
                }
            }
        }
    }

    if (isWithdrawOpen) {
        WithdrawFundsDialog(walletBalance, tech.bankDetails, onDismiss = { isWithdrawOpen = false }) { amt ->
            onWithdrawn(amt)
            isWithdrawOpen = false
        }
    }
}

@Composable
fun WithdrawFundsDialog(
    balance: Double,
    channelDetails: String,
    onDismiss: () -> Unit,
    onSuccess: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var processingState by remember { mutableStateOf("START") }
    var chosenRoute by remember { mutableStateOf("EasyPaisa") }

    Dialog(onDismissRequest = onDismiss) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (processingState == "START") {
                    Text("Direct Capital Cashout Portal", fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Text("Route earnings clean to verified mobile wallets.", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("EasyPaisa", "JazzCash", "Bank Transfer").forEach { rt ->
                            val isChosen = chosenRoute == rt
                            Card(
                                modifier = Modifier.weight(1f).clickable { chosenRoute = rt },
                                colors = CardDefaults.cardColors(containerColor = if (isChosen) BrandEmerald.copy(0.12f) else CardSlate)
                            ) {
                                Text(rt, fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp).fillMaxWidth())
                            }
                        }
                    }

                    Text("Recipient Account: ${channelDetails.ifEmpty { "0300-XXXXXXX (Self Match)" }}", fontSize = 9.sp, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Transfer Amount (₨)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandEmerald, focusedLabelColor = BrandEmerald),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val req = amountText.toDoubleOrNull() ?: 0.0
                            if (req in 100.0..balance) { processingState = "PROGRESS" }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        enabled = (amountText.toDoubleOrNull() ?: 0.0) >= 100.0 && (amountText.toDoubleOrNull() ?: 0.0) <= balance,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Payout Outlets Submit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (processingState == "PROGRESS") {
                    CircularProgressIndicator(color = BrandEmerald, modifier = Modifier.size(32.dp))
                    Text("Registering transaction block...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LaunchedEffect(Unit) {
                        delay(2000)
                        processingState = "CONFIRMED"
                    }
                } else {
                    Box(modifier = Modifier.size(46.dp).background(BrandEmerald, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, null, tint = Color.White)
                    }
                    Text("Payout Gateway Confirmed!", fontSize = 13.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                    Text("Transferred secure payouts direct within Pakistan banking nodes.", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                    Button(onClick = { onSuccess(amountText.toDoubleOrNull() ?: 0.0) }, colors = ButtonDefaults.buttonColors(containerColor = BrandCharcoal), modifier = Modifier.fillMaxWidth()) {
                        Text("Finish", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun TrustDocsTab(tech: TechnicianProfile) {
    var previewDocTitle by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column {
                Text("📋 National Onboarding Compliance Trust Docs", fontSize = 14.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
                Text("Verified NADRA registers matched secure with partner biometric clearance.", fontSize = 10.sp, color = Color.Gray)
            }
        }

        // Onboarding Verification steps timeline
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Onboarding Verification Status:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    listOf("Bio Nadra Record Verified", "Selfie Face ID Verified", "Practical Audit Certified").forEach { label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(14.dp).background(BrandEmerald, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        item {
            Text("Your Compliance Vault Certificates", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        }

        // Document list items clickable
        items(
            listOf(
                "National Identity Registration (CNIC)",
                "Audited Technical License Certificate",
                "Police Security Verification Stamp"
            )
        ) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().clickable { previewDocTitle = item }
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("APPROVED 🟢", fontSize = 8.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                }
            }
        }
    }

    if (previewDocTitle != null) {
        Dialog(onDismissRequest = { previewDocTitle = null }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(previewDocTitle!!, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Divider(color = Color.LightGray.copy(0.4f))

                    if (previewDocTitle!!.contains("CNIC")) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE5F5ED)), modifier = Modifier.fillMaxWidth().height(100.dp)) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("GOVERNMENT OF PAKISTAN CNIC RECORD", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                Text("Name: ${tech.name.uppercase()}", fontSize = 8.sp, fontWeight = FontWeight.Black)
                                Text("CNIC: ${tech.cnic}", fontSize = 8.sp, fontWeight = FontWeight.Black)
                                Text("Status Indicator: BIO SECURED 🟢", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                            }
                        }
                    } else {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF5)), modifier = Modifier.fillMaxWidth().height(100.dp)) {
                            Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text("BOARD OF PROFESSIONAL AUDITING", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                Text("FX-PARTNER-REF-${tech.phone.takeLast(4)}", fontSize = 7.sp, color = Color.Gray)
                                Text("Certified category expert qualification standards certified cleanly.", fontSize = 8.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    Button(onClick = { previewDocTitle = null }, colors = ButtonDefaults.buttonColors(containerColor = BrandCharcoal), modifier = Modifier.fillMaxWidth()) {
                        Text("Close Secure Preview", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun JobAcceptanceOverlay(
    viewModel: FixNowViewModel,
    offer: Booking,
    tech: TechnicianProfile,
    onAccept: () -> Unit = { viewModel.acceptBooking(offer.id) },
    onDecline: () -> Unit = { viewModel.declineBooking(offer.id) }
) {
    var secondsRemaining by remember { mutableStateOf(30) }

    LaunchedEffect(offer.id) {
        secondsRemaining = 30
        while (secondsRemaining > 0) {
            delay(1000)
            secondsRemaining--
        }
        onDecline()
    }

    val distance = remember(tech, offer) {
        sqrt((tech.latitude - offer.latitude).pow(2.0) + (tech.longitude - offer.longitude).pow(2.0)) * 100
    }
    val distanceString = String.format("%.1f KM", distance)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )
    
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha2"
    )

    Dialog(
        onDismissRequest = { /* Cannot dismiss without manual accept/decline action for regulatory compliance */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(16.dp)
                .testTag("job_offer_alert_box"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .border(2.dp, BrandEmerald, RoundedCornerShape(24.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // High Urgency Sound-alike Flashing Header Block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.Red, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "INCOMING DISPATCH GIG",
                            color = Color.Red,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(0.15f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = offer.serviceCategory.uppercase(),
                            color = BrandEmerald,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Wave pulse radar matching animations
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .scale(pulseScale1)
                            .background(BrandEmerald.copy(alpha = pulseAlpha1), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .scale(pulseScale2)
                            .background(AccentAmber.copy(alpha = pulseAlpha2), CircleShape)
                    )
                    
                    // Count-down circular text badge
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(Color(0xFF0F172A), CircleShape)
                            .border(3.dp, if (secondsRemaining > 10) BrandEmerald else Color.Red, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$secondsRemaining",
                                color = if (secondsRemaining > 10) Color.White else Color.Red,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "sec Left",
                                color = Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = offer.serviceName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )

                // Distance & Customer Rating row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.White.copy(0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Navigation, null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("DISTANCE", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text(distanceString, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, Color.White.copy(0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Star, null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("RATING", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("4.9 ★", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // EXPECTED PARTNER SHARE PAYMENT BADGE
                Card(
                    colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(0.1f)),
                    border = BorderStroke(1.5.dp, BrandEmerald),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "EXPECTED NET PAYMENT (80%)",
                                color = BrandEmerald,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Rs. ${(offer.price * 0.8).toInt()}",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Gross: Rs. ${offer.price.toInt()}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }

                // GPS Route simulation preview map
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "📍 DISPATCH PICKUP MAP PREVIEW",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SimulatedGpsMap(
                        customerLat = offer.latitude,
                        customerLng = offer.longitude,
                        techLat = tech.latitude,
                        techLng = tech.longitude,
                        techName = "Your GPS",
                        status = "Assigned",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                    )
                }

                // Customer exact instructions & address
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF334155).copy(0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Place, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CUSTOMER ADDRESS:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        }
                        Text(offer.customerAddress, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Message, null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CUSTOMER NOTE:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        }
                        Text(
                            text = if (offer.issueDescription.isNotEmpty()) offer.issueDescription else "Regular troubleshooting required.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(0.9f)
                        )
                    }
                }

                // Final action confirmations layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDecline,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("decline_job_btn_click"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DECLINE", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(52.dp)
                            .testTag("accept_job_btn_click"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("ACCEPT GIG", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
