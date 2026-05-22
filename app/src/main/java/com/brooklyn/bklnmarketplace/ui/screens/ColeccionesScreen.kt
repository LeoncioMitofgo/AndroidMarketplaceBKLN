package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.R
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Catalog
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ColeccionesScreen(navController: NavController) {
    val scope         = rememberCoroutineScope()
    val currentUserId = remember { SupabaseClient.client.auth.currentUserOrNull()?.id ?: "" }
    var catalogs      by remember { mutableStateOf<List<Catalog>>(emptyList()) }
    var isPremium     by remember { mutableStateOf(false) }
    var loading       by remember { mutableStateOf(true) }
    var deleteTarget  by remember { mutableStateOf<Catalog?>(null) }

    suspend fun reload() {
        try {
            val profile = SupabaseClient.client.postgrest
                .from("profiles")
                .select(columns = Columns.raw("plan")) {
                    filter { eq("id", currentUserId) }
                    limit(1)
                }
                .decodeSingleOrNull<com.brooklyn.bklnmarketplace.models.Profile>()
            isPremium = profile?.plan == "premium"

            if (isPremium) {
                catalogs = SupabaseClient.client.postgrest
                    .from("catalogs")
                    .select(columns = Columns.raw(
                        "id, user_id, name, description, cover_url, expires_at, active, sort_order, created_at, " +
                        "catalog_products(product_id, sort_order, products(id, name, images))"
                    )) {
                        filter { eq("user_id", currentUserId); eq("active", true) }
                        order("sort_order", Order.ASCENDING)
                    }
                    .decodeList<Catalog>()
            }
        } catch (_: Exception) {}
        loading = false
    }

    LaunchedEffect(Unit) { reload() }

    // Delete confirmation dialog
    deleteTarget?.let { cat ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = BklnBgCard,
            title = { Text("Eliminar colección", color = BklnWhite, fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar \"${cat.name}\"?\nEsta acción no se puede deshacer.", color = BklnMuted) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            SupabaseClient.client.postgrest
                                .from("catalogs")
                                .update(mapOf("active" to false)) {
                                    filter { eq("id", cat.id) }
                                }
                            catalogs = catalogs.filter { it.id != cat.id }
                        } catch (_: Exception) {}
                        deleteTarget = null
                    }
                }) { Text("Eliminar", color = BklnError, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar", color = BklnMuted) }
            }
        )
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
                contentDescription = null, tint = Color.Unspecified,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("MIS ", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("COLECCIONES", color = BklnOrange, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.weight(1f))
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BklnOrange)
                        .clickable { navController.navigate("nueva_coleccion") }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("+ Nueva", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
            }
        }
        HorizontalDivider(color = BklnStroke)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
            return@Column
        }

        if (!isPremium) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BklnBgCard)
                        .border(1.dp, Color(0x33F5C842), RoundedCornerShape(12.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("👑", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Función Premium", color = BklnYellow, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Las colecciones te permiten organizar tu catálogo y destacar tus mejores productos en una página propia.",
                        color = BklnMuted, fontSize = 13.sp, lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnYellow)
                            .clickable { navController.navigate("planes") }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Ver planes →", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            return@Column
        }

        if (catalogs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📁", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Sin colecciones aún", color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Crea tu primera colección para organizar tu catálogo.", color = BklnMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnOrange)
                            .clickable { navController.navigate("nueva_coleccion") }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) { Text("+ Crear colección", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(catalogs, key = { it.id }) { cat ->
                CatalogManageCard(
                    catalog = cat,
                    onView   = { navController.navigate("coleccion/${cat.id}") },
                    onEdit   = { navController.navigate("editar_coleccion/${cat.id}") },
                    onDelete = { deleteTarget = cat }
                )
            }
        }
    }
}

@Composable
private fun CatalogManageCard(
    catalog: Catalog,
    onView: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val prods = catalog.catalogProducts ?: emptyList()
    val images = prods.mapNotNull { it.products?.images?.firstOrNull() }.take(4)
    val expired = isExpired(catalog.expiresAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BklnBgCard)
            .border(1.dp, if (expired) BklnStroke.copy(alpha = 0.4f) else BklnStroke, RoundedCornerShape(10.dp))
    ) {
        // Cover mosaic
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable { onView() }
        ) {
            CoverMosaic(coverUrl = catalog.coverUrl, images = images, modifier = Modifier.fillMaxSize())
            if (expired) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(BklnError.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("⏰ Expirada", color = BklnWhite, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            } else if (!catalog.expiresAt.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart).padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("⏳ ${formatExpiryShort(catalog.expiresAt)}", color = BklnWhite, fontSize = 9.sp) }
            }
        }

        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                catalog.name,
                color = if (expired) BklnMuted else BklnWhite,
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!catalog.description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(catalog.description, color = BklnMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📦 ${prods.size} producto${if (prods.size != 1) "s" else ""}", color = BklnMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, "Editar", tint = BklnMuted, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = BklnError, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoverMosaic(coverUrl: String?, images: List<String>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)).background(BklnBgInput)) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (images.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("📁", fontSize = 36.sp)
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                val col1 = images.take(2)
                val col2 = images.drop(2).take(2)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    col1.forEach { url ->
                        AsyncImage(
                            model = url, contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    repeat(2 - col1.size) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(BklnBgCard))
                    }
                }
                if (images.size > 1) {
                    Spacer(Modifier.width(1.dp))
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        col2.forEach { url ->
                            AsyncImage(
                                model = url, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )
                        }
                        repeat(2 - col2.size) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth().background(BklnBgCard))
                        }
                    }
                }
            }
        }
    }
}

internal fun isExpired(expiresAt: String?): Boolean {
    if (expiresAt.isNullOrBlank()) return false
    return try {
        val sdf  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(expiresAt.take(19)) ?: return false
        date.before(java.util.Date())
    } catch (_: Exception) { false }
}

internal fun formatExpiryShort(expiresAt: String?): String {
    if (expiresAt.isNullOrBlank()) return ""
    return try {
        val sdf  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(expiresAt.take(19)) ?: return ""
        SimpleDateFormat("d MMM", Locale("es")).format(date)
    } catch (_: Exception) { "" }
}
