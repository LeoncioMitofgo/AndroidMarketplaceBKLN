package com.brooklyn.bklnmarketplace.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Catalog
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Calendar

@Serializable
private data class CatalogInsert(
    @SerialName("user_id") val userId: String,
    val name: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
private data class CatalogUpdate(
    val name: String,
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
private data class CatalogProductInsert(
    @SerialName("catalog_id") val catalogId: String,
    @SerialName("product_id") val productId: String,
    @SerialName("sort_order") val sortOrder: Int,
)

@Serializable
private data class NewCatalogId(val id: String = "")

@Composable
fun NuevaColeccionScreen(navController: NavController, editCatalogId: String = "") {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val currentUserId = remember { SupabaseClient.client.auth.currentUserOrNull()?.id ?: "" }

    val isEdit = editCatalogId.isNotBlank()

    var name        by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expiresAt   by remember { mutableStateOf("") }  // ISO date string
    var coverUrl    by remember { mutableStateOf<String?>(null) }
    var newCoverUri by remember { mutableStateOf<Uri?>(null) }

    var userProducts     by remember { mutableStateOf<List<Product>>(emptyList()) }
    var selectedIds      by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loadingProducts  by remember { mutableStateOf(true) }
    var saving           by remember { mutableStateOf(false) }
    var saved            by remember { mutableStateOf(false) }
    var errorMsg         by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) newCoverUri = uri
    }

    // Load data
    LaunchedEffect(Unit) {
        try {
            userProducts = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw("id, name, images, price, active")) {
                    filter { eq("user_id", currentUserId); eq("active", true) }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Product>()
        } catch (_: Exception) {}
        loadingProducts = false

        if (isEdit) {
            try {
                val cat = SupabaseClient.client.postgrest
                    .from("catalogs")
                    .select(columns = Columns.raw("id, name, description, cover_url, expires_at, catalog_products(product_id)")) {
                        filter { eq("id", editCatalogId) }
                        limit(1)
                    }
                    .decodeList<Catalog>()
                    .firstOrNull()

                if (cat != null) {
                    name        = cat.name
                    description = cat.description ?: ""
                    expiresAt   = cat.expiresAt?.take(10) ?: ""
                    coverUrl    = cat.coverUrl
                    selectedIds = cat.catalogProducts?.map { it.productId }?.toSet() ?: emptySet()
                }
            } catch (_: Exception) {}
        }
    }

    // Date picker
    val cal = Calendar.getInstance()
    val datePicker = DatePickerDialog(
        context,
        { _, year, month, day ->
            expiresAt = "%04d-%02d-%02d".format(year, month + 1, day)
        },
        cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
    )

    if (saved) {
        Box(Modifier.fillMaxSize().background(BklnBgDark), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✅", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    if (isEdit) "¡Colección actualizada!" else "¡Colección creada!",
                    color = BklnWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BklnOrange)
                        .clickable { navController.navigate("colecciones") {
                            popUpTo("colecciones") { inclusive = true }
                        }}
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) { Text("Ver mis colecciones", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().background(BklnBgDark),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {

        // ── HEADER ───────────────────────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().background(BklnBgCard).padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = BklnWhite)
                    }
                    Text(
                        if (isEdit) "EDITAR COLECCIÓN" else "NUEVA COLECCIÓN",
                        color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                    )
                }
                HorizontalDivider(color = BklnStroke)
            }
        }

        // ── FORM ─────────────────────────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Cover image
                Column {
                    Text("Portada (opcional)", color = BklnMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BklnBgInput)
                            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        val previewUri = newCoverUri?.toString() ?: coverUrl
                        if (!previewUri.isNullOrBlank()) {
                            AsyncImage(
                                model = previewUri, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { newCoverUri = null; coverUrl = null },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                            ) {
                                Icon(Icons.Default.Close, "Quitar portada", tint = BklnWhite, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🖼️", fontSize = 28.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("Toca para añadir portada", color = BklnMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Name
                Column {
                    Text("Nombre *", color = BklnMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 60) name = it },
                        placeholder = { Text("Ej. Colección Verano 2025", color = BklnMuted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BklnOrange, unfocusedBorderColor = BklnStroke,
                            focusedTextColor = BklnWhite, unfocusedTextColor = BklnWhite,
                            cursorColor = BklnOrange, focusedContainerColor = BklnBgInput,
                            unfocusedContainerColor = BklnBgInput
                        ),
                        singleLine = true,
                        trailingIcon = { Text("${name.length}/60", color = BklnMuted, fontSize = 10.sp) }
                    )
                }

                // Description
                Column {
                    Text("Descripción (opcional)", color = BklnMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { if (it.length <= 280) description = it },
                        placeholder = { Text("Describe tu colección...", color = BklnMuted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BklnOrange, unfocusedBorderColor = BklnStroke,
                            focusedTextColor = BklnWhite, unfocusedTextColor = BklnWhite,
                            cursorColor = BklnOrange, focusedContainerColor = BklnBgInput,
                            unfocusedContainerColor = BklnBgInput
                        ),
                        minLines = 2, maxLines = 4,
                        trailingIcon = { Text("${description.length}/280", color = BklnMuted, fontSize = 10.sp) }
                    )
                }

                // Expiry date
                Column {
                    Text("Fecha de expiración (opcional)", color = BklnMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(BklnBgInput)
                            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                            .clickable { datePicker.show() }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (expiresAt.isBlank()) "Sin fecha límite" else expiresAt,
                            color = if (expiresAt.isBlank()) BklnMuted else BklnWhite,
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (expiresAt.isNotBlank()) {
                                Icon(Icons.Default.Close, "Quitar fecha",
                                    tint = BklnMuted, modifier = Modifier.size(16.dp).clickable { expiresAt = "" })
                            }
                            Icon(Icons.Default.CalendarToday, "Fecha", tint = BklnMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Error
                if (!errorMsg.isNullOrBlank()) {
                    Text(errorMsg!!, color = BklnError, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                // Products section header
                Text(
                    "PRODUCTOS EN LA COLECCIÓN",
                    color = BklnMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
                )
                if (selectedIds.isNotEmpty()) {
                    Text("${selectedIds.size} seleccionado${if (selectedIds.size != 1) "s" else ""}",
                        color = BklnOrange, fontSize = 12.sp)
                }
            }
        }

        // ── PRODUCT SELECTOR ─────────────────────────────────────────────────
        if (loadingProducts) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BklnOrange, modifier = Modifier.size(24.dp))
                }
            }
        } else if (userProducts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No tienes productos activos. Publica primero.", color = BklnMuted, fontSize = 13.sp)
                }
            }
        } else {
            items(userProducts, key = { it.id }) { product ->
                val selected = product.id in selectedIds
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) BklnOrange.copy(alpha = 0.15f) else BklnBgCard)
                        .border(
                            2.dp,
                            if (selected) BklnOrange else BklnStroke,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedIds = if (selected) selectedIds - product.id else selectedIds + product.id
                        }
                ) {
                    Column {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(80.dp).background(BklnBgInput),
                            contentAlignment = Alignment.Center
                        ) {
                            val img = product.images?.firstOrNull()
                            if (!img.isNullOrBlank()) {
                                AsyncImage(model = img, contentDescription = null,
                                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Text("📦", fontSize = 20.sp)
                            }
                            if (selected) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(BklnOrange.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) { Text("✓", color = BklnWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                            }
                        }
                        Text(
                            product.name, color = BklnWhite, fontSize = 10.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp,
                            modifier = Modifier.padding(5.dp)
                        )
                    }
                }
            }
        }

        // ── SAVE BUTTON ───────────────────────────────────────────────────────
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (name.isNotBlank()) BklnOrange else BklnBgInput)
                        .clickable(enabled = name.isNotBlank() && !saving) {
                            if (name.isBlank()) { errorMsg = "El nombre es obligatorio"; return@clickable }
                            scope.launch {
                                saving = true
                                errorMsg = null
                                try {
                                    // Upload cover if new
                                    var finalCoverUrl = if (newCoverUri == null) coverUrl else null
                                    val uri = newCoverUri
                                    if (uri != null) {
                                        val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                                        if (bytes != null) {
                                            val path = "$currentUserId/${System.currentTimeMillis()}.jpg"
                                            SupabaseClient.client.storage.from("catalogs").upload(path, bytes) { upsert = true }
                                            finalCoverUrl = SupabaseClient.client.storage.from("catalogs").publicUrl(path)
                                        }
                                    }

                                    val finalExpiry = if (expiresAt.isBlank()) null else "${expiresAt}T23:59:59"

                                    val catalogId: String = if (isEdit) {
                                        SupabaseClient.client.postgrest
                                            .from("catalogs")
                                            .update(CatalogUpdate(name.trim(), description.trim().ifBlank { null }, finalCoverUrl, finalExpiry)) {
                                                filter { eq("id", editCatalogId) }
                                            }
                                        editCatalogId
                                    } else {
                                        SupabaseClient.client.postgrest
                                            .from("catalogs")
                                            .insert(CatalogInsert(currentUserId, name.trim(), description.trim().ifBlank { null }, finalCoverUrl, finalExpiry)) {
                                                select(Columns.raw("id"))
                                            }
                                            .decodeSingle<NewCatalogId>().id
                                    }

                                    // Sync products
                                    SupabaseClient.client.postgrest
                                        .from("catalog_products")
                                        .delete { filter { eq("catalog_id", catalogId) } }

                                    if (selectedIds.isNotEmpty()) {
                                        val rows = selectedIds.toList().mapIndexed { i, pid ->
                                            CatalogProductInsert(catalogId, pid, i)
                                        }
                                        SupabaseClient.client.postgrest.from("catalog_products").insert(rows)
                                    }

                                    saved = true
                                } catch (e: Exception) {
                                    errorMsg = "Error al guardar: ${e.message?.take(80)}"
                                }
                                saving = false
                            }
                        }
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (saving) {
                        CircularProgressIndicator(color = BklnWhite, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (isEdit) "Guardar cambios" else "Crear colección",
                            color = if (name.isNotBlank()) BklnWhite else BklnMuted,
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
