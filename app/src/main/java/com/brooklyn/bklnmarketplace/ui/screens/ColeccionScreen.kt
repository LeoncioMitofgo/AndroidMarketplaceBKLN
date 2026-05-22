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
import com.brooklyn.bklnmarketplace.models.Catalog
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ColeccionScreen(navController: NavController, catalogId: String) {
    var catalog  by remember { mutableStateOf<Catalog?>(null) }
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading  by remember { mutableStateOf(true) }

    LaunchedEffect(catalogId) {
        try {
            catalog = SupabaseClient.client.postgrest
                .from("catalogs")
                .select(columns = Columns.raw(
                    "id, user_id, name, description, cover_url, expires_at, active, " +
                    "profiles!catalogs_user_id_fkey(id, full_name, avatar_url, plan, username)"
                )) {
                    filter { eq("id", catalogId); eq("active", true) }
                    limit(1)
                }
                .decodeList<Catalog>()
                .firstOrNull()

            // Load catalog_products ordered, then fetch products
            val cpRows = SupabaseClient.client.postgrest
                .from("catalog_products")
                .select(columns = Columns.raw(
                    "product_id, sort_order, " +
                    "products(id, name, price, original_price, images, category, condition, views, " +
                    "profiles!products_user_id_fkey(id, full_name, avatar_url, plan))"
                )) {
                    filter { eq("catalog_id", catalogId) }
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<com.brooklyn.bklnmarketplace.models.CatalogProduct>()

            products = cpRows.mapNotNull { it.products }.filter { it.active != false }
        } catch (_: Exception) {}
        loading = false
    }

    val cat = catalog

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(BklnBgDark),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Header
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
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
                    Text(
                        cat?.name ?: "Colección",
                        color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider(color = BklnStroke)
            }
        }

        if (loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BklnOrange)
                }
            }
            return@LazyVerticalGrid
        }

        if (cat == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("Colección no encontrada", color = BklnMuted, fontSize = 15.sp)
                }
            }
            return@LazyVerticalGrid
        }

        // Hero
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                // Cover mosaic
                val images = products.mapNotNull { it.images?.firstOrNull() }.take(4)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    CoverMosaic(
                        coverUrl = cat.coverUrl,
                        images = images,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp))
                    )
                    // Gradient overlay at bottom
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color.Transparent, BklnBgDark)
                                )
                            )
                    )
                }

                // Info
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        cat.name,
                        color = BklnWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp
                    )
                    if (!cat.description.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(cat.description, color = BklnMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    }

                    val expired = isExpired(cat.expiresAt)
                    if (!cat.expiresAt.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (expired) BklnError.copy(alpha = 0.2f) else BklnOrange.copy(alpha = 0.15f))
                                .border(1.dp, if (expired) BklnError.copy(alpha = 0.5f) else BklnOrange.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (expired) "⏰ Colección expirada" else "⏳ Disponible hasta ${formatExpiryShort(cat.expiresAt)}",
                                color = if (expired) BklnError else BklnOrange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Seller row
                    val seller = cat.profiles
                    if (seller != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(BklnBgCard)
                                .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                                .clickable {
                                    val sid = seller.id
                                    if (!sid.isNullOrBlank()) navController.navigate("vendedor/$sid")
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val av = seller.avatarUrl
                            if (!av.isNullOrBlank()) {
                                AsyncImage(
                                    model = av, contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(BklnBgInput)
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(36.dp).clip(CircleShape).background(BklnBgInput),
                                    contentAlignment = Alignment.Center
                                ) { Text("👤", fontSize = 16.sp) }
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (seller.plan == "premium") Text("👑", fontSize = 12.sp)
                                    Text(seller.fullName ?: "Vendedor", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text("Ver tienda →", color = BklnOrange, fontSize = 11.sp)
                            }
                            Text("📦 ${products.size} producto${if (products.size != 1) "s" else ""}", color = BklnMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${products.size} PRODUCTO${if (products.size != 1) "S" else ""} EN ESTA COLECCIÓN",
                        color = BklnMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // Product grid
        if (products.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📦", fontSize = 36.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Esta colección no tiene productos aún.", color = BklnMuted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(products, key = { it.id }) { product ->
                ColProductCard(product) { navController.navigate("producto/${product.id}") }
            }
        }
    }
}

@Composable
private fun ColProductCard(product: Product, onClick: () -> Unit) {
    val isPremium = product.profiles?.plan == "premium"
    val hasOffer  = product.originalPrice != null && product.originalPrice > (product.price ?: 0.0)

    Column(
        modifier = Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(1.dp, if (isPremium) Color(0x33F5C842) else BklnStroke, RoundedCornerShape(8.dp))
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(95.dp).background(BklnBgInput),
            contentAlignment = Alignment.Center
        ) {
            val img = product.images?.firstOrNull()
            if (!img.isNullOrBlank()) {
                AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("📦", fontSize = 22.sp)
            }
            if (isPremium) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(BklnYellow.copy(alpha = 0.9f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) { Text("👑", fontSize = 8.sp) }
            } else if (hasOffer) {
                val disc = ((1.0 - (product.price ?: 0.0) / product.originalPrice!!) * 100).toInt()
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(BklnOrange.copy(alpha = 0.9f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) { Text("-$disc%", color = BklnWhite, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
            }
        }
        Column(modifier = Modifier.padding(6.dp)) {
            Text(product.name, color = BklnWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
            Spacer(Modifier.height(2.dp))
            Text(colFormatPrice(product.price), color = BklnYellow, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (hasOffer) {
                Text(colFormatPrice(product.originalPrice), color = BklnMuted, fontSize = 10.sp, textDecoration = TextDecoration.LineThrough)
            }
        }
    }
}

private fun colFormatPrice(price: Double?): String {
    if (price == null) return "Gratis"
    return "${NumberFormat.getNumberInstance(Locale("es", "ES")).format(price.toLong())} XAF"
}
