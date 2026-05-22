package com.brooklyn.bklnmarketplace.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.SupabaseClient
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

// ── PRIVATE DATA CLASSES ──────────────────────────────────────────────────────

@Serializable
private data class ProductoComment(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("product_id") val productId: String = "",
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null,
)

@Serializable
private data class CommentInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
    val content: String,
)

@Serializable
private data class FavRow(
    @SerialName("user_id") val userId: String = "",
    @SerialName("product_id") val productId: String = "",
)

@Serializable
private data class FavInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("product_id") val productId: String,
)

@Serializable
private data class ProductReportInsert(
    @SerialName("reporter_id") val reporterId: String,
    @SerialName("product_id") val productId: String,
    val reason: String,
    val description: String? = null,
    val status: String = "pending",
)

private val PRODUCT_REPORT_REASONS = listOf(
    "Producto falso o engañoso",
    "Precio abusivo",
    "Contenido inapropiado",
    "Producto prohibido",
    "Spam o duplicado",
    "Información incorrecta",
    "Otro"
)

// ── SCREEN ────────────────────────────────────────────────────────────────────

@Composable
fun ProductoScreen(navController: NavController, productId: String) {
    val scope        = rememberCoroutineScope()
    val context      = LocalContext.current
    val focusManager = LocalFocusManager.current

    var product    by remember { mutableStateOf<Product?>(null) }
    var comments   by remember { mutableStateOf<List<ProductoComment>>(emptyList()) }
    var related    by remember { mutableStateOf<List<Product>>(emptyList()) }
    var moreSeller by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading    by remember { mutableStateOf(true) }

    var isFav          by remember { mutableStateOf(false) }
    var favLoading     by remember { mutableStateOf(false) }
    var commentInput   by remember { mutableStateOf("") }
    var commentLoading by remember { mutableStateOf(false) }
    var currentUserId  by remember { mutableStateOf("") }
    var galleryIndex   by remember { mutableIntStateOf(0) }

    var showReportDialog  by remember { mutableStateOf(false) }
    var reportReason      by remember { mutableStateOf("") }
    var reportDescription by remember { mutableStateOf("") }
    var reportSubmitting  by remember { mutableStateOf(false) }
    var reportSent        by remember { mutableStateOf(false) }

    LaunchedEffect(productId) {
        currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""

        try {
            val p = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw(
                    "id, name, price, original_price, description, category, images, " +
                    "condition, location, views, active, created_at, user_id, " +
                    "profiles!products_user_id_fkey(id, full_name, avatar_url, plan, rating, review_count, whatsapp)"
                )) {
                    filter { eq("id", productId) }
                    limit(1)
                }
                .decodeList<Product>()
                .firstOrNull()
            product = p

            // Comments
            val loadedComments = SupabaseClient.client.postgrest
                .from("product_comments")
                .select(columns = Columns.raw(
                    "id, user_id, product_id, content, created_at, " +
                    "profiles!product_comments_user_id_fkey(full_name, avatar_url)"
                )) {
                    filter { eq("product_id", productId) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<ProductoComment>()
            comments = loadedComments

            // Favorites
            if (currentUserId.isNotBlank()) {
                val favs = SupabaseClient.client.postgrest
                    .from("favorites")
                    .select { filter { eq("user_id", currentUserId); eq("product_id", productId) } }
                    .decodeList<FavRow>()
                isFav = favs.isNotEmpty()
            }

            // Related products
            val cat = p?.category
            if (!cat.isNullOrBlank()) {
                related = SupabaseClient.client.postgrest
                    .from("products")
                    .select(columns = Columns.raw(
                        "id, name, price, original_price, images, category, condition, user_id, views, " +
                        "profiles!products_user_id_fkey(id, full_name, avatar_url, plan)"
                    )) {
                        filter {
                            eq("category", cat)
                            eq("active", true)
                            neq("id", productId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(8)
                    }
                    .decodeList<Product>()
            }

            // More from seller
            val sellerId = p?.userId
            if (!sellerId.isNullOrBlank()) {
                moreSeller = SupabaseClient.client.postgrest
                    .from("products")
                    .select(columns = Columns.raw(
                        "id, name, price, original_price, images, category, condition, user_id, views, " +
                        "profiles!products_user_id_fkey(id, full_name, avatar_url, plan)"
                    )) {
                        filter {
                            eq("user_id", sellerId)
                            eq("active", true)
                            neq("id", productId)
                        }
                        order("created_at", Order.DESCENDING)
                        limit(8)
                    }
                    .decodeList<Product>()
            }
        } catch (_: Exception) {}
        loading = false
    }

    fun toggleFav() {
        if (currentUserId.isBlank()) { navController.navigate("login"); return }
        if (favLoading) return
        scope.launch {
            favLoading = true
            try {
                if (isFav) {
                    SupabaseClient.client.postgrest.from("favorites").delete {
                        filter { eq("user_id", currentUserId); eq("product_id", productId) }
                    }
                    isFav = false
                } else {
                    SupabaseClient.client.postgrest.from("favorites")
                        .insert(FavInsert(userId = currentUserId, productId = productId))
                    isFav = true
                }
            } catch (_: Exception) {}
            favLoading = false
        }
    }

    fun submitComment() {
        val text = commentInput.trim()
        if (text.isBlank() || commentLoading) return
        if (currentUserId.isBlank()) { navController.navigate("login"); return }
        scope.launch {
            commentLoading = true
            focusManager.clearFocus()
            try {
                SupabaseClient.client.postgrest.from("product_comments")
                    .insert(CommentInsert(userId = currentUserId, productId = productId, content = text))
                commentInput = ""
                // Reload comments
                comments = SupabaseClient.client.postgrest
                    .from("product_comments")
                    .select(columns = Columns.raw(
                        "id, user_id, product_id, content, created_at, " +
                        "profiles!product_comments_user_id_fkey(full_name, avatar_url)"
                    )) {
                        filter { eq("product_id", productId) }
                        order("created_at", Order.DESCENDING)
                    }
                    .decodeList<ProductoComment>()
            } catch (_: Exception) {}
            commentLoading = false
        }
    }

    fun submitReport() {
        if (reportReason.isBlank() || reportSubmitting) return
        scope.launch {
            reportSubmitting = true
            try {
                val existing = SupabaseClient.client.postgrest
                    .from("reports")
                    .select { filter { eq("reporter_id", currentUserId); eq("product_id", productId) } }
                    .decodeList<ProductReportInsert>()
                if (existing.isEmpty()) {
                    SupabaseClient.client.postgrest.from("reports").insert(
                        ProductReportInsert(
                            reporterId = currentUserId,
                            productId = productId,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BklnBgDark)
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = BklnOrange,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val p = product
            if (p == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Producto no encontrado", color = BklnMuted, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Volver", color = BklnOrange)
                    }
                }
            } else {
                val images = p.images?.filter { it.isNotBlank() } ?: emptyList()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // ── GALLERY ──────────────────────────────────────────────
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(BklnBgInput)
                        ) {
                            if (images.isNotEmpty()) {
                                AsyncImage(
                                    model = images[galleryIndex.coerceIn(0, images.lastIndex)],
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    productCatEmoji(p.category),
                                    fontSize = 60.sp,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            // Back button
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.ArrowBack, "Volver", tint = BklnWhite)
                            }

                            // Fav + Share buttons
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = { toggleFav() },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        "Favorito",
                                        tint = if (isFav) BklnOrange else BklnWhite
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "¡Mira este producto! ${p.name}")
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    },
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(Icons.Default.Share, "Compartir", tint = BklnWhite)
                                }
                            }

                            // Nav arrows
                            if (images.size > 1) {
                                if (galleryIndex > 0) {
                                    IconButton(
                                        onClick = { galleryIndex-- },
                                        modifier = Modifier
                                            .align(Alignment.CenterStart)
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, "Anterior", tint = BklnWhite)
                                    }
                                }
                                if (galleryIndex < images.lastIndex) {
                                    IconButton(
                                        onClick = { galleryIndex++ },
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(4.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    ) {
                                        Icon(Icons.Default.ChevronRight, "Siguiente", tint = BklnWhite)
                                    }
                                }

                                // Counter
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "${galleryIndex + 1}/${images.size}",
                                        color = BklnWhite,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Thumbnail row
                    if (images.size > 1) {
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BklnBgCard)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(images) { i, url ->
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .border(
                                                1.5.dp,
                                                if (i == galleryIndex) BklnOrange else BklnStroke,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { galleryIndex = i }
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── PRODUCT INFO ─────────────────────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Badges row
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (!p.category.isNullOrBlank()) {
                                    Badge(label = p.category, color = BklnOrange)
                                }
                                if (!p.condition.isNullOrBlank()) {
                                    val (condLabel, condColor) = conditionInfo(p.condition)
                                    Badge(label = condLabel, color = condColor)
                                }
                            }

                            Text(
                                p.name,
                                color = BklnWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 26.sp
                            )

                            // Price
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    productFormatPrice(p.price),
                                    color = BklnYellow,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (p.originalPrice != null && p.originalPrice > (p.price ?: 0.0)) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        productFormatPrice(p.originalPrice),
                                        color = BklnMuted,
                                        fontSize = 16.sp,
                                        textDecoration = TextDecoration.LineThrough
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    val discount = ((1.0 - (p.price ?: 0.0) / p.originalPrice) * 100).toInt()
                                    Badge(label = "-$discount%", color = BklnSuccess)
                                }
                            }

                            if ((p.views ?: 0) > 0) {
                                Text("👁 ${p.views} visualizaciones", color = BklnMuted, fontSize = 12.sp)
                            }

                            if (!p.description.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    p.description,
                                    color = BklnWhite,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    // ── META CARD ────────────────────────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BklnBgCard)
                                .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!p.condition.isNullOrBlank()) {
                                val (label, _) = conditionInfo(p.condition)
                                MetaRow(label = "Estado", value = label)
                            }
                            if (!p.location.isNullOrBlank()) {
                                MetaRow(label = "Ubicación", value = "📍 ${p.location}")
                            }
                            if (!p.createdAt.isNullOrBlank()) {
                                MetaRow(label = "Publicado", value = productFormatDate(p.createdAt))
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // ── SELLER CARD ──────────────────────────────────────────
                    item {
                        val seller = p.profiles
                        if (seller != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(BklnBgCard)
                                    .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val sellerId = seller.id
                                        if (!sellerId.isNullOrBlank()) {
                                            navController.navigate("vendedor/$sellerId")
                                        }
                                    }
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val avatarUrl = seller.avatarUrl
                                    if (!avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = avatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(BklnBgInput)
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(BklnBgInput),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("👤", fontSize = 20.sp)
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (seller.plan == "premium") {
                                                Text("👑", fontSize = 13.sp)
                                            }
                                            Text(
                                                seller.fullName ?: "Vendedor",
                                                color = BklnWhite,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        if ((seller.rating ?: 0.0) > 0) {
                                            val r = seller.rating!!
                                            Text(
                                                "${"★".repeat(r.toInt().coerceIn(0,5))}${"☆".repeat(5 - r.toInt().coerceIn(0,5))}  ${"%.1f".format(r)}",
                                                color = BklnYellow,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    Text("Ver tienda →", color = BklnOrange, fontSize = 12.sp)
                                }

                                // Enviar mensaje (in-app chat)
                                if (currentUserId.isNotBlank() && currentUserId != p.userId) {
                                    Spacer(Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(BklnOrange)
                                            .clickable { navController.navigate("chat/${p.userId}") }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "✉ Enviar mensaje",
                                            color = BklnWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // WhatsApp button
                                val wa = seller.whatsapp
                                if (!wa.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF25D366))
                                            .clickable {
                                                val number = wa.filter { it.isDigit() }
                                                val uri = Uri.parse("https://wa.me/$number")
                                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                            }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "💬 Contactar por WhatsApp",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Report button
                                if (currentUserId.isNotBlank() && currentUserId != p.userId) {
                                    Spacer(Modifier.height(14.dp))
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (reportSent) "✓ Reporte enviado" else "⚑ Reportar producto",
                                            color = if (reportSent) BklnSuccess else BklnMuted,
                                            fontSize = 11.sp,
                                            modifier = if (!reportSent) Modifier.clickable { showReportDialog = true } else Modifier
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // ── COMMENTS ─────────────────────────────────────────────
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                "Comentarios (${comments.size})",
                                color = BklnWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(10.dp))

                            if (true) { // shown to all; guests redirected to login on submit
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = commentInput,
                                        onValueChange = { commentInput = it },
                                        placeholder = { Text("Escribe un comentario...", color = BklnMuted, fontSize = 13.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor   = BklnOrange,
                                            unfocusedBorderColor = BklnStroke,
                                            focusedTextColor     = BklnWhite,
                                            unfocusedTextColor   = BklnWhite,
                                            cursorColor          = BklnOrange,
                                            focusedContainerColor   = BklnBgInput,
                                            unfocusedContainerColor = BklnBgInput,
                                        ),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(onSend = { submitComment() }),
                                        maxLines = 3,
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                                    )
                                    IconButton(
                                        onClick = { submitComment() },
                                        enabled = !commentLoading && commentInput.isNotBlank(),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (commentInput.isNotBlank()) BklnOrange else BklnBgInput)
                                    ) {
                                        if (commentLoading) {
                                            CircularProgressIndicator(
                                                color = BklnWhite,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(Icons.Default.Send, "Enviar", tint = BklnWhite)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }

                    if (comments.isEmpty()) {
                        item {
                            Text(
                                "Sin comentarios aún. ¡Sé el primero!",
                                color = BklnMuted,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(comments, key = { it.id }) { comment ->
                            CommentCard(comment)
                        }
                    }

                    item { Spacer(Modifier.height(24.dp)) }

                    // ── RELATED PRODUCTS ─────────────────────────────────────
                    if (related.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    "Productos similares",
                                    color = BklnWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(related, key = { it.id }) { rel ->
                                        MiniProductCard(rel) {
                                            navController.navigate("producto/${rel.id}")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }

                    // ── MORE FROM SELLER ─────────────────────────────────────
                    if (moreSeller.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    "Más de este vendedor",
                                    color = BklnWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(10.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(moreSeller, key = { it.id }) { rel ->
                                        MiniProductCard(rel) {
                                            navController.navigate("producto/${rel.id}")
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }

                // ── REPORT DIALOG ─────────────────────────────────────────────
                if (showReportDialog) {
                    AlertDialog(
                        onDismissRequest = { showReportDialog = false },
                        containerColor = BklnBgCard,
                        titleContentColor = BklnWhite,
                        title = { Text("Reportar producto", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Motivo del reporte", color = BklnMuted, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                PRODUCT_REPORT_REASONS.forEach { reason ->
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
                                onClick = { submitReport() },
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
            }
        }
    }
}

// ── COMPOSABLES ───────────────────────────────────────────────────────────────

@Composable
private fun CommentCard(comment: ProductoComment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val avatar = comment.profiles?.avatarUrl
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BklnBgInput)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(BklnBgInput),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 14.sp)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    comment.profiles?.fullName ?: "Usuario",
                    color = BklnWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    productFormatDate(comment.createdAt),
                    color = BklnMuted,
                    fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.content, color = BklnWhite, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun MiniProductCard(product: Product, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
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
                Text(productCatEmoji(product.category), fontSize = 28.sp)
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                product.name,
                color = BklnWhite,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                productFormatPrice(product.price),
                color = BklnYellow,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun Badge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = BklnMuted, fontSize = 13.sp)
        Text(value, color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

private fun conditionInfo(condition: String?): Pair<String, Color> = when (condition) {
    "nuevo"       -> "Nuevo"       to BklnSuccess
    "como_nuevo"  -> "Como nuevo"  to Color(0xFF3DD6DC)
    "usado"       -> "Usado"       to BklnYellow
    "para_piezas" -> "Para piezas" to BklnError
    else          -> (condition ?: "Desconocido") to BklnMuted
}

private fun productFormatPrice(price: Double?): String {
    if (price == null) return "Gratis"
    val fmt = NumberFormat.getNumberInstance(Locale("es", "ES"))
    return "${fmt.format(price.toLong())} XAF"
}

private fun productFormatDate(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return ""
    return try {
        val sdf  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(isoDate.substringBefore("+").take(19)) ?: return ""
        SimpleDateFormat("d MMM yyyy", Locale("es", "ES")).format(date)
    } catch (_: Exception) { "" }
}

private fun productCatEmoji(category: String?): String = when (category) {
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
    else              -> "📦"
}
