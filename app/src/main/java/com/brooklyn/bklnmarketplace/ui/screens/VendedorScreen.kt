package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Catalog
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
private data class SellerReportInsert(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("reported_user") val reportedUser: String,
    val reason: String,
    val description: String? = null,
    val status: String = "pending",
)

private val SELLER_REPORT_REASONS = listOf(
    "Comportamiento abusivo o acoso",
    "Estafa o fraude",
    "Información falsa en el perfil",
    "Venta de productos prohibidos",
    "Spam o actividad sospechosa",
    "Otro"
)

@Composable
fun VendedorScreen(navController: NavController, sellerId: String) {
    val scope = rememberCoroutineScope()

    var seller      by remember { mutableStateOf<Profile?>(null) }
    var products    by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }
    var isFollowing by remember { mutableStateOf(false) }
    var followLoading by remember { mutableStateOf(false) }
    var currentUserId by remember { mutableStateOf("") }
    var activeTab        by remember { mutableStateOf(0) } // 0=Productos 1=Colecciones 2=Reseñas
    var catalogs         by remember { mutableStateOf<List<Catalog>>(emptyList()) }
    var reviews          by remember { mutableStateOf<List<Review>>(emptyList()) }
    var hasReviewed      by remember { mutableStateOf(false) }
    var showReviewModal  by remember { mutableStateOf(false) }
    var reviewRating     by remember { mutableStateOf(0) }
    var reviewComment    by remember { mutableStateOf("") }
    var reviewSubmitting by remember { mutableStateOf(false) }

    var showReportDialog   by remember { mutableStateOf(false) }
    var reportReason       by remember { mutableStateOf("") }
    var reportDescription  by remember { mutableStateOf("") }
    var reportSubmitting   by remember { mutableStateOf(false) }
    var reportSent         by remember { mutableStateOf(false) }

    LaunchedEffect(sellerId) {
        val me = SupabaseClient.client.auth.currentUserOrNull()
        currentUserId = me?.id ?: ""

        // Load profile
        try {
            seller = SupabaseClient.client.postgrest
                .from("profiles")
                .select(columns = Columns.raw(
                    "id, full_name, username, avatar_url, plan, rating, review_count, " +
                    "followers_count, following_count, bio, created_at"
                )) {
                    filter { eq("id", sellerId) }
                }
                .decodeSingleOrNull<Profile>()
        } catch (_: Exception) {}

        // Load products
        try {
            products = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw("id, name, price, original_price, images, category, condition, views, active")) {
                    filter {
                        eq("user_id", sellerId)
                        eq("active", true)
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Product>()
        } catch (_: Exception) {}

        // Check if following
        if (currentUserId.isNotBlank() && currentUserId != sellerId) {
            try {
                val result = SupabaseClient.client.postgrest
                    .from("follows")
                    .select {
                        filter {
                            eq("follower_id", currentUserId)
                            eq("following_id", sellerId)
                        }
                    }
                    .decodeList<FollowRow>()
                isFollowing = result.isNotEmpty()
            } catch (_: Exception) {}
        }

        // Load reviews
        try {
            val loaded = SupabaseClient.client.postgrest
                .from("reviews")
                .select(columns = Columns.raw(
                    "id, reviewer_id, seller_id, rating, comment, created_at, " +
                    "profiles!reviewer_id(full_name, avatar_url, username)"
                )) {
                    filter { eq("seller_id", sellerId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Review>()
            reviews = loaded
            if (currentUserId.isNotBlank()) hasReviewed = loaded.any { it.reviewerId == currentUserId }
        } catch (_: Exception) {}

        // Load catalogs if seller is premium
        if (seller?.plan == "premium") {
            try {
                val loaded = SupabaseClient.client.postgrest
                    .from("catalogs")
                    .select(columns = Columns.raw(
                        "id, name, description, cover_url, expires_at, " +
                        "catalog_products(product_id, sort_order, products(id, name, images))"
                    )) {
                        filter { eq("user_id", sellerId); eq("active", true) }
                        order("sort_order", Order.ASCENDING)
                    }
                    .decodeList<Catalog>()
                catalogs = loaded.filter { !isExpired(it.expiresAt) }
            } catch (_: Exception) {}
        }

        loading = false
    }

    fun toggleFollow() {
        if (currentUserId.isBlank() || currentUserId == sellerId) return
        followLoading = true
        scope.launch {
            try {
                if (isFollowing) {
                    SupabaseClient.client.postgrest.from("follows").delete {
                        filter {
                            eq("follower_id", currentUserId)
                            eq("following_id", sellerId)
                        }
                    }
                    isFollowing = false
                } else {
                    SupabaseClient.client.postgrest.from("follows").insert(
                        mapOf("follower_id" to currentUserId, "following_id" to sellerId)
                    )
                    isFollowing = true
                }
            } catch (_: Exception) {}
            followLoading = false
        }
    }

    fun submitReview() {
        if (reviewRating == 0 || reviewSubmitting) return
        reviewSubmitting = true
        scope.launch {
            try {
                SupabaseClient.client.postgrest.from("reviews").insert(
                    ReviewInsert(
                        reviewerId = currentUserId,
                        sellerId   = sellerId,
                        rating     = reviewRating,
                        comment    = if (reviewComment.isBlank()) null else reviewComment.trim()
                    )
                )
                val loaded = SupabaseClient.client.postgrest
                    .from("reviews")
                    .select(columns = Columns.raw(
                        "id, reviewer_id, seller_id, rating, comment, created_at, " +
                        "profiles!reviewer_id(full_name, avatar_url, username)"
                    )) {
                        filter { eq("seller_id", sellerId) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<Review>()
                reviews = loaded
                hasReviewed = true
                showReviewModal = false
                reviewRating = 0
                reviewComment = ""
            } catch (_: Exception) {}
            reviewSubmitting = false
        }
    }

    fun submitSellerReport() {
        if (reportReason.isBlank() || reportSubmitting) return
        scope.launch {
            reportSubmitting = true
            try {
                val existing = SupabaseClient.client.postgrest
                    .from("reports")
                    .select { filter { eq("reporter_id", currentUserId); eq("reported_user", sellerId) } }
                    .decodeList<SellerReportInsert>()
                if (existing.isEmpty()) {
                    SupabaseClient.client.postgrest.from("reports").insert(
                        SellerReportInsert(
                            reporterId = currentUserId,
                            reportedUser = sellerId,
                            reason = reportReason,
                            description = reportDescription.trim().ifBlank { null },
                        )
                    )
                }
                reportSent = true
            } catch (_: Exception) { reportSent = true }
            reportSubmitting = false
            showReportDialog = false
        }
    }

    val isPremium = seller?.plan == "premium"

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {

        // ── TOPBAR ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(BklnBgCard)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = BklnWhite)
            }
            Text(
                seller?.fullName ?: "Vendedor",
                color = BklnWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isPremium) {
                Text("👑", fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
            }
        }

        HorizontalDivider(color = BklnStroke)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // ── PROFILE HEADER ─────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BklnBgCard)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(BklnBgInput)
                                .border(2.dp, if (isPremium) BklnYellow else BklnOrange, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            val av = seller?.avatarUrl
                            if (!av.isNullOrBlank()) {
                                AsyncImage(
                                    model = av,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    (seller?.fullName ?: "?").first().uppercaseChar().toString(),
                                    color = BklnWhite,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            seller?.fullName ?: "Vendedor",
                            color = BklnWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!seller?.username.isNullOrBlank()) {
                            Text("@${seller?.username}", color = BklnMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(8.dp))

                        // Plan badge
                        if (isPremium) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x1AF5C842))
                                    .border(1.dp, Color(0x4DF5C842), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("👑 PREMIUM", color = BklnYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        // Bio
                        if (!seller?.bio.isNullOrBlank()) {
                            Text(
                                seller!!.bio!!,
                                color = BklnMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            )
                            Spacer(Modifier.height(10.dp))
                        }

                        // Stats row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SellerStat((seller?.followersCount ?: 0).toString(), "Seguidores")
                            Box(Modifier.width(1.dp).height(32.dp).background(BklnStroke))
                            SellerStat((seller?.followingCount ?: 0).toString(), "Siguiendo")
                            Box(Modifier.width(1.dp).height(32.dp).background(BklnStroke))
                            SellerStat(products.size.toString(), "Productos")
                            Box(Modifier.width(1.dp).height(32.dp).background(BklnStroke))
                            val r = seller?.rating ?: 0.0
                            SellerStat(if (r > 0) "${"%.1f".format(r)}★" else "—", "Rating")
                        }

                        // Follow button (only if not own profile)
                        if (currentUserId.isNotBlank() && currentUserId != sellerId) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isFollowing) Color(0x1A3DDC97) else BklnOrange
                                    )
                                    .border(
                                        1.dp,
                                        if (isFollowing) BklnSuccess else BklnOrange,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable(enabled = !followLoading) { toggleFollow() }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (followLoading) {
                                    CircularProgressIndicator(
                                        color = if (isFollowing) BklnSuccess else BklnWhite,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        if (isFollowing) "✓ Siguiendo" else "+ Seguir",
                                        color = if (isFollowing) BklnSuccess else BklnWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (reportSent) "✓ Reporte enviado" else "⚑ Reportar vendedor",
                                color = if (reportSent) BklnSuccess else BklnMuted,
                                fontSize = 11.sp,
                                modifier = if (!reportSent) Modifier.clickable { showReportDialog = true } else Modifier
                            )
                        }
                    }
                }

                // ── TABS ───────────────────────────────────────────────────
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BklnBgCard)
                            .border(1.dp, BklnStroke)
                    ) {
                        val tabs = buildList {
                            add(Pair("Productos (${products.size})", 0))
                            if (isPremium && catalogs.isNotEmpty()) add(Pair("Colecciones (${catalogs.size})", 1))
                            add(Pair("Reseñas (${seller?.reviewCount ?: 0})", 2))
                        }
                        tabs.forEach { (label, tabIndex) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { activeTab = tabIndex }
                                    .then(
                                        if (activeTab == tabIndex) Modifier.drawBehind {
                                            drawLine(
                                                color = BklnOrange,
                                                start = Offset(0f, size.height),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = 2.dp.toPx()
                                            )
                                        } else Modifier
                                    )
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (activeTab == tabIndex) BklnWhite else BklnMuted,
                                    fontSize = 13.sp,
                                    fontWeight = if (activeTab == tabIndex) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // ── TAB CONTENT ────────────────────────────────────────────
                when (activeTab) {
                0 -> {
                    // Products grid
                    if (products.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📦", fontSize = 36.sp)
                                Spacer(Modifier.height(10.dp))
                                Text("Sin productos aún", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Este vendedor no ha publicado nada todavía.", color = BklnMuted, fontSize = 13.sp)
                            }
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(4.dp))
                        }
                        items(products, key = { it.id }) { product ->
                            Box(modifier = Modifier.padding(
                                start = if (products.indexOf(product) % 2 == 0) 10.dp else 0.dp,
                                end   = if (products.indexOf(product) % 2 == 1) 10.dp else 0.dp
                            )) {
                                SellerProductCard(product, onClick = {
                                    navController.navigate("producto/${product.id}")
                                })
                            }
                        }
                    }
                }
                1 -> {
                    // ── COLECCIONES TAB ───────────────────────────────────────
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp)
                                .padding(top = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            catalogs.forEach { cat ->
                                val prods  = cat.catalogProducts ?: emptyList()
                                val images = prods.mapNotNull { it.products?.images?.firstOrNull() }.take(4)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BklnBgCard)
                                        .border(1.dp, BklnStroke, RoundedCornerShape(10.dp))
                                        .clickable { navController.navigate("coleccion/${cat.id}") }
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                        CoverMosaic(coverUrl = cat.coverUrl, images = images, modifier = Modifier.fillMaxSize())
                                        if (!cat.expiresAt.isNullOrBlank()) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart).padding(8.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xCC000000))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) { Text("⏳ ${formatExpiryShort(cat.expiresAt)}", color = BklnWhite, fontSize = 9.sp) }
                                        }
                                    }
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(cat.name, color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (!cat.description.isNullOrBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(cat.description, color = BklnMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text("📦 ${prods.size} producto${if (prods.size != 1) "s" else ""}", color = BklnMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // ── REVIEWS TAB ──────────────────────────────────────────
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val rating = seller?.rating ?: 0.0
                        val total  = reviews.size
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BklnBgCard)
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(end = 20.dp)
                                ) {
                                    Text(
                                        if (rating > 0) "${"%.1f".format(rating)}" else "—",
                                        color = BklnYellow,
                                        fontSize = 38.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 38.sp
                                    )
                                    Text(starsText(rating), color = BklnYellow, fontSize = 12.sp, letterSpacing = 2.sp)
                                    Text("$total reseña${if (total != 1) "s" else ""}", color = BklnMuted, fontSize = 11.sp)
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    (5 downTo 1).forEach { n ->
                                        val count = reviews.count { it.rating == n }
                                        val pct = if (total > 0) count.toFloat() / total else 0f
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("$n★", color = BklnMuted, fontSize = 10.sp, modifier = Modifier.width(22.dp))
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(BklnBgInput)
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(pct)
                                                        .background(BklnYellow)
                                                )
                                            }
                                            Text(" $count", color = BklnMuted, fontSize = 10.sp, modifier = Modifier.width(22.dp))
                                        }
                                    }
                                }
                            }
                            if (currentUserId.isNotBlank() && currentUserId != sellerId) {
                                Spacer(Modifier.height(14.dp))
                                if (hasReviewed) {
                                    Text("✓ Ya dejaste una reseña", color = BklnSuccess, fontSize = 12.sp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, BklnOrange, RoundedCornerShape(6.dp))
                                            .clickable { showReviewModal = true }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("✏️ Escribir reseña", color = BklnOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    if (reviews.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("⭐", fontSize = 32.sp)
                                Spacer(Modifier.height(10.dp))
                                Text("Sin reseñas todavía", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("Sé el primero en dejar una reseña.", color = BklnMuted, fontSize = 13.sp)
                            }
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .padding(top = 8.dp, bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                reviews.forEach { review ->
                                    ReviewCard(review)
                                }
                            }
                        }
                    }
                }
                }
            }
        }

        // ── REPORT DIALOG ───────────────────────────────────────────────────
        if (showReportDialog) {
            AlertDialog(
                onDismissRequest = { showReportDialog = false },
                containerColor = BklnBgCard,
                titleContentColor = BklnWhite,
                title = { Text("Reportar vendedor", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Motivo del reporte", color = BklnMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        SELLER_REPORT_REASONS.forEach { reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { reportReason = reason }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = reportReason == reason,
                                    onClick = { reportReason = reason },
                                    colors = RadioButtonDefaults.colors(selectedColor = BklnOrange)
                                )
                                Text(reason, color = BklnWhite, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = reportDescription,
                            onValueChange = { if (it.length <= 400) reportDescription = it },
                            placeholder = { Text("Detalles adicionales (opcional)", color = BklnMuted, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BklnOrange,
                                unfocusedBorderColor = BklnStroke,
                                focusedTextColor = BklnWhite,
                                unfocusedTextColor = BklnWhite,
                                cursorColor = BklnOrange
                            ),
                            maxLines = 3,
                            minLines = 2
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { submitSellerReport() },
                        enabled = reportReason.isNotBlank() && !reportSubmitting
                    ) {
                        if (reportSubmitting) {
                            CircularProgressIndicator(color = BklnOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Enviar reporte", color = BklnOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showReportDialog = false
                        reportReason = ""
                        reportDescription = ""
                    }) {
                        Text("Cancelar", color = BklnMuted)
                    }
                }
            )
        }

        // ── REVIEW MODAL ────────────────────────────────────────────────────
        if (showReviewModal) {
            AlertDialog(
                onDismissRequest = { showReviewModal = false },
                containerColor = BklnBgCard,
                titleContentColor = BklnWhite,
                title = { Text("Dejar una reseña", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Puntuación", color = BklnMuted, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { n ->
                                Text(
                                    "★",
                                    color = if (reviewRating >= n) BklnYellow else BklnMuted,
                                    fontSize = 30.sp,
                                    modifier = Modifier.clickable { reviewRating = n }
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = reviewComment,
                            onValueChange = { reviewComment = it },
                            placeholder = {
                                Text(
                                    "Tu experiencia con este vendedor... (opcional)",
                                    color = BklnMuted,
                                    fontSize = 13.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BklnOrange,
                                unfocusedBorderColor = BklnStroke,
                                focusedTextColor = BklnWhite,
                                unfocusedTextColor = BklnWhite,
                                cursorColor = BklnOrange
                            ),
                            maxLines = 4,
                            minLines = 3
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { submitReview() },
                        enabled = reviewRating > 0 && !reviewSubmitting
                    ) {
                        if (reviewSubmitting) {
                            CircularProgressIndicator(color = BklnOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Publicar", color = BklnOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReviewModal = false; reviewRating = 0; reviewComment = "" }) {
                        Text("Cancelar", color = BklnMuted)
                    }
                }
            )
        }
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

@Composable
private fun SellerStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = BklnMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SellerProductCard(product: Product, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(95.dp)
                .background(BklnBgInput),
            contentAlignment = Alignment.Center
        ) {
            val img = product.images?.firstOrNull()
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(catEmoji(product.category), fontSize = 24.sp)
            }
            if (product.originalPrice != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BklnOrange)
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) { Text("OFERTA", color = BklnWhite, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                product.name,
                color = BklnWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                formatSellerPrice(product.price),
                color = BklnYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@kotlinx.serialization.Serializable
private data class Review(
    val id: String = "",
    @kotlinx.serialization.SerialName("reviewer_id") val reviewerId: String = "",
    @kotlinx.serialization.SerialName("seller_id") val sellerId: String = "",
    val rating: Int = 0,
    val comment: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null
)

@kotlinx.serialization.Serializable
private data class ReviewInsert(
    @kotlinx.serialization.SerialName("reviewer_id") val reviewerId: String,
    @kotlinx.serialization.SerialName("seller_id") val sellerId: String,
    val rating: Int,
    val comment: String? = null
)

@kotlinx.serialization.Serializable
private data class FollowRow(
    @kotlinx.serialization.SerialName("follower_id") val followerId: String = "",
    @kotlinx.serialization.SerialName("following_id") val followingId: String = ""
)

private fun formatSellerPrice(price: Double?): String {
    if (price == null) return ""
    val fmt = NumberFormat.getNumberInstance(Locale("es", "ES"))
    return "${fmt.format(price.toLong())} XAF"
}

@Composable
private fun ReviewCard(review: Review) {
    val reviewer = review.profiles
    val stars    = "★".repeat(review.rating) + "☆".repeat(5 - review.rating)
    val date     = formatReviewDate(review.createdAt)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BklnBgInput)
            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(BklnBgCard),
                contentAlignment = Alignment.Center
            ) {
                val av = reviewer?.avatarUrl
                if (!av.isNullOrBlank()) {
                    AsyncImage(model = av, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(
                        (reviewer?.fullName ?: "?").first().uppercaseChar().toString(),
                        color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(reviewer?.fullName ?: "Usuario", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (date.isNotBlank()) Text(date, color = BklnMuted, fontSize = 11.sp)
            }
            Text(stars, color = BklnYellow, fontSize = 12.sp, letterSpacing = 1.sp)
        }
        if (!review.comment.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(review.comment, color = BklnMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun formatReviewDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return ""
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(isoDate.substringBefore("+").take(19)) ?: return ""
        SimpleDateFormat("d MMM yyyy", Locale("es", "ES")).format(date)
    } catch (_: Exception) { "" }
}

private fun starsText(rating: Double): String {
    val full  = rating.toInt().coerceIn(0, 5)
    val empty = 5 - full
    return "★".repeat(full) + "☆".repeat(empty)
}

private fun catEmoji(category: String?): String = when (category) {
    "Ropa y Moda"     -> "👗"
    "Complementos"    -> "👜"
    "Calzado"         -> "👟"
    "Móviles"         -> "📱"
    "Electrónica"     -> "💻"
    "Hogar y Deco"    -> "🏠"
    "Vehículos"       -> "🚗"
    "Deportes"        -> "⚽"
    "Belleza y Salud" -> "💄"
    "Infantil"        -> "👶"
    "Mascotas"        -> "🐾"
    "Libros"          -> "📚"
    "Servicios"       -> "🔧"
    "Comida"          -> "🍔"
    "Arte"            -> "🎨"
    "Otros"           -> "📦"
    else              -> "📦"
}
