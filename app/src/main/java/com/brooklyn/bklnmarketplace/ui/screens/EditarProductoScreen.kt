package com.brooklyn.bklnmarketplace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
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
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

private val EP_CATEGORIES = listOf(
    "👗" to "Ropa y Moda",
    "👜" to "Complementos",
    "👟" to "Calzado",
    "📱" to "Móviles",
    "💻" to "Electrónica",
    "🏠" to "Hogar y Deco",
    "🚗" to "Vehículos",
    "⚽" to "Deportes",
    "💄" to "Belleza y Salud",
    "👶" to "Infantil",
    "🎮" to "Videojuegos",
    "🎵" to "Música",
    "📚" to "Libros",
    "🎨" to "Arte y Colección",
    "🛠️" to "Servicios",
    "🏘️" to "Inmuebles",
)

private val EP_CONDITIONS = listOf(
    "nuevo"      to "✨ Nuevo",
    "como_nuevo" to "🌟 Como nuevo",
    "bueno"      to "👍 Bueno",
    "usado"      to "🔄 Usado",
)

@Serializable
private data class ProductUpdate(
    val name: String,
    val description: String,
    val price: Double,
    @SerialName("original_price") val originalPrice: Double?,
    val category: String,
    val condition: String,
    val location: String,
    val images: List<String>,
)

@Composable
fun EditarProductoScreen(navController: NavController, productId: String) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var loading   by remember { mutableStateOf(true) }
    var saving    by remember { mutableStateOf(false) }
    var saved     by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf("") }
    var userPlan  by remember { mutableStateOf("free") }

    // Form fields
    var name          by remember { mutableStateOf("") }
    var category      by remember { mutableStateOf("") }
    var condition     by remember { mutableStateOf("") }
    var desc          by remember { mutableStateOf("") }
    var location      by remember { mutableStateOf("") }
    var price         by remember { mutableStateOf("") }
    var originalPrice by remember { mutableStateOf("") }

    // Images
    var keptUrls  by remember { mutableStateOf<List<String>>(emptyList()) }
    var newUris   by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // Validation
    var errName      by remember { mutableStateOf(false) }
    var errCategory  by remember { mutableStateOf(false) }
    var errCondition by remember { mutableStateOf(false) }
    var errDesc      by remember { mutableStateOf(false) }
    var errPrice     by remember { mutableStateOf(false) }

    val maxPhotos = if (userPlan == "premium") 10 else 3
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val slots = maxPhotos - (keptUrls.size + newUris.size)
        if (slots > 0) newUris = (newUris + uris).take(slots)
    }

    // Load product + plan
    LaunchedEffect(productId) {
        try {
            val product = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw(
                    "id, name, description, price, original_price, category, condition, location, images"
                )) {
                    filter { eq("id", productId) }
                    limit(1)
                }
                .decodeList<Product>()
                .firstOrNull()

            product?.let { p ->
                name          = p.name
                desc          = p.description ?: ""
                price         = p.price?.toLong()?.toString() ?: ""
                originalPrice = p.originalPrice?.toLong()?.toString() ?: ""
                category      = p.category ?: ""
                condition     = p.condition ?: ""
                location      = p.location ?: ""
                keptUrls      = p.images ?: emptyList()
            }
        } catch (_: Exception) {}

        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id
        if (userId != null) {
            try {
                val profile = SupabaseClient.client.postgrest
                    .from("profiles")
                    .select(columns = Columns.raw("plan")) {
                        filter { eq("id", userId) }; limit(1)
                    }
                    .decodeSingleOrNull<com.brooklyn.bklnmarketplace.models.Profile>()
                userPlan = profile?.plan ?: "free"
            } catch (_: Exception) {}
        }
        loading = false
    }

    fun validate(): Boolean {
        errName      = name.isBlank()
        errCategory  = category.isBlank()
        errCondition = condition.isBlank()
        errDesc      = desc.isBlank()
        errPrice     = price.isBlank() || price.toDoubleOrNull()?.let { it <= 0 } == true
        return !errName && !errCategory && !errCondition && !errDesc && !errPrice
    }

    fun save() {
        if (!validate()) return
        scope.launch {
            saving = true; error = ""
            try {
                val user = SupabaseClient.client.auth.currentUserOrNull()
                    ?: throw Exception("No autenticado")

                // Upload new images
                val uploadedUrls = mutableListOf<String>()
                newUris.forEachIndexed { i, uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@forEachIndexed
                    val ext  = context.contentResolver.getType(uri)
                        ?.substringAfter("/")?.substringBefore(";") ?: "jpg"
                    val path = "${user.id}/${System.currentTimeMillis()}-$i.$ext"
                    SupabaseClient.client.storage.from("products").upload(path, bytes)
                    uploadedUrls.add(SupabaseClient.client.storage.from("products").publicUrl(path))
                }

                val finalImages = keptUrls + uploadedUrls
                val op = originalPrice.toDoubleOrNull()?.takeIf { it > 0 }

                SupabaseClient.client.postgrest.from("products").update(
                    ProductUpdate(
                        name          = name.trim(),
                        description   = desc.trim(),
                        price         = price.toDouble(),
                        originalPrice = op,
                        category      = category,
                        condition     = condition,
                        location      = location.trim(),
                        images        = finalImages,
                    )
                ) { filter { eq("id", productId) } }

                saved = true
            } catch (e: Exception) {
                error = e.message ?: "Error al guardar"
            } finally {
                saving = false
            }
        }
    }

    // ── SUCCESS ───────────────────────────────────────────────────────────────
    if (saved) {
        Column(
            modifier = Modifier.fillMaxSize().background(BklnBgDark),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("✅", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("¡GUARDADO!", color = BklnWhite, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text("Los cambios ya son visibles.", color = BklnMuted, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            BklnButton("← Volver a mis productos") {
                navController.navigate("mis_productos") {
                    popUpTo("mis_productos") { inclusive = true }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {

        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = BklnWhite)
            }
            Text(
                "EDITAR PRODUCTO",
                color = BklnWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            if (saving) {
                CircularProgressIndicator(
                    color = BklnOrange,
                    modifier = Modifier.size(20.dp).padding(end = 12.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        HorizontalDivider(color = BklnStroke)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            // ── FOTOS ─────────────────────────────────────────────────────────
            EpLabel("FOTOS (máx. $maxPhotos)")
            if (keptUrls.isEmpty() && newUris.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BklnBgInput)
                        .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                        .clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📸", fontSize = 28.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Toca para añadir fotos", color = BklnMuted, fontSize = 13.sp)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    keptUrls.forEachIndexed { i, url ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, if (i == 0) BklnOrange else BklnStroke, RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = url, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { keptUrls = keptUrls.filter { it != url } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color(0xCC000000), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = BklnWhite, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    newUris.forEach { uri ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = uri, contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { newUris = newUris.filter { it != uri } },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(Color(0xCC000000), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = BklnWhite, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                    if (keptUrls.size + newUris.size < maxPhotos) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(BklnBgInput)
                                .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                                .clickable { imagePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = BklnMuted, fontSize = 28.sp, fontWeight = FontWeight.Light)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── NOMBRE ───────────────────────────────────────────────────────
            EpLabel("NOMBRE DEL PRODUCTO")
            EpTextField(
                value = name, onValueChange = { name = it; errName = false },
                placeholder = "Ej: iPhone 13 Pro 256GB",
                isError = errName, errorText = "El nombre es obligatorio"
            )

            Spacer(Modifier.height(16.dp))

            // ── CATEGORÍA ────────────────────────────────────────────────────
            EpLabel("CATEGORÍA")
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EP_CATEGORIES.forEach { (emoji, label) ->
                    val sel = category == label
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (sel) Color(0x1AE8431A) else BklnBgInput)
                            .border(1.dp, if (sel) BklnOrange else BklnStroke, RoundedCornerShape(20.dp))
                            .clickable { category = label; errCategory = false }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("$emoji $label", color = if (sel) BklnWhite else BklnMuted, fontSize = 12.sp)
                    }
                }
            }
            if (errCategory) Text("Selecciona una categoría", color = BklnError, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(16.dp))

            // ── CONDICIÓN ────────────────────────────────────────────────────
            EpLabel("CONDICIÓN")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                EP_CONDITIONS.forEach { (key, label) ->
                    val sel = condition == key
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (sel) Color(0x1AE8431A) else BklnBgInput)
                            .border(1.dp, if (sel) BklnOrange else BklnStroke, RoundedCornerShape(6.dp))
                            .clickable { condition = key; errCondition = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (sel) BklnWhite else BklnMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            if (errCondition) Text("Selecciona la condición", color = BklnError, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(16.dp))

            // ── DESCRIPCIÓN ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EpLabel("DESCRIPCIÓN")
                Text("${desc.length}/600", color = BklnMuted, fontSize = 10.sp)
            }
            EpTextField(
                value = desc, onValueChange = { if (it.length <= 600) { desc = it; errDesc = false } },
                placeholder = "Describe tu producto...",
                isError = errDesc, errorText = "La descripción es obligatoria",
                minLines = 4, maxLines = 6
            )

            Spacer(Modifier.height(16.dp))

            // ── PRECIO ───────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    EpLabel("PRECIO (XAF) *")
                    EpTextField(
                        value = price, onValueChange = { price = it; errPrice = false },
                        placeholder = "15000",
                        isError = errPrice, errorText = "Obligatorio",
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                }
                Column(Modifier.weight(1f)) {
                    EpLabel("PRECIO ANTERIOR")
                    if (userPlan == "premium") {
                        EpTextField(
                            value = originalPrice, onValueChange = { originalPrice = it },
                            placeholder = "20000 (precio tachado)",
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .background(BklnBgInput)
                                .border(1.dp, BklnStroke, androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
                                .clickable { }
                                .padding(13.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("🔒", fontSize = 13.sp)
                                Text("Solo Premium", color = BklnMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── UBICACIÓN ────────────────────────────────────────────────────
            EpLabel("UBICACIÓN (opcional)")
            EpTextField(value = location, onValueChange = { location = it }, placeholder = "Ciudad, País")

            if (error.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = BklnError, fontSize = 13.sp)
            }

            Spacer(Modifier.height(28.dp))
            BklnButton(text = if (saving) "Guardando..." else "Guardar cambios", enabled = !saving) { save() }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── LOCAL HELPERS ─────────────────────────────────────────────────────────────

@Composable
private fun EpLabel(text: String) {
    Text(
        text,
        color = BklnMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun EpTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isError: Boolean = false,
    errorText: String = "",
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = BklnMuted, fontSize = 14.sp) },
        isError = isError,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = BklnOrange,
            unfocusedBorderColor    = BklnStroke,
            errorBorderColor        = BklnError,
            focusedTextColor        = BklnWhite,
            unfocusedTextColor      = BklnWhite,
            cursorColor             = BklnOrange,
            errorTextColor          = BklnWhite,
            focusedContainerColor   = BklnBgInput,
            unfocusedContainerColor = BklnBgInput,
            errorContainerColor     = BklnBgInput,
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp)
    )
    if (isError && errorText.isNotBlank()) {
        Text(errorText, color = BklnError, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
    }
}
