package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import io.github.jan.supabase.postgrest.query.Count
import java.text.NumberFormat
import java.util.Locale

private val PLAN_LIMIT_FREE = 15
private val PLAN_LIMIT_PREMIUM = Int.MAX_VALUE

@Composable
fun ResumenScreen(navController: NavController) {
    var productCount   by remember { mutableStateOf(0) }
    var totalViews     by remember { mutableStateOf(0) }
    var plan           by remember { mutableStateOf("free") }
    var memberSince    by remember { mutableStateOf("—") }
    var rating         by remember { mutableStateOf<Double?>(null) }
    var reviewCount    by remember { mutableStateOf(0) }
    var recentProducts by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading        by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val user = SupabaseClient.client.auth.currentUserOrNull() ?: return@LaunchedEffect

        // Member since
        memberSince = user.createdAt?.toString()?.take(10) ?: "—"

        // Plan desde profiles
        try {
            val profile = SupabaseClient.client.postgrest
                .from("profiles")
                .select { filter { eq("id", user.id) } }
                .decodeSingleOrNull<Profile>()
            plan        = profile?.plan ?: "free"
            rating      = profile?.rating
            reviewCount = profile?.reviewCount ?: 0
        } catch (_: Exception) {}

        // Productos del usuario — últimos 6 para "publicaciones recientes"
        try {
            val products = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw("id, name, price, images, category, views, active, created_at")) {
                    filter { eq("user_id", user.id) }
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<Product>()
            productCount  = products.size
            totalViews    = products.sumOf { it.views ?: 0 }
            recentProducts = products.take(6)
        } catch (_: Exception) {}

        loading = false
    }

    val isPremium   = plan == "premium"
    val limit       = if (isPremium) PLAN_LIMIT_PREMIUM else PLAN_LIMIT_FREE
    val limitLabel  = if (isPremium) "Ilimitado" else "de $limit disponibles"
    val limitText   = if (isPremium) "$productCount / ∞" else "$productCount/$limit"
    val progress    = if (isPremium) 0f else (productCount.toFloat() / limit).coerceIn(0f, 1f)
    val barColor    = when {
        progress >= 1f   -> BklnError
        progress >= 0.8f -> BklnYellow
        else             -> BklnOrange
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BklnBgDark)
    ) {
        // ── TOPBAR ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(BklnBgCard)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = BklnWhite)
            }
            Text(
                "RESUMEN",
                color = BklnWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            Text(memberSince, color = BklnMuted, fontSize = 11.sp, modifier = Modifier.padding(end = 12.dp))
        }

        HorizontalDivider(color = BklnStroke)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // ── STATS GRID ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatCard(
                        label = "Productos publicados",
                        value = productCount.toString(),
                        sub = limitLabel,
                        accentColor = BklnOrange,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Visitas recibidas",
                        value = if (totalViews > 0) totalViews.toString() else "—",
                        sub = "visitas totales",
                        accentColor = BklnSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Rating vendedor",
                        value = if (rating != null && rating!! > 0) "${"%.1f".format(rating)}★" else "—",
                        sub = if (reviewCount > 0) "$reviewCount reseña${if (reviewCount != 1) "s" else ""}" else "sin reseñas aún",
                        accentColor = BklnYellow,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(14.dp))

                // ── BARRA DE LÍMITE ──────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BklnBgCard)
                        .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Capacidad de publicación — Plan ${if (isPremium) "Premium" else "Gratuito"}",
                            color = BklnMuted,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(BklnBgInput)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progress)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(barColor)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(limitText, color = BklnMuted, fontSize = 12.sp)
                        if (!isPremium) {
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BklnOrange)
                                    .clickable {}
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text("Ampliar", color = BklnWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── PUBLICACIONES RECIENTES ──────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Publicaciones recientes",
                        color = BklnWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Ver todas →",
                        color = BklnMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { navController.navigate("mis_productos") }
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (recentProducts.isEmpty()) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnBgCard)
                            .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("📦", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Aún no tienes productos", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Publica tu primer producto gratis y empieza a vender hoy.",
                            color = BklnMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth(0.8f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BklnOrange)
                                .clickable { navController.navigate("publicar") }
                                .padding(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text("+ Publicar producto", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // Grid 2 columnas con los últimos productos
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        recentProducts.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { product ->
                                    MiniProductCard(product, Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                // ── UPGRADE BANNER (solo free) ───────────────────────────
                if (!isPremium) {
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Color(0x14E8431A)
                            )
                            .border(1.dp, Color(0x33E8431A), RoundedCornerShape(6.dp))
                            .padding(20.dp)
                    ) {
                        Column {
                            Row {
                                Text("¿LISTO PARA MÁS? ", color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                Text("HAZTE PREMIUM", color = BklnOrange, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Ofertas · Colecciones · Badge · Prioridad absoluta — 10,000 XAF/mes",
                                color = BklnMuted,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BklnOrange)
                                    .clickable {}
                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Text("Ver planes →", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    sub: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
    ) {
        // Accent bar izquierda
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Column(modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 8.dp, bottom = 12.dp)) {
            Text(label, color = BklnMuted, fontSize = 9.sp, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = BklnWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(2.dp))
            Text(sub, color = BklnMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MiniProductCard(product: Product, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
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
                Text("📦", fontSize = 28.sp)
            }
            // Badge activo/inactivo
            val isActive = product.active != false
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) BklnSuccess else BklnBgGray3)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    if (isActive) "ACTIVO" else "INACTIVO",
                    color = if (isActive) Color.Black else BklnMuted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                product.name,
                color = BklnWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatPriceMini(product.price),
                color = BklnYellow,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if ((product.views ?: 0) > 0) {
                Text("${product.views} visitas", color = BklnMuted, fontSize = 10.sp)
            }
        }
    }
}

private fun formatPriceMini(price: Double?): String {
    if (price == null) return ""
    val fmt = NumberFormat.getNumberInstance(Locale("es", "ES"))
    return "${fmt.format(price.toLong())} XAF"
}
