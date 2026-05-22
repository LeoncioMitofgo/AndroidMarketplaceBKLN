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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale

@Serializable
private data class FavItem(
    @SerialName("product_id") val productId: String = "",
    val products: Product? = null,
)

@Composable
fun FavoritosScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var favProducts    by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading        by remember { mutableStateOf(true) }
    var currentUserId  by remember { mutableStateOf("") }
    var removingIds    by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun loadFavs(userId: String) {
        val items = SupabaseClient.client.postgrest
            .from("favorites")
            .select(columns = Columns.raw(
                "product_id, products(" +
                "id, name, price, original_price, images, category, condition, " +
                "user_id, views, active, " +
                "profiles!products_user_id_fkey(id, full_name, avatar_url, plan)" +
                ")"
            )) {
                filter { eq("user_id", userId) }
            }
            .decodeList<FavItem>()
        favProducts = items.mapNotNull { it.products }
    }

    LaunchedEffect(Unit) {
        currentUserId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""
        if (currentUserId.isNotBlank()) {
            try { loadFavs(currentUserId) } catch (_: Exception) {}
        }
        loading = false
    }

    fun removeFav(productId: String) {
        if (removingIds.contains(productId)) return
        scope.launch {
            removingIds = removingIds + productId
            try {
                SupabaseClient.client.postgrest.from("favorites").delete {
                    filter { eq("user_id", currentUserId); eq("product_id", productId) }
                }
                favProducts = favProducts.filter { it.id != productId }
            } catch (_: Exception) {}
            removingIds = removingIds - productId
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BklnBgDark)
    ) {
        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = BklnWhite)
            }
            Text(
                "MIS FAVORITOS",
                color = BklnWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            if (!loading) {
                Text(
                    "${favProducts.size} productos",
                    color = BklnMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BklnOrange)
                }
            }
            favProducts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❤️", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No tienes favoritos aún",
                            color = BklnWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Guarda productos tocando el corazón\ncuando los veas.",
                            color = BklnMuted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Spacer(Modifier.height(20.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BklnOrange)
                                .clickable { navController.navigate("productos") }
                                .padding(horizontal = 20.dp, vertical = 11.dp)
                        ) {
                            Text("Explorar productos", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(favProducts, key = { it.id }) { product ->
                        FavProductCard(
                            product = product,
                            removing = removingIds.contains(product.id),
                            onRemove = { removeFav(product.id) },
                            onClick  = { navController.navigate("producto/${product.id}") }
                        )
                    }
                    // bottom padding item
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ── CARD ─────────────────────────────────────────────────────────────────────

@Composable
private fun FavProductCard(
    product: Product,
    removing: Boolean,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val isPremium = product.profiles?.plan == "premium"
    val hasOffer  = product.originalPrice != null && product.originalPrice > (product.price ?: 0.0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(
                1.dp,
                if (isPremium) Color(0x33F5C842) else BklnStroke,
                RoundedCornerShape(8.dp)
            )
    ) {
        Column {
            // Image
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
                    Text(favCatEmoji(product.category), fontSize = 24.sp)
                }

                // Premium badge
                if (isPremium) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(BklnYellow.copy(alpha = 0.9f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("👑", fontSize = 9.sp)
                    }
                }
            }

            // Info
            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)) {
                if (!product.category.isNullOrBlank()) {
                    Text(
                        product.category,
                        color = BklnOrange,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(1.dp))
                }
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
                    favFormatPrice(product.price),
                    color = BklnYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (hasOffer) {
                    Text(
                        favFormatPrice(product.originalPrice),
                        color = BklnMuted,
                        fontSize = 10.sp,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    product.profiles?.fullName ?: "Vendedor",
                    color = BklnMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Remove fav button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(enabled = !removing) { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            if (removing) {
                CircularProgressIndicator(
                    color = BklnOrange,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Quitar de favoritos",
                    tint = BklnOrange,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

private fun favFormatPrice(price: Double?): String {
    if (price == null) return "Gratis"
    val fmt = NumberFormat.getNumberInstance(Locale("es", "ES"))
    return "${fmt.format(price.toLong())} XAF"
}

private fun favCatEmoji(category: String?): String = when (category) {
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
