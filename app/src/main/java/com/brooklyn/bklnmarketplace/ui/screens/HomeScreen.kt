package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.R
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.text.NumberFormat
import java.util.Locale

private val HOME_CATEGORIES = listOf(
    "👗" to "Ropa y Moda",
    "👜" to "Complementos",
    "👟" to "Calzado",
    "📱" to "Móviles",
    "💻" to "Electrónica",
    "🏠" to "Hogar y Deco",
    "🚗" to "Vehículos",
    "⚽" to "Deportes",
    "💄" to "Belleza y Salud",
    "🎮" to "Videojuegos",
    "📚" to "Libros",
    "🎨" to "Arte y Colección",
)

private data class FanLayout(
    val rotZ: Float, val txDp: Float, val tyDp: Float, val scale: Float, val alpha: Float, val zOrd: Float
)

private val FAN_LAYOUTS = listOf(
    FanLayout(-22f, -88f,  6f, 0.78f, 0.70f, 1f),
    FanLayout(-11f, -48f,  0f, 0.90f, 0.90f, 2f),
    FanLayout(  0f,   0f, -4f, 1.00f, 1.00f, 5f),
    FanLayout( 11f,  48f,  0f, 0.90f, 0.90f, 2f),
    FanLayout( 22f,  88f,  6f, 0.78f, 0.70f, 1f),
)

@Composable
fun HomeScreen(navController: NavController) {

    var userProfile      by remember { mutableStateOf<Profile?>(null) }
    var recentProducts   by remember { mutableStateOf<List<Product>>(emptyList()) }
    var offerProducts    by remember { mutableStateOf<List<Product>>(emptyList()) }
    var featuredProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading          by remember { mutableStateOf(true) }
    var currentUserId    by remember { mutableStateOf("") }
    var activeTab        by rememberSaveable { mutableStateOf("recientes") }

    LaunchedEffect(Unit) {
        currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""
        try {
            if (currentUserId.isNotBlank()) {
                userProfile = SupabaseClient.client.postgrest
                    .from("profiles")
                    .select(columns = Columns.raw("id, full_name, username, avatar_url, plan")) {
                        filter { eq("id", currentUserId) }; limit(1)
                    }
                    .decodeList<Profile>()
                    .firstOrNull()
            }
            val allProducts = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw(
                    "id, name, price, original_price, images, category, condition, user_id, " +
                    "created_at, views, active, " +
                    "profiles!products_user_id_fkey(id, full_name, avatar_url, plan)"
                )) {
                    filter { eq("active", true) }
                    order("created_at", Order.DESCENDING)
                    limit(40)
                }
                .decodeList<Product>()

            recentProducts   = allProducts.take(16)
            offerProducts    = allProducts.filter { it.originalPrice != null }.take(12)
            featuredProducts = allProducts.filter { it.profiles?.plan == "premium" }.take(12)
        } catch (_: Exception) {}
        loading = false
    }

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {

        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xEC0A0A0A))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Icon(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(28.dp)
                )
                Row {
                    Text("BKLN ", color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    Text("MKT", color = BklnOrange, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                }
            }
            if (currentUserId.isBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BklnOrange)
                        .clickable { navController.navigate("login") }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) { Text("Entrar →", color = BklnWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            } else {
                val avatar = userProfile?.avatarUrl
                if (!avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = avatar, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(34.dp).clip(CircleShape)
                            .border(1.dp, BklnOrange, CircleShape)
                            .clickable { navController.navigate("perfil") }
                    )
                } else {
                    Box(
                        modifier = Modifier.size(34.dp).clip(CircleShape).background(BklnOrange)
                            .clickable { navController.navigate("perfil") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            (userProfile?.fullName?.firstOrNull() ?: userProfile?.username?.firstOrNull() ?: '?')
                                .uppercaseChar().toString(),
                            color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = BklnStroke.copy(alpha = 0.4f))

        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
        } else {

        val tabProducts = when (activeTab) {
            "ofertas" -> offerProducts
            "premium" -> featuredProducts
            else      -> recentProducts
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 32.dp)) {

            // ── HERO + FAN STACK ──────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF0A0A0A), Color(0xFF1A1612), Color(0xFF0A0A0A)))
                        )
                ) {
                    // Radial orange glow (top-left)
                    Box(
                        modifier = Modifier
                            .size(320.dp)
                            .offset(x = (-80).dp, y = (-60).dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(BklnOrange.copy(alpha = 0.18f), Color.Transparent)
                                )
                            )
                    )
                    // Ghost watermark
                    Text(
                        "BKLN",
                        color = Color.White.copy(alpha = 0.03f),
                        fontSize = 90.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 56.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Eyebrow
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                "// edición de hoy",
                                color = BklnOrange, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            if (currentUserId.isBlank()) {
                                Text("LO MEJOR", color = BklnWhite, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, lineHeight = 32.sp)
                                Text("DE BKLN", color = BklnWhite, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, lineHeight = 32.sp)
                                Text("MARKETPLACE.", color = BklnOrange, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, lineHeight = 32.sp)
                            } else {
                                val name = userProfile?.fullName?.split(" ")?.firstOrNull() ?: userProfile?.username ?: "ahí"
                                Text("HOLA,", color = BklnWhite, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, lineHeight = 30.sp)
                                Text(name.uppercase(), color = BklnOrange, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, lineHeight = 30.sp)
                                Text("¿QUÉ BUSCAS?", color = BklnWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, lineHeight = 22.sp)
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── FAN CARD STACK ────────────────────────────────────
                        val heroProducts = recentProducts.take(5)
                        if (heroProducts.isNotEmpty()) {
                            val density = LocalDensity.current
                            Box(
                                modifier = Modifier.fillMaxWidth().height(190.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                heroProducts.forEachIndexed { i, product ->
                                    val L = FAN_LAYOUTS[i]
                                    Box(
                                        modifier = Modifier
                                            .width(115.dp)
                                            .zIndex(L.zOrd)
                                            .graphicsLayer {
                                                rotationZ  = L.rotZ
                                                translationX = with(density) { L.txDp.dp.toPx() }
                                                translationY = with(density) { L.tyDp.dp.toPx() }
                                                scaleX = L.scale
                                                scaleY = L.scale
                                                alpha  = L.alpha
                                            }
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BklnBgCard)
                                            .border(1.dp, BklnStroke, RoundedCornerShape(10.dp))
                                            .clickable { navController.navigate("producto/${product.id}") }
                                    ) {
                                        Column {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(90.dp).background(BklnBgInput),
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
                                                    Text(homeCatEmoji(product.category), fontSize = 26.sp)
                                                }
                                            }
                                            Column(modifier = Modifier.padding(6.dp, 4.dp, 6.dp, 6.dp)) {
                                                Text(
                                                    product.name,
                                                    color = BklnWhite, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 11.sp
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    homeFormatPrice(product.price),
                                                    color = BklnYellow, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(190.dp))
                        }

                        // ── CTA BUTTONS ───────────────────────────────────────
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BklnOrange)
                                    .clickable { navController.navigate("productos") }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("Explorar →", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Transparent)
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .clickable { navController.navigate("publicar") }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) { Text("+ Publicar", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // ── SEARCH BAR ────────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BklnBgCard)
                        .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
                        .clickable { navController.navigate("productos") }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🔍", fontSize = 16.sp)
                        Text("iPhone, sofá, Jordan...", color = BklnMuted, fontSize = 14.sp)
                    }
                }
            }

            // ── CATEGORY PILLS ────────────────────────────────────────────────
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(HOME_CATEGORIES) { (emoji, name) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(BklnBgCard)
                                .border(1.dp, BklnStroke, RoundedCornerShape(999.dp))
                                .clickable {
                                    val encoded = java.net.URLEncoder.encode(name, "UTF-8")
                                    navController.navigate("productos?cat=$encoded")
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(emoji, fontSize = 13.sp)
                                Text(
                                    name.split(" ").firstOrNull() ?: name,
                                    color = BklnWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // ── TAB STRIP ─────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    listOf(
                        "recientes" to "Recientes",
                        "ofertas"   to "🏷️ Ofertas",
                        "premium"   to "👑 Premium",
                    ).forEach { (id, label) ->
                        val isActive = activeTab == id
                        Box(
                            modifier = Modifier
                                .clickable { activeTab = id }
                                .padding(end = 16.dp, bottom = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    label,
                                    color = if (isActive) BklnWhite else BklnMuted,
                                    fontSize = 13.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    letterSpacing = 0.3.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .height(2.dp)
                                        .width(if (isActive) 28.dp else 0.dp)
                                        .background(BklnOrange)
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = BklnStroke.copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
            }

            // ── PRODUCT GRID ──────────────────────────────────────────────────
            if (tabProducts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Sin productos por ahora", color = BklnMuted, fontSize = 13.sp) }
                }
            } else {
                items(tabProducts.chunked(2)) { pair ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { product ->
                            HomeGridCard(
                                product = product,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate("producto/${product.id}") }
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // ── MARQUEE TICKER ────────────────────────────────────────────────
            if (offerProducts.isNotEmpty()) {
                item { HomeMarquee() }
            }

            // ── TENDENCIAS BANNER ────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF2A0D00), Color(0xFF1A1A1A))))
                        .border(1.dp, BklnOrange.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { navController.navigate("tendencias") }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🔥", fontSize = 24.sp)
                            Column {
                                Text("TENDENCIAS DEL MOMENTO", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                                Text("Lo más visto · Premium · Vendedores top", color = BklnMuted, fontSize = 11.sp)
                            }
                        }
                        Text("→", color = BklnOrange, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── PUBLISH CTA ──────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF2A1000), Color(0xFF1E1E1E))))
                        .border(1.dp, BklnOrange.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(20.dp)
                ) {
                    Column {
                        Text("¿TIENES ALGO", color = BklnWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Text("QUE VENDER?", color = BklnOrange, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Publica gratis en minutos.", color = BklnMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BklnOrange)
                                .clickable { navController.navigate("publicar") }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) { Text("Publicar ahora →", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        } // end else (!loading)
    }
}

// ── FAN CARD COMPOSABLES ──────────────────────────────────────────────────────

@Composable
private fun HomeGridCard(product: Product, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isPremium = product.profiles?.plan == "premium"
    val hasOffer  = product.originalPrice != null && product.originalPrice > (product.price ?: 0.0)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(1.dp, if (isPremium) Color(0x33F5C842) else BklnStroke, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(BklnBgInput),
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
                Text(homeCatEmoji(product.category), fontSize = 32.sp)
            }
            if (isPremium) {
                Box(
                    Modifier.align(Alignment.TopStart).padding(5.dp)
                        .clip(RoundedCornerShape(2.dp)).background(BklnYellow.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) { Text("👑", fontSize = 8.sp) }
            } else if (hasOffer) {
                val disc = ((1.0 - (product.price ?: 0.0) / product.originalPrice!!) * 100).toInt()
                Box(
                    Modifier.align(Alignment.TopStart).padding(5.dp)
                        .clip(RoundedCornerShape(2.dp)).background(BklnOrange.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) { Text("-$disc%", color = BklnWhite, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Column(modifier = Modifier.padding(9.dp)) {
            Text(
                product.name,
                color = BklnWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(homeFormatPrice(product.price), color = BklnYellow, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            if (hasOffer) {
                Text(
                    homeFormatPrice(product.originalPrice),
                    color = BklnMuted, fontSize = 10.sp,
                    textDecoration = TextDecoration.LineThrough
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeMarquee() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BklnYellow)
            .padding(vertical = 7.dp)
    ) {
        Text(
            text = "  🏷️ OFERTAS DEL DÍA  •  SOLO POR TIEMPO LIMITADO  •  🏷️ OFERTAS DEL DÍA  •  SOLO POR TIEMPO LIMITADO  •  ",
            color = Color.Black,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(iterations = Int.MAX_VALUE)
        )
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

private fun homeFormatPrice(price: Double?): String {
    if (price == null) return "Gratis"
    return "${NumberFormat.getNumberInstance(Locale("es", "ES")).format(price.toLong())} XAF"
}

private fun homeCatEmoji(category: String?): String = when (category) {
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
    "Videojuegos"     -> "🎮"
    "Libros"          -> "📚"
    "Arte y Colección"-> "🎨"
    else              -> "📦"
}
