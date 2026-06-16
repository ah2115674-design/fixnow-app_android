package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Booking
import com.example.data.TechnicianProfile
import com.example.data.CustomerProfile
import com.example.ui.theme.*
import com.example.ui.viewmodel.FixNowViewModel
import com.example.BuildConfig
import com.example.data.LatLng
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas

@Composable
fun AdminSpace(
    viewModel: FixNowViewModel,
    modifier: Modifier = Modifier
) {
    val isAuthorized by viewModel.isAdminAuthorized.collectAsState()

    if (!isAuthorized) {
        AdminLoginView(viewModel = viewModel)
    } else {
        val adminTab by viewModel.adminTab.collectAsState()
        val technicians by viewModel.technicians.collectAsState()
        val bookings by viewModel.bookings.collectAsState()
        val tickets by viewModel.supportTickets.collectAsState()
        val couponsList by viewModel.coupons.collectAsState()
        val fraudAlerts by viewModel.fraudAlerts.collectAsState()
        val paymentWithdrawals by viewModel.paymentWithdrawals.collectAsState()
        val announcementsLogs by viewModel.announcementsLogs.collectAsState()

        var newCouponCode by remember { mutableStateOf("") }
        var newPromoDiscount by remember { mutableStateOf("") }
        var newPromoDesc by remember { mutableStateOf("") }
        var techSubTab by remember { mutableStateOf("Active Fleet") }

        var broadcastTitle by remember { mutableStateOf("") }
        var broadcastContent by remember { mutableStateOf("") }
        var targetGroup by remember { mutableStateOf("All Techs") }

        val pendingApprovals = remember(technicians) {
            technicians.filter { !it.isApproved }
        }

        val totalGmv = remember(bookings) {
            64800 + bookings.filter { it.status == "Completed" }.sumOf { it.price }.toInt()
        }

        val stats = remember(bookings) {
            val total = bookings.size
            if (total == 0) {
                listOf(
                    Triple("Electrical Installations", "45%", 0.45f),
                    Triple("AC Repair/Gas Refills", "32%", 0.32f),
                    Triple("Household Plumbing", "15%", 0.15f),
                    Triple("Oven & Washer Fixes", "8%", 0.08f)
                )
            } else {
                val categories = listOf(
                    "Electrical Services" to "Electrical Installations",
                    "AC Services" to "AC Repair/Gas Refills",
                    "Plumbing Services" to "Household Plumbing",
                    "Generator & UPS Services" to "Generator & UPS Services",
                    "Cleaning & Maintenance" to "Cleaning & Maintenance"
                )
                val counts = categories.map { (key, label) ->
                    val count = bookings.count { it.serviceCategory == key }
                    Triple(label, count, key)
                }
                val bookingWithCat = counts.sumOf { it.second }
                if (bookingWithCat == 0) {
                    listOf(
                        Triple("Electrical Installations", "45%", 0.45f),
                        Triple("AC Repair/Gas Refills", "32%", 0.32f),
                        Triple("Household Plumbing", "15%", 0.15f),
                        Triple("Oven & Washer Fixes", "10%", 0.10f)
                    )
                } else {
                    counts.map { (label, count, _) ->
                        val ratio = count.toFloat() / bookingWithCat
                        val pct = "${(ratio * 100).toInt()}%"
                        Triple(label, pct, ratio)
                    }
                }
            }
        }

        val cities = remember(bookings) {
            val total = bookings.size
            if (total == 0) {
                listOf(
                    Pair("Lahore Command Unit", "72% jobs completed"),
                    Pair("Karachi Command Unit", "21% jobs completed"),
                    Pair("Islamabad Command Unit", "7% jobs completed")
                )
            } else {
                val lahoreCount = bookings.count { it.customerCity.contains("Lahore", ignoreCase = true) }
                val karachiCount = bookings.count { it.customerCity.contains("Karachi", ignoreCase = true) }
                val islamabadCount = bookings.count { it.customerCity.contains("Islamabad", ignoreCase = true) }
                val sum = lahoreCount + karachiCount + islamabadCount
                if (sum == 0) {
                    listOf(
                        Pair("Lahore Command Unit", "0 jobs completed"),
                        Pair("Karachi Command Unit", "0 jobs completed"),
                        Pair("Islamabad Command Unit", "0 jobs completed")
                    )
                } else {
                    listOf(
                        Pair("Lahore Command Unit", "${(lahoreCount.toFloat() / sum * 100).toInt()}% jobs completed"),
                        Pair("Karachi Command Unit", "${(karachiCount.toFloat() / sum * 100).toInt()}% jobs completed"),
                        Pair("Islamabad Command Unit", "${(islamabadCount.toFloat() / sum * 100).toInt()}% jobs completed")
                    )
                }
            }
        }

        AdaptiveWidthCenteredBox(
            maxWidth = 1000.dp
        ) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
                .testTag("admin_dashboard_root"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // High level numbers overview card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Central Pakistan Command Center", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(text = "Real-time dispatch system monitoring Lahore, Karachi, Islamabad", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(
                                onClick = { viewModel.logoutAdmin() },
                                modifier = Modifier.testTag("admin_logout_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "Close Admin Console",
                                    tint = Color.Red
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ACTIVE JOBS TODAY", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${bookings.filter { it.status != "Completed" && it.status != "Cancelled" }.size} Jobs", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CNIC PENDING REG", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("${pendingApprovals.size} Profiles", fontSize = 16.sp, fontWeight = FontWeight.Black, color = AccentAmber)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("TOTAL SYSTEM GMV", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Text("Rs. $totalGmv", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                            }
                        }
                    }
                }
            }

        // Subtabs: Technicians Approved, Fleet Map, Bookings Override, Analytics Logs
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.15f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(4.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        "Analytics",
                        "Technicians",
                        "Live Map",
                        "Bookings",
                        "Dispute Desk",
                        "Fraud Check",
                        "Notifications",
                        "Payments",
                        "Coupons"
                    )
                    tabs.forEach { t ->
                        val sel = adminTab == t
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .background(
                                    color = if (sel) BrandEmerald.copy(0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                               )
                                .clickable { viewModel.setAdminTab(t) }
                                .padding(horizontal = 12.dp)
                                .testTag("admin_subtab_${t.replace(" ", "_").lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = t,
                                color = if (sel) BrandEmerald else MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Tab Content Routing
        when (adminTab) {
            "Analytics" -> {
                // 1) REVENUE ANALYTICS SUMMARY
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📊 PLATFORM FINANCIAL MONITOR",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = BrandEmerald
                                )
                                Text(
                                    text = "COMMISSION CAP: 20%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("GROSS GMV VOLUME", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("Rs. $totalGmv", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("+14% from last week", fontSize = 8.sp, color = BrandEmerald, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("SYSTEM COMMISSIONS", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("Rs. ${(totalGmv * 0.20).toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = AccentAmber)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Net royalties collected", fontSize = 8.sp, color = Color.Gray)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("PARTNER SHARE (80%)", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("Rs. ${(totalGmv * 0.80).toInt()}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Released to fleet wallets", fontSize = 8.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }

                // REAL-TIME CHARTS: WEEKLY VALUE SPARKLINE
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("📈 OPERATIONS TIMELINE (LAST 7 DAYS)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Transaction index based on Pakistan central standard clock", fontSize = 10.sp, color = Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(BrandEmerald.copy(0.12f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Rs. 2,400 Avg Ticket", fontSize = 8.sp, color = BrandEmerald, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Custom Graphic Canvas Line Chart
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                            ) {
                                val w = size.width
                                val h = size.height
                                
                                // Draw horizontal grid gridlines
                                drawLine(Color.White.copy(0.05f), Offset(0f, h * 0.25f), Offset(w, h * 0.25f), strokeWidth = 1f)
                                drawLine(Color.White.copy(0.05f), Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = 1f)
                                drawLine(Color.White.copy(0.05f), Offset(0f, h * 0.75f), Offset(w, h * 0.75f), strokeWidth = 1f)

                                // Hardcoded daily peak points representing weekly simulation
                                val points = listOf(
                                    Offset(w * 0.05f, h * 0.75f), // Mon
                                    Offset(w * 0.2f, h * 0.62f),  // Tue
                                    Offset(w * 0.35f, h * 0.8f),  // Wed
                                    Offset(w * 0.5f, h * 0.40f),  // Thu
                                    Offset(w * 0.65f, h * 0.52f), // Fri
                                    Offset(w * 0.8f, h * 0.25f),  // Sat
                                    Offset(w * 0.95f, h * 0.15f)  // Sun (Today peak)
                                )

                                // Draw glowing connection line path
                                for (i in 0 until points.size - 1) {
                                    drawLine(
                                        color = BrandEmerald,
                                        start = points[i],
                                        end = points[i + 1],
                                        strokeWidth = 4f
                                    )
                                }

                                // Draw pulsing node point on Today
                                val lastPt = points.last()
                                drawCircle(BrandEmerald.copy(0.35f), radius = 12f, center = lastPt)
                                drawCircle(Color.White, radius = 4f, center = lastPt)

                                // Node Points
                                points.forEach { pt ->
                                    drawCircle(BrandEmerald, radius = 3f, center = pt)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Today").forEach { day ->
                                    Text(day, fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 2) BOOKING FUNNEL SEGMENTATION
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📑 BOOKING PERFORMANCE METRICS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Operations pipeline volumes & completion rates", fontSize = 10.sp, color = Color.Gray)

                            Spacer(modifier = Modifier.height(14.dp))

                            val completed = bookings.count { it.status == "Completed" }
                            val active = bookings.count { it.status != "Completed" && it.status != "Cancelled" }
                            val cancelled = bookings.count { it.status == "Cancelled" }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("COMPLETED JOBS", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text("$completed Jobs", fontSize = 14.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                                    }
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("ACTIVE DISPATCH", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text("$active Roles", fontSize = 14.sp, fontWeight = FontWeight.Black, color = AccentAmber)
                                    }
                                }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CANCELLED LEADS", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                        Text("$cancelled Jobs", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color.Red)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Text("Service Category Distribution Share", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.LightGray)
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                stats.forEach { (cat, pct, ratio) ->
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(cat, fontSize = 11.sp)
                                            Text(pct, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(if (ratio <= 0f) 0.01f else if (ratio >= 1f) 1f else ratio)
                                                    .height(6.dp)
                                                    .background(BrandEmerald, RoundedCornerShape(3.dp))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3) OPERATIONAL HOTZONES (Pakistan)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Active Operational Hotzones (Pakistan)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(10.dp))

                            cities.forEach { (c, log) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Place, null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(c, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(log, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }

            "Technicians" -> {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.12f))
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            val subTabs = listOf("Active Fleet", "Pending Audits")
                            subTabs.forEach { subT ->
                                val isSel = techSubTab == subT
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .background(
                                            color = if (isSel) BrandEmerald else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { techSubTab = subT }
                                        .testTag("tech_subtab_${subT.replace(" ", "_").lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val countSuffix = if (subT == "Active Fleet") " (${technicians.count { it.isApproved }})" else " (${technicians.count { !it.isApproved }})"
                                    Text(
                                        text = "$subT$countSuffix",
                                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                if (techSubTab == "Active Fleet") {
                    val approvedList = technicians.filter { it.isApproved }
                    if (approvedList.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                            ) {
                                Text(
                                    "No approved active technicians in fleet currently.",
                                    modifier = Modifier.padding(24.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    } else {
                        items(approvedList) { tech ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f)),
                                modifier = Modifier.fillMaxWidth().testTag("active_tech_card_${tech.phone}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tech.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        if (tech.isOnline) BrandEmerald.copy(0.15f) else Color.Gray.copy(0.15f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (tech.isOnline) "ONLINE" else "OFFLINE",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (tech.isOnline) BrandEmerald else Color.Gray
                                                )
                                            }
                                        }
                                        Text("${tech.category} · ${tech.city}", fontSize = 11.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("📞 ${tech.phone}", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("💳 Payout Target: ${tech.bankDetails}", fontSize = 11.sp, color = Color.DarkGray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("⭐ ${tech.rating}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                        Text("${tech.totalJobs} live jobs", fontSize = 10.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = { viewModel.rejectTechnician(tech.phone) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(0.85f)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(28.dp),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Revoke ID", fontSize = 9.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (pendingApprovals.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                            ) {
                                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Verified, null, tint = BrandEmerald, modifier = Modifier.size(36.dp))
                                    Text(
                                        text = "All prospective CNIC submissions verified!",
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        items(pendingApprovals) { applicant ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, AccentAmber.copy(0.5f)),
                                modifier = Modifier.fillMaxWidth().testTag("pending_aud_card_${applicant.phone}")
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(applicant.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("${applicant.category} · ${applicant.city}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Card(colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(0.15f))) {
                                            Text(
                                                text = "CNIC AUDIT",
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Black,
                                                color = AccentAmber,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    Divider()

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1.5f)) {
                                            Text("SUBMITTED CNIC CARD ID", fontSize = 8.sp, color = Color.Gray)
                                            Text(applicant.cnic, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text("PAYMENT WALLET ADDR", fontSize = 8.sp, color = Color.Gray)
                                            Text(applicant.bankDetails, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(4.dp))
                                                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AddAPhoto, null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                                            Text("Selfie Captured", fontSize = 6.sp, color = Color.Gray, modifier = Modifier.padding(top = 28.dp))
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        Button(
                                            onClick = { viewModel.rejectTechnician(applicant.phone) },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f).testTag("reject_tech_click_${applicant.phone}"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Reject ID", fontSize = 11.sp, color = Color.White)
                                        }
                                        Button(
                                            onClick = { viewModel.approveTechnician(applicant.phone) },
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                            modifier = Modifier.weight(1.2f).testTag("approve_tech_click_${applicant.phone}"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Approve Profile", fontSize = 11.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Live Map" -> {
                item {
                    AdminLiveFleetMapView(viewModel = viewModel)
                }
            }

            "Bookings" -> {
                if (bookings.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                        ) {
                            Text(
                                "No active platform bookings. Open customer tab to book services.",
                                modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.Gray
                            )
                        }
                    }
                } else {
                    items(bookings) { booking ->
                        var isExpandedOverride by remember { mutableStateOf(false) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.12f)),
                            modifier = Modifier.fillMaxWidth().testTag("admin_booking_card_${booking.id}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(booking.serviceName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Customer: ${booking.customerName} (${booking.customerPhone})", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    val color = when (booking.status) {
                                        "Completed" -> BrandEmerald
                                        "Cancelled" -> Color.Red
                                        else -> AccentAmber
                                    }
                                    Text(
                                        booking.status.uppercase(),
                                        fontWeight = FontWeight.ExtraBold, fontSize = 9.sp, color = color
                                    )
                                }

                                Divider()

                                Text("Address Details: ${booking.customerAddress} · ${booking.customerCity}", fontSize = 11.sp, color = Color.DarkGray)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Fare: Rs. ${booking.price.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                                    Text("Assigned tech: ${booking.technicianName ?: "Searching eligible roster..."}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                }

                                if (booking.status == "Requested" || booking.status == "Assigned") {
                                    Button(
                                        onClick = { isExpandedOverride = !isExpandedOverride },
                                        colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                                        modifier = Modifier.fillMaxWidth().testTag("override_btn_${booking.id}"),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Manual Dispatch Override", fontSize = 11.sp, color = Color.White)
                                    }

                                    if (isExpandedOverride) {
                                        val onlineTechs = technicians.filter { it.isApproved && it.isOnline && it.city == booking.customerCity }
                                        Text("Pick active technician to assign:", fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                        if (onlineTechs.isEmpty()) {
                                            Text("No technicians online, approved & ready in ${booking.customerCity} currently.", fontSize = 10.sp, color = Color.Red)
                                        } else {
                                            onlineTechs.forEach { tech ->
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.manualAssignTechnician(booking.id, tech.phone)
                                                            isExpandedOverride = false
                                                        }
                                                        .testTag("force_dispatch_${tech.phone}")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(tech.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                            Text("Main category: ${tech.category}", fontSize = 10.sp, color = Color.Gray)
                                                        }
                                                        Text("Assign Work", fontSize = 11.sp, color = BrandEmerald, fontWeight = FontWeight.SemiBold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Dispute Desk" -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Active Incidents & Conflict Mediation Console", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Initiate customer refunds, audit communications, and deploy backup crews.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                if (tickets.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))
                        ) {
                            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SupportAgent, null, tint = BrandEmerald, modifier = Modifier.size(36.dp))
                                Text("No logged support tickets!", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                } else {
                    items(tickets) { ticket ->
                        var agentReplyText by remember { mutableStateOf("") }
                        val isResolved = ticket.status == "Resolved"

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, if (ticket.status == "Escalated") Color.Red.copy(0.5f) else Color.Gray.copy(0.2f)),
                            modifier = Modifier.fillMaxWidth().testTag("admin_dispute_ticket_${ticket.id}")
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Ticket #${ticket.id} · ${ticket.customerName}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Phone: ${ticket.customerPhone}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = when (ticket.status) {
                                                    "Open" -> Color.Blue.copy(0.12f)
                                                    "In Progress" -> Color.Gray.copy(0.15f)
                                                    "Escalated" -> Color.Red.copy(0.12f)
                                                    "Resolved" -> BrandEmerald.copy(0.12f)
                                                    else -> Color.DarkGray.copy(0.12f)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = ticket.status.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = when (ticket.status) {
                                                "Open" -> Color.Blue
                                                "In Progress" -> Color.DarkGray
                                                "Escalated" -> Color.Red
                                                "Resolved" -> BrandEmerald
                                                else -> Color.DarkGray
                                            }
                                        )
                                    }
                                }

                                Text("Complaint Category: ${ticket.category}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                                Text("Customer Description: ${ticket.description}", fontSize = 12.sp)

                                if (ticket.bookingId != null) {
                                    Text("Linked Booking Reference: #${ticket.bookingId}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                                }

                                Divider(color = Color.LightGray.copy(0.2f))

                                Text("Chat Transcript History:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ticket.chatMessages.forEach { msg ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = if (msg.sender == "Customer") Arrangement.Start else Arrangement.End
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(
                                                        color = if (msg.sender == "Customer") Color.White.copy(0.05f) else BrandEmerald.copy(0.12f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .padding(8.dp)
                                            ) {
                                                Text("${msg.sender}: ${msg.message}", fontSize = 11.sp, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                if (!isResolved) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("AUDITOR DISPUTE INTERVENTIONS:", fontSize = 9.sp, fontWeight = FontWeight.Black, color = AccentAmber)
                                    
                                    // Extreme Mediator options rows
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = { 
                                                viewModel.updateTicketStatus(ticket.id, "Resolved")
                                                viewModel.submitSupportAgentReply(ticket.id, "AUDIT REMEDY: We have verified the incident and processed a full refund to your EasyPaisa wallet. A formal apology discount voucher of Rs. 150 has been credited under 'FIXNOW10'.")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Text("Settle & Refund Wallet", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.updateTicketStatus(ticket.id, "Escalated")
                                                viewModel.submitSupportAgentReply(ticket.id, "COMPLIANCE NOTICE: This ticket is escalated to NADRA ID fraud audit departments for technician character verification.")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Text("Escalate & Lock Tech", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.submitSupportAgentReply(ticket.id, "EMERGENCY DISPATCH: Sincere apologies. A senior Field Inspection backup leader is assigned to household immediately.")
                                                if (ticket.bookingId != null) {
                                                    viewModel.manualAssignTechnician(ticket.bookingId, "03009988771") // Assign top backup tech
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(1f).height(38.dp)
                                        ) {
                                            Text("Deploy Backup crew", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.updateTicketStatus(ticket.id, "Resolved") },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.weight(0.8f).height(38.dp)
                                        ) {
                                            Text("Dismiss Case", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Quick replies section
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Macro Agent Replies:", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        listOf("Refund PKR 150", "Reviewing Profile", "Formal Apology").forEach { reply ->
                                            OutlinedButton(
                                                onClick = {
                                                    val finalMsg = when (reply) {
                                                        "Refund PKR 150" -> "Dear Ahmad Malik, we have credited coupon code 'FIXNOW10' to settle this pricing mismatch."
                                                        "Reviewing Profile" -> "We are conducting NADRA biometric trace reviews on the dispatch technician's profile."
                                                        "Formal Apology" -> "Greetings. We acknowledge the substandard quality. A compensation refund has been released."
                                                        else -> reply
                                                    }
                                                    viewModel.submitSupportAgentReply(ticket.id, finalMsg)
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.weight(1f).height(30.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(reply, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Custom reply text area
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = agentReplyText,
                                            onValueChange = { agentReplyText = it },
                                            placeholder = { Text("Write custom agent response...", fontSize = 11.sp) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).height(48.dp).testTag("agent_reply_input_${ticket.id}")
                                        )
                                        Button(
                                            onClick = {
                                                if (agentReplyText.isNotEmpty()) {
                                                    viewModel.submitSupportAgentReply(ticket.id, agentReplyText)
                                                    agentReplyText = ""
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                            modifier = Modifier.height(48.dp).testTag("agent_reply_submit_${ticket.id}"),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Send", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Fraud Check" -> {
                val alerts = fraudAlerts

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🛡️ ENTERPRISE FRAUD DETECTION & COMPLIANCE", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Live ML integrity checker auditing GPS spoofing, rate-gouging, and IP sharing.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                items(alerts) { alert ->
                    val color = when (alert.severity) {
                        "CRITICAL" -> Color.Red
                        "HIGH" -> AccentAmber
                        else -> Color.Gray
                    }
                    val isResolved = alert.isResolved

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.2.dp, if (isResolved) Color.Gray.copy(0.3f) else color.copy(0.5f)),
                        modifier = Modifier.fillMaxWidth().testTag("fraud_alert_${alert.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(alert.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (isResolved) Color.Gray.copy(0.1f) else color.copy(0.12f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isResolved) "RESOLVED" else alert.severity,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isResolved) Color.Gray else color
                                    )
                                }
                            }

                            Text(alert.description, fontSize = 11.sp, color = Color.LightGray)
                            Text("Associated Phone: ${alert.associatedPhone}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = BrandEmerald)

                            if (!isResolved) {
                                Divider(color = Color.LightGray.copy(0.1f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.blockTechnicianProfile(alert.associatedPhone) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Suspend Partner", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.resolveFraudAlert(alert.id) },
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Dismiss & Archive", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Notifications" -> {
                val broadcastLogs = announcementsLogs

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("📣 PUSH NOTIFICATION & ANNOUNCEMENT DESK", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Broadcast high-urgency notifications to customers or service professionals.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.12f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Create Mass Broadcast announcement", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Select Target Audience:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("All Techs", "All Customers", "Karachi Only", "Lahore Only").forEach { grp ->
                                        val isSel = targetGroup == grp
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { targetGroup = grp },
                                            label = { Text(grp, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BrandEmerald.copy(0.15f),
                                                selectedLabelColor = BrandEmerald
                                            )
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = broadcastTitle,
                                onValueChange = { broadcastTitle = it },
                                label = { Text("Broadcast Subject Header") },
                                placeholder = { Text("e.g. System upgrade notification") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("broadcast_title_input")
                            )

                            OutlinedTextField(
                                value = broadcastContent,
                                onValueChange = { broadcastContent = it },
                                label = { Text("Message Body Body text") },
                                placeholder = { Text("A mandatory facial scan updates is pushing live tonight...") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth().testTag("broadcast_content_input")
                            )

                            Button(
                                onClick = {
                                    if (broadcastTitle.isNotEmpty() && broadcastContent.isNotEmpty()) {
                                        viewModel.sendGlobalPushAnnouncement(targetGroup, broadcastTitle, broadcastContent)
                                        broadcastTitle = ""
                                        broadcastContent = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit_broadcast_action_btn"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Campaign, null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Broadcast System Messages", fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                    }
                }

                item {
                    Text("Sent Core System Logs & Broadcast History", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                items(broadcastLogs) { log ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(log.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Card(colors = CardDefaults.cardColors(containerColor = BrandEmerald.copy(0.15f))) {
                                    Text(
                                        log.targetGroup.uppercase(),
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BrandEmerald,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(log.content, fontSize = 11.sp, color = Color.LightGray)
                            Text("Sent system ID: #${log.id}", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "Payments" -> {
                val payouts = paymentWithdrawals

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("💸 TRANSACTION MONITOR & PAYOUT APPROVALS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Authorized field technician cashout requests and platform dynamic commission ratios.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Operational Revenue Commission Stream", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Technician Wallet (80%)", fontSize = 10.sp, color = Color.LightGray)
                                Text("Platform Overhead (20%)", fontSize = 10.sp, color = Color.LightGray)
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(5.dp))
                            ) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(8f)
                                            .background(BrandEmerald, RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(2f)
                                            .background(AccentAmber, RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp))
                                    )
                                }
                            }
                        }
                    }
                }

                items(payouts) { pwd ->
                    val statusColor = when (pwd.status) {
                        "Released & Processed" -> BrandEmerald
                        "Under Compliance Hold" -> Color.Red
                        else -> AccentAmber
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(0.12f)),
                        modifier = Modifier.fillMaxWidth().testTag("withdrawal_txn_${pwd.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(pwd.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                    Text("Ref ID: ${pwd.id}", fontSize = 10.sp, color = Color.Gray)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(statusColor.copy(0.12f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = pwd.status.uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = statusColor
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("PAYOUT GATEWAY DETAILS:", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(pwd.paymentDetails, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("WITHDRAWAL VALUE:", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("Rs. ${pwd.amount.toInt()}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                                }
                            }

                            if (pwd.status.contains("Pending") || pwd.status.contains("Hold")) {
                                Divider(color = Color.LightGray.copy(0.1f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.rejectPayoutWithdrawal(pwd.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier.weight(1f).height(36.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Compliance Hold", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { viewModel.releasePayoutWithdrawal(pwd.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                        modifier = Modifier.weight(1.2f).height(36.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("Approve & Deposit", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Coupons" -> {
                item {
                    Text("Campaign Coupon Codes & Referral Engine Dashboard", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🎟️ Create New Promotional Coupon / Referral Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            OutlinedTextField(
                                value = newCouponCode,
                                onValueChange = { newCouponCode = it },
                                label = { Text("Code (e.g. SAVE300)") },
                                placeholder = { Text("Symmetric coupon key") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("new_coupon_code_field")
                            )

                            OutlinedTextField(
                                value = newPromoDiscount,
                                onValueChange = { newPromoDiscount = it },
                                label = { Text("Discount Amount (Rs.)") },
                                placeholder = { Text("e.g. 200") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("new_coupon_discount_field")
                            )

                            OutlinedTextField(
                                value = newPromoDesc,
                                onValueChange = { newPromoDesc = it },
                                label = { Text("Description") },
                                placeholder = { Text("e.g. Flat Rs. 200 off on any water cleaning service") },
                                minLines = 1,
                                maxLines = 2,
                                modifier = Modifier.fillMaxWidth().testTag("new_coupon_desc_field")
                            )

                            Button(
                                onClick = {
                                    val amt = newPromoDiscount.toDoubleOrNull() ?: 100.0
                                    if (newCouponCode.isNotEmpty() && newPromoDesc.isNotEmpty()) {
                                        viewModel.createCoupon(newCouponCode, amt, newPromoDesc)
                                        newCouponCode = ""
                                        newPromoDiscount = ""
                                        newPromoDesc = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_coupon_btn")
                            ) {
                                Text("Add Promo Campaign", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Currently Configured Referral & Campaign Promotions", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                items(couponsList) { coupon ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("coupon_item_${coupon.code}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(coupon.code, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = BrandEmerald)
                                    if (coupon.isReferral) {
                                        Box(
                                            modifier = Modifier.background(Color.Yellow.copy(0.2f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("Referral tied", fontSize = 8.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(coupon.description, fontSize = 11.sp)
                            }
                            Text("Rs. ${coupon.discountAmount.toInt()} Off", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
    }
}
}

/**
 * Clean Administrative Secure Passcode Gate Screen
 */
@Composable
fun AdminLoginView(
    viewModel: FixNowViewModel
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 600.dp

        val authError by viewModel.adminAuthError.collectAsState()
        var passcode by remember { mutableStateOf("") }
        var isPasscodeVisible by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isWide) 32.dp else 24.dp, vertical = 24.dp)
                .testTag("admin_login_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Back to gateway at top
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.switchMode("Onboarding") },
                modifier = Modifier.testTag("admin_back_to_gateway")
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to Gateway", tint = BrandCharcoal)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Exit Admin Desk", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = BrandCharcoal)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = "Admin Console",
                tint = BrandEmerald,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "FixNow Admin Web-Desk",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = BrandCharcoal
            )
            Text(
                text = "Enter Administrative Passcode to Access Operations Console",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = {
                            passcode = it
                            viewModel.clearAdminAuthError()
                        },
                        label = { Text("Security Access Code") },
                        placeholder = { Text("Enter admin password") },
                        visualTransformation = if (isPasscodeVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = BrandEmerald) },
                        trailingIcon = {
                            val image = if (isPasscodeVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { isPasscodeVisible = !isPasscodeVisible }) {
                                Icon(imageVector = image, contentDescription = if (isPasscodeVisible) "Hide password" else "Show password")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("admin_password_input")
                    )

                    if (authError != null) {
                        Text(
                            text = authError!!,
                            color = Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("admin_auth_error_label")
                        )
                    }

                    Button(
                        onClick = { viewModel.loginAdmin(passcode) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_login_submit_btn")
                    ) {
                        Text("Unlock Console", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }


    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLiveFleetMapView(
    viewModel: FixNowViewModel,
    modifier: Modifier = Modifier
) {
    val technicians by viewModel.technicians.collectAsState()
    val bookings by viewModel.bookings.collectAsState()
    val customers by viewModel.customers.collectAsState()

    var selectedCity by remember { mutableStateOf("Lahore") }
    var showLiveMap by remember { mutableStateOf(true) }

    // Coordinates mapping based on City
    var adminZoom by remember { mutableStateOf(11.0) }
    var adminCenterLat by remember(selectedCity) {
        mutableStateOf(
            when (selectedCity) {
                "Karachi" -> 24.8607
                "Islamabad" -> 33.6844
                else -> 31.5204
            }
        )
    }
    var adminCenterLng by remember(selectedCity) {
        mutableStateOf(
            when (selectedCity) {
                "Karachi" -> 67.0011
                "Islamabad" -> 73.0479
                else -> 74.3587
            }
        )
    }

    val transition = rememberInfiniteTransition(label = "fleet_radar")
    val radarPulseRadius by transition.animateFloat(
        initialValue = 10f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_pulse"
    )
    val riderStepProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rider_step"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mode & Filters Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("📡 LIVE FLEET TRANSMISSION UNIT", fontWeight = FontWeight.Black, fontSize = 11.sp, color = BrandEmerald)
                        Text("Active positioning overlay of technicians & households", fontSize = 11.sp, color = Color.Gray)
                    }
                    
                    FilledIconButton(
                        onClick = { showLiveMap = !showLiveMap },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (showLiveMap) BrandEmerald else Color.Black
                        ),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (showLiveMap) Icons.Default.GpsFixed else Icons.Default.GpsNotFixed,
                            contentDescription = "Toggle Map style",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Divider()

                // City Selectors & Toggle info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cities
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Lahore", "Karachi", "Islamabad").forEach { city ->
                            val isSel = selectedCity == city
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCity = city },
                                label = { Text(city, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandEmerald.copy(0.12f),
                                    selectedLabelColor = BrandEmerald
                                ),
                                border = BorderStroke(1.dp, if (isSel) BrandEmerald else Color.LightGray.copy(alpha = 0.5f))
                            )
                        }
                    }

                    // Map label badge
                    Box(
                        modifier = Modifier
                            .background(if (showLiveMap) BrandEmerald.copy(0.15f) else Color.DarkGray.copy(0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showLiveMap) "LIVE SERVICE MAP" else "REALTIME FLEET RADAR",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = if (showLiveMap) BrandEmerald else Color.Gray
                        )
                    }
                }
            }
        }

        // FLEET MAP RENDER VIEWPORT
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .testTag("admin_fleet_map_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSlate),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Real Mapbox Map with online rider pinpoints (ALWAYS ACTIVE - No Mock/Fake alternatives)
                val token = BuildConfig.MAPBOX_ACCESS_TOKEN
                val hasValidToken = token.isNotEmpty() && token != "your_token_here" && !token.contains("your_token", ignoreCase = true)

                val mapboxUrl = if (hasValidToken) {
                    "https://api.mapbox.com/styles/v1/mapbox/dark-v11/static/$adminCenterLng,$adminCenterLat,$adminZoom,0,0/600x300@2x?access_token=$token"
                } else {
                    ""
                }

                if (mapboxUrl.isNotEmpty()) {
                    AsyncImage(
                        model = mapboxUrl,
                        contentDescription = "Mapbox Admin Fleet Active",
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selectedCity) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val degreesPerPixel = 360.0 / (256.0 * Math.pow(2.0, adminZoom))
                                    adminCenterLng -= dragAmount.x * degreesPerPixel
                                    adminCenterLat += dragAmount.y * degreesPerPixel
                                }
                            },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    // Fallback interactive grid
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A))
                            .pointerInput(selectedCity) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val degreesPerPixel = 360.0 / (256.0 * Math.pow(2.0, adminZoom))
                                    adminCenterLng -= dragAmount.x * degreesPerPixel
                                    adminCenterLat += dragAmount.y * degreesPerPixel
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = BrandEmerald.copy(0.04f),
                                radius = size.minDimension / 3,
                                center = center
                            )
                        }
                    }
                }

                // High-precision viewport projection layering
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val pixelsPerDegree = 256.0 * Math.pow(2.0, adminZoom) / 360.0

                    fun getOffset(destLat: Double, destLng: Double): Offset {
                        val dx = (destLng - adminCenterLng) * pixelsPerDegree
                        val dy = (adminCenterLat - destLat) * pixelsPerDegree
                        return Offset((width / 2) + dx.toFloat(), (height / 2) + dy.toFloat())
                    }

                    // Plot Technicians of selected city
                    technicians.filter { it.city == selectedCity }.forEach { tech ->
                        val offset = getOffset(tech.latitude, tech.longitude)
                        if (offset.x in 0f..width && offset.y in 0f..height) {
                            val techDotColor = if (tech.isOnline) BrandEmerald else Color.Gray
                            if (tech.isOnline) {
                                drawCircle(
                                    color = BrandEmerald.copy(0.2f),
                                    radius = 16f,
                                    center = offset
                                )
                            }
                            drawCircle(
                                color = techDotColor,
                                radius = 7f,
                                center = offset
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.5f,
                                center = offset
                            )
                        }
                    }

                    // Plot Active Booking Customers and overlapping requested heat maps of demand
                    bookings.filter { it.customerCity == selectedCity && it.status != "Completed" && it.status != "Cancelled" }.forEach { booking ->
                        val custOffset = getOffset(booking.latitude, booking.longitude)
                        if (custOffset.x in 0f..width && custOffset.y in 0f..height) {
                            val heatmapRadiusPixels = (1200.0 * pixelsPerDegree / 111320.0).toFloat().coerceAtLeast(15f)
                            drawCircle(
                                color = Color(0x38DF3C30),
                                radius = heatmapRadiusPixels,
                                center = custOffset
                            )
                            drawCircle(
                                color = Color(0x78DF3C30),
                                radius = heatmapRadiusPixels,
                                center = custOffset,
                                style = Stroke(width = 2f)
                            )

                            drawCircle(
                                color = Color(0xFF38BDF8).copy(alpha = 0.4f),
                                radius = 18f,
                                center = custOffset
                            )
                            drawCircle(
                                color = Color(0xFF38BDF8),
                                radius = 8f,
                                center = custOffset
                            )

                            if (booking.techLatitude != 0.0) {
                                val techOffset = getOffset(booking.techLatitude, booking.techLongitude)
                                drawLine(
                                    color = BrandEmerald,
                                    start = techOffset,
                                    end = custOffset,
                                    strokeWidth = 5f
                                )
                            }
                        }
                    }
                }

                // Zoom controls overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { adminZoom = (adminZoom + 1.0).coerceAtMost(20.0) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(0.75f), CircleShape)
                    ) {
                        Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { adminZoom = (adminZoom - 1.0).coerceAtLeast(1.0) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(0.75f), CircleShape)
                    ) {
                        Text("-", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Map Overlay Card Legends - Overlayed beautifully inside the Map's Box container
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("LEGEND METRICS:", color = Color.Gray, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(AccentAmber, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Online Rider Technician", color = Color.White, fontSize = 8.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF38BDF8), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Service Destination (User)", color = Color.White, fontSize = 8.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(BrandEmerald, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Active Connect Transit Way", color = Color.White, fontSize = 8.sp)
                        }
                    }
                }
            }
        }

        // Summary Fleet State Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val totalRiders = technicians.count { it.city == selectedCity }
            val onlineRiders = technicians.count { it.isOnline && it.city == selectedCity }
            val activeDeliveries = bookings.count { it.customerCity == selectedCity && it.status != "Completed" && it.status != "Cancelled" }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ACTIVE FLEET", fontSize = 8.sp, color = Color.Gray)
                    Text("$onlineRiders / $totalRiders ON", fontSize = 14.sp, fontWeight = FontWeight.Black, color = BrandCharcoal)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))
            ) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PENDING DISPATCH", fontSize = 8.sp, color = Color.Gray)
                    Text("$activeDeliveries JOBS", fontSize = 14.sp, fontWeight = FontWeight.Black, color = BrandEmerald)
                }
            }
        }
    }
}
