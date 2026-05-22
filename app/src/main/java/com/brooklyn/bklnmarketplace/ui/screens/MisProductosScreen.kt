package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale

@Serializable
private data class ActivePatch(val active: Boolean)

@Composable
fun MisProductosScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var products        by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading         by remember { mutableStateOf(true) }
    var plan            by remember { mutableStateOf("free") }
    var productToDelete by remember { mutableStateOf<Product?>(null) }

    suspend fun reload() {
        val user = SupabaseClient.client.auth.currentUserOrNull() ?: return
        try {
            val profile = SupabaseClient.client.postgrest
                .from("profiles")
                .select { filter { eq("id", user.id) } }
                .decodeSingleOrNull<Profile>()
            plan = profile?.plan ?: "free"
        } catch (_: Exception) {}
        try {
            products = SupabaseClient.client.postgrest
                .from("products")
                .select {
                    filter { eq("user_id", user.id) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Product>()
        } catch (_: Exception) {}
        loading = false
    }

    LaunchedEffect(Unit) { scope.launch { reload() } }

    // ── DELETE DIALOG ────────────────────────────────────────────────────────
    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            containerColor = BklnBgCard,
            title = { Text("Eliminar producto", color = BklnWhite, fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar \"${product.name}\"?\nEsta acción no se puede deshacer.", color = BklnMuted) },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = product
                    scope.launch {
                        try {
                            SupabaseClient.client.postgrest
                                .from("products")
                                .delete { filter { eq("id", toDelete.id) } }
                            products = products.filter { it.id != toDelete.id }
                        } catch (_: Exception) {}
                        productToDelete = null
                    }
                }) { Text("Eliminar", color = BklnError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Cancelar", color = BklnMuted)
                }
            }
        )
    }

    val isPremium = plan == "premium"
    val limit    = if (isPremium) Int.MAX_VALUE else 15
    val progress = if (isPremium) 0f else (products.size.toFloat() / limit).coerceIn(0f, 1f)
    val barColor = when {
        progress >= 1f   -> BklnError
        progress >= 0.8f -> BklnYellow
        else             -> BklnOrange
    }

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
                "MIS PRODUCTOS",
                color = BklnWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            if (!loading) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BklnOrange)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(products.size.toString(), color = BklnWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(BklnOrange)
                    .clickable { navController.navigate("publicar") }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("+ Nuevo", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
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
                // Limit bar — full width
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LimitBarRow(
                        count = products.size,
                        limit = limit,
                        progress = progress,
                        barColor = barColor,
                        isPremium = isPremium
                    )
                }

                if (products.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyProductsState { navController.navigate("publicar") }
                    }
                } else {
                    items(products, key = { it.id }) { product ->
                        ProductManageCard(
                            product = product,
                            onEdit = { navController.navigate("editar_producto/${product.id}") },
                            onDelete = { productToDelete = product },
                            onToggleActive = {
                                scope.launch {
                                    try {
                                        val newActive = !(product.active ?: true)
                                        SupabaseClient.client.postgrest
                                            .from("products")
                                            .update(ActivePatch(newActive)) {
                                                filter { eq("id", product.id) }
                                            }
                                        products = products.map {
                                            if (it.id == product.id) it.copy(active = newActive) else it
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    }
                    item {
                        AddProductCard { navController.navigate("publicar") }
                    }
                }
            }
        }
    }
}

// ── LIMIT BAR ────────────────────────────────────────────────────────────────

@Composable
private fun LimitBarRow(
    count: Int,
    limit: Int,
    progress: Float,
    barColor: Color,
    isPremium: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Capacidad — Plan ${if (isPremium) "Premium" else "Gratuito"}",
                color = BklnMuted,
                fontSize = 10.sp
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BklnBgInput)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(if (isPremium) 0f else progress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (isPremium) "$count / ∞" else "$count/$limit",
            color = BklnMuted,
            fontSize = 11.sp
        )
    }
}

// ── PRODUCT CARD ─────────────────────────────────────────────────────────────

@Composable
private fun ProductManageCard(
    product: Product,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    val isActive = product.active != false

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
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
                Text("📦", fontSize = 24.sp)
            }

            // Status badge — tap to toggle
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isActive) BklnSuccess else BklnBgGray3)
                    .clickable { onToggleActive() }
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    if (isActive) "ACTIVO" else "INACT.",
                    color = if (isActive) Color.Black else BklnMuted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 2.dp)) {
            Text(
                product.name,
                color = BklnWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatProductPrice(product.price),
                color = BklnYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = BklnMuted, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = BklnError, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ── ADD CARD ─────────────────────────────────────────────────────────────────

@Composable
private fun AddProductCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Transparent)
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("＋", fontSize = 28.sp, color = BklnMuted)
            Spacer(Modifier.height(6.dp))
            Text("Publicar producto", color = BklnMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── EMPTY STATE ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyProductsState(onPublish: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
            .padding(40.dp),
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
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BklnOrange)
                .clickable { onPublish() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text("+ Publicar producto", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatProductPrice(price: Double?): String {
    if (price == null) return ""
    val fmt = NumberFormat.getNumberInstance(Locale("es", "ES"))
    return "${fmt.format(price.toLong())} XAF"
}
