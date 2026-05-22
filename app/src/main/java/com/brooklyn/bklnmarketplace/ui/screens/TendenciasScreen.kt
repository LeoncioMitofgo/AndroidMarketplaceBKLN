package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.R
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Catalog
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun TendenciasScreen(navController: NavController) {

    var mostViewed  by remember { mutableStateOf<List<Product>>(emptyList()) }
    var catalogs    by remember { mutableStateOf<List<Catalog>>(emptyList()) }
    var topSellers  by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var loading     by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val cols = "id, name, price, original_price, images, category, condition, views, " +
                       "profiles!products_user_id_fkey(id, full_name, avatar_url, plan)"

            // Más vistos
            mostViewed = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw(cols)) {
                    filter { eq("active", true); gt("views", 0) }
                    order("views", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<Product>()

            // Colecciones premium
            val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date())
            val allCatalogs = SupabaseClient.client.postgrest
                .from("catalogs")
                .select(columns = Columns.raw(
                    "id, name, description, cover_url, expires_at, " +
                    "profiles!catalogs_user_id_fkey(id, full_name, username, avatar_url, plan), " +
                    "catalog_products(product_id, products(name, images))"
                )) {
                    filter { eq("active", true) }
                    limit(50)
                }
                .decodeList<Catalog>()
            catalogs = allCatalogs
                .filter { c ->
                    c.profiles?.plan == "premium" &&
                    (c.expiresAt == null || c.expiresAt > nowStr)
                }
                .sortedByDescending { c -> c.catalogProducts?.size ?: 0 }
                .take(16)

            // Vendedores populares
            topSellers = SupabaseClient.client.postgrest
                .from("profiles")
                .select(columns = Columns.raw(
                    "id, full_name, username, avatar_url, plan, rating, review_count, followers_count"
                )) {
                    order("followers_count", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<Profile>()
                .filter { it.plan == "premium" && ((it.followersCount ?: 0) > 0 || (it.reviewCount ?: 0) > 0) }
                .take(12)

        } catch (_: Exception) {}
        loading = false
    }

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {

        // ── HEADER ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = BklnWhite)
            }
            Icon(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("BKLN ", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("TENDENCIAS", color = BklnOrange, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        HorizontalDivider(color = BklnStroke)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {

            // ── HERO ─────────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF1A0A05), BklnBgCard))
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Column {
                        Text(
                            "// lo más popular ahora",
                            color = BklnOrange, fontSize = 10.sp,
                            letterSpacing = 2.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("TENDENCIAS", color = BklnWhite, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text("DEL MOMENTO.", color = BklnOrange, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Colecciones premium, productos más vistos y vendedores que están marcando tendencia.",
                            color = BklnMuted, fontSize = 13.sp, lineHeight = 18.sp
                        )
                    }
                }
            }

            // ── PRODUCTOS MÁS VISTOS ──────────────────────────────────────────
            if (mostViewed.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    TrendSectionHeader("// los más buscados esta semana", "🔥 PRODUCTOS MÁS VISTOS")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(mostViewed, key = { it.id }) { product ->
                            TrendProductCard(
                                product = product,
                                showViews = true,
                                onClick = { navController.navigate("producto/${product.id}") }
                            )
                        }
                    }
                }
            }

            // ── COLECCIONES PREMIUM ───────────────────────────────────────────
            if (catalogs.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    TrendSectionHeader("// vendedores premium", "👑 COLECCIONES PREMIUM")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(catalogs, key = { it.id }) { catalog ->
                            TrendCatalogCard(catalog) {
                                navController.navigate("coleccion/${catalog.id}")
                            }
                        }
                    }
                }
            }

            // ── VENDEDORES POPULARES ──────────────────────────────────────────
            if (topSellers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    TrendSectionHeader("// más seguidos en la comunidad", "⭐ VENDEDORES POPULARES")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(topSellers) { seller ->
                            TrendSellerCard(seller) {
                                val id = seller.id
                                if (!id.isNullOrBlank()) navController.navigate("vendedor/$id")
                            }
                        }
                    }
                }
            }

            // ── EMPTY STATE ───────────────────────────────────────────────────
            if (mostViewed.isEmpty() && catalogs.isEmpty() && topSellers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔥", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("Aún no hay tendencias", color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Text("Vuelve pronto para ver los productos más populares.", color = BklnMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── COMPOSABLES ───────────────────────────────────────────────────────────────

@Composable
private fun TrendSectionHeader(label: String, title: String) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(label, color = BklnOrange, fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(title, color = BklnWhite, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun TrendProductCard(product: Product, showViews: Boolean, onClick: () -> Unit) {
    val isPremium = product.profiles?.plan == "premium"
    val hasOffer  = product.originalPrice != null && product.originalPrice > (product.price ?: 0.0)

    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(1.dp, if (isPremium) Color(0x33F5C842) else BklnStroke, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(BklnBgInput),
            contentAlignment = Alignment.Center
        ) {
            val img = product.images?.firstOrNull()
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = img, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(trendCatEmoji(product.category), fontSize = 28.sp)
            }
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart).padding(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BklnYellow.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) { Text("👑", fontSize = 8.sp) }
            } else if (hasOffer) {
                val disc = ((1.0 - (product.price ?: 0.0) / product.originalPrice!!) * 100).toInt()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart).padding(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BklnOrange.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) { Text("-$disc%", color = BklnWhite, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                product.name,
                color = BklnWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                trendFormatPrice(product.price),
                color = BklnYellow, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
            )
            if (hasOffer) {
                Text(
                    trendFormatPrice(product.originalPrice),
                    color = BklnMuted, fontSize = 10.sp,
                    textDecoration = TextDecoration.LineThrough
                )
            }
            if (showViews && (product.views ?: 0) > 0) {
                Spacer(Modifier.height(3.dp))
                Text("👁 ${product.views} vistas", color = BklnOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TrendSellerCard(seller: Profile, onClick: () -> Unit) {
    val isPremium = seller.plan == "premium"
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(1.dp, if (isPremium) Color(0x33F5C842) else BklnStroke, RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val avatar = seller.avatarUrl
        if (!avatar.isNullOrBlank()) {
            AsyncImage(
                model = avatar, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(CircleShape).background(BklnBgInput)
            )
        } else {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(BklnBgInput),
                contentAlignment = Alignment.Center
            ) { Text("👤", fontSize = 22.sp) }
        }
        Text(
            seller.fullName ?: seller.username ?: "Vendedor",
            color = BklnWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (isPremium) Text("👑 Premium", color = BklnYellow, fontSize = 9.sp)
        val followers = seller.followersCount ?: 0
        if (followers > 0) {
            Text("$followers seguidores", color = BklnMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val r = seller.rating ?: 0.0
        if (r > 0) Text("★ ${"%.1f".format(r)}", color = BklnYellow, fontSize = 10.sp)
    }
}

@Composable
private fun TrendCatalogCard(catalog: Catalog, onClick: () -> Unit) {
    val seller = catalog.profiles
    val images = catalog.catalogProducts
        ?.mapNotNull { cp -> cp.products?.images?.firstOrNull() }
        ?.filter { it.isNotBlank() }
        ?.take(4)
        ?: emptyList()
    val productCount = catalog.catalogProducts?.size ?: 0

    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(1.dp, Color(0x33F5C842), RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(BklnBgInput)
        ) {
            when {
                !catalog.coverUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = catalog.coverUrl, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                images.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("📁", fontSize = 28.sp)
                    }
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.weight(1f)) {
                            AsyncImage(
                                model = images.getOrNull(0), contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            if (images.size > 1) AsyncImage(
                                model = images.getOrNull(1), contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) else Box(Modifier.weight(1f).fillMaxHeight().background(BklnBgDark))
                        }
                        if (images.size > 2) Row(Modifier.weight(1f)) {
                            AsyncImage(
                                model = images.getOrNull(2), contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                            if (images.size > 3) AsyncImage(
                                model = images.getOrNull(3), contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) else Box(Modifier.weight(1f).fillMaxHeight().background(BklnBgDark))
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart).padding(5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BklnYellow.copy(alpha = 0.9f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) { Text("👑", fontSize = 8.sp) }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                catalog.name,
                color = BklnWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                seller?.fullName ?: seller?.username ?: "",
                color = BklnMuted, fontSize = 10.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (productCount > 0) {
                Text("$productCount productos", color = BklnOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

private fun trendFormatPrice(price: Double?): String {
    if (price == null) return "Gratis"
    return "${NumberFormat.getNumberInstance(Locale("es", "ES")).format(price.toLong())} XAF"
}

private fun trendCatEmoji(category: String?): String = when (category) {
    "Ropa y Moda"     -> "👗"
    "Complementos"    -> "👜"
    "Calzado"         -> "👟"
    "Móviles"         -> "📱"
    "Electrónica"     -> "💻"
    "Hogar y Deco"    -> "🏠"
    "Vehículos"       -> "🚗"
    "Deportes"        -> "⚽"
    "Belleza y Salud" -> "💄"
    "Videojuegos"     -> "🎮"
    "Libros"          -> "📚"
    "Arte y Colección"-> "🎨"
    else              -> "📦"
}
