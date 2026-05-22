package com.brooklyn.bklnmarketplace.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val CATEGORIES = listOf(
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

private val CONDITIONS = listOf(
    "nuevo"      to "✨ Nuevo",
    "como_nuevo" to "🌟 Como nuevo",
    "bueno"      to "👍 Bueno",
    "usado"      to "🔄 Usado",
)

@Composable
fun PublicarScreen(navController: NavController) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // ── AUTH GATE ──────────────────────────────────────────────────────────
    var authChecked  by remember { mutableStateOf(false) }
    var userPlan     by remember { mutableStateOf("free") }
    var productCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user == null) {
            navController.navigate("login") {
                popUpTo("publicar") { inclusive = true }
            }
            return@LaunchedEffect
        }
        try {
            val profile = SupabaseClient.client.postgrest
                .from("profiles")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("plan")) {
                    filter { eq("id", user.id) }; limit(1)
                }
                .decodeSingleOrNull<com.brooklyn.bklnmarketplace.models.Profile>()
            userPlan = profile?.plan ?: "free"
        } catch (_: Exception) {}
        try {
            val result = SupabaseClient.client.postgrest
                .from("products")
                .select {
                    count(io.github.jan.supabase.postgrest.query.Count.EXACT)
                    filter { eq("user_id", user.id); eq("active", true) }
                }
            productCount = result.countOrNull()?.toInt() ?: 0
        } catch (_: Exception) {}
        authChecked = true
    }
    if (!authChecked) return

    val isPremiumUser = userPlan == "premium"
    val photoLimit    = if (isPremiumUser) 10 else 3
    val atProdLimit   = !isPremiumUser && productCount >= 15

    // ── STEP 1 state ───────────────────────────────────────────────────────
    var name      by remember { mutableStateOf("") }
    var category  by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var desc      by remember { mutableStateOf("") }
    var location  by remember { mutableStateOf("") }

    // ── STEP 2 state ───────────────────────────────────────────────────────
    var imageUris     by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var price         by remember { mutableStateOf("") }
    var whatsapp      by remember { mutableStateOf("") }
    var isOffer       by remember { mutableStateOf(false) }
    var originalPrice by remember { mutableStateOf("") }

    // ── WIZARD ─────────────────────────────────────────────────────────────
    var step      by remember { mutableIntStateOf(1) }
    var loading   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf("") }
    var success   by remember { mutableStateOf(false) }

    // ── VALIDATION errors ──────────────────────────────────────────────────
    var errName      by remember { mutableStateOf(false) }
    var errCategory  by remember { mutableStateOf(false) }
    var errCondition by remember { mutableStateOf(false) }
    var errDesc      by remember { mutableStateOf(false) }
    var errPhotos    by remember { mutableStateOf(false) }
    var errPrice     by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val combined = (imageUris + uris).distinctBy { it.toString() }.take(photoLimit)
        imageUris = combined
        if (imageUris.isNotEmpty()) errPhotos = false
    }

    fun validateStep1(): Boolean {
        errName      = name.isBlank()
        errCategory  = category.isBlank()
        errCondition = condition.isBlank()
        errDesc      = desc.isBlank()
        return !errName && !errCategory && !errCondition && !errDesc
    }

    fun validateStep2(): Boolean {
        errPhotos = imageUris.isEmpty()
        errPrice  = price.isBlank() || price.toDoubleOrNull()?.let { it <= 0 } == true
        return !errPhotos && !errPrice
    }

    suspend fun publish() {
        if (!validateStep2()) { step = 2; return }
        loading = true; error = ""
        try {
            val user = SupabaseClient.client.auth.currentUserOrNull()
                ?: throw Exception("No autenticado")

            // Upload images to Supabase Storage
            val imageUrls = mutableListOf<String>()
            imageUris.forEachIndexed { i, uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@forEachIndexed
                val ext  = context.contentResolver.getType(uri)
                    ?.substringAfter("/")?.substringBefore(";") ?: "jpg"
                val path = "${user.id}/${System.currentTimeMillis()}-$i.$ext"
                SupabaseClient.client.storage.from("products").upload(path, bytes)
                val url = SupabaseClient.client.storage.from("products").publicUrl(path)
                imageUrls.add(url)
            }

            // Insert product
            SupabaseClient.client.postgrest.from("products").insert(
                buildJsonObject {
                    put("user_id",    user.id)
                    put("name",       name.trim())
                    put("description",desc.trim())
                    put("price",      price.toDouble())
                    put("category",   category)
                    put("condition",  condition)
                    put("location",   location.trim())
                    put("images",     buildJsonArray { imageUrls.forEach { add(it) } })
                    put("active",     true)
                    if (isOffer && isPremiumUser) {
                        val origP = originalPrice.toDoubleOrNull()
                        if (origP != null && origP > 0) {
                            put("original_price", origP)
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            cal.add(Calendar.HOUR_OF_DAY, 24)
                            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                            sdf.timeZone = TimeZone.getTimeZone("UTC")
                            put("offer_ends_at", sdf.format(cal.time))
                        }
                    }
                }
            )

            success = true
        } catch (e: Exception) {
            error = e.message ?: "Error al publicar"
        } finally {
            loading = false
        }
    }

    // ── PRODUCT LIMIT GATE (free plan) ─────────────────────────────────────
    if (atProdLimit) {
        Column(
            modifier = Modifier.fillMaxSize().background(BklnBgDark),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📦", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text("Límite alcanzado", color = BklnWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Tu plan Gratuito permite hasta 15 productos.\nTienes $productCount publicados.",
                color = BklnMuted, fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .padding(horizontal = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BklnYellow)
                    .clickable { navController.navigate("planes") }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("👑 Ver plan Premium", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        return
    }

    // ── SUCCESS SCREEN ─────────────────────────────────────────────────────
    if (success) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BklnBgDark),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🚀", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "¡PUBLICADO!",
                color = BklnWhite,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(8.dp))
            Text("Tu anuncio ya es visible en BKLN Marketplace.", color = BklnMuted, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BklnBgCard)
                        .border(1.dp, BklnStroke, RoundedCornerShape(4.dp))
                        .clickable { navController.navigate("mis_productos") { popUpTo("publicar") { inclusive = true } } }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) { Text("Ver mis productos", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BklnOrange)
                        .clickable {
                            name = ""; category = ""; condition = ""; desc = ""; location = ""
                            imageUris = emptyList(); price = ""; whatsapp = ""
                            isOffer = false; originalPrice = ""
                            step = 1; success = false
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) { Text("+ Publicar otro", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        return
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
            IconButton(onClick = {
                if (step > 1) step-- else navController.popBackStack()
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = BklnWhite)
            }
            Text(
                when (step) { 1 -> "INFORMACIÓN"; 2 -> "FOTOS Y PRECIO"; else -> "VISTA PREVIA" },
                color = BklnWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            Text("$step/3", color = BklnMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 16.dp))
        }

        // ── STEP PROGRESS BAR ────────────────────────────────────────────────
        StepsBar(current = step)

        HorizontalDivider(color = BklnStroke)

        // ── CONTENT ──────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            when (step) {
                1 -> Step1Info(
                    name = name, onName = { name = it; errName = false },
                    category = category, onCategory = { category = it; errCategory = false },
                    condition = condition, onCondition = { condition = it; errCondition = false },
                    desc = desc, onDesc = { desc = it; errDesc = false },
                    location = location, onLocation = { location = it },
                    errName = errName, errCategory = errCategory,
                    errCondition = errCondition, errDesc = errDesc,
                    onNext = { if (validateStep1()) step = 2 }
                )
                2 -> Step2Photos(
                    imageUris = imageUris,
                    onPickImages = { imagePicker.launch("image/*") },
                    onRemoveImage = { uri -> imageUris = imageUris.filter { it != uri } },
                    price = price, onPrice = { price = it; errPrice = false },
                    whatsapp = whatsapp, onWhatsapp = { whatsapp = it },
                    errPhotos = errPhotos, errPrice = errPrice,
                    photoLimit = photoLimit,
                    isPremium = isPremiumUser,
                    isOffer = isOffer, onIsOffer = { isOffer = it },
                    originalPrice = originalPrice, onOriginalPrice = { originalPrice = it },
                    onBack = { step = 1 },
                    onNext = { if (validateStep2()) step = 3 }
                )
                3 -> Step3Preview(
                    name = name, category = category, condition = condition,
                    desc = desc, location = location, price = price,
                    imageUris = imageUris, photoCount = imageUris.size,
                    isOffer = isOffer && isPremiumUser, originalPrice = originalPrice,
                    error = error, loading = loading,
                    onBack = { step = 2 },
                    onPublish = { scope.launch { publish() } }
                )
            }
        }
    }
}

// ── STEPS BAR ─────────────────────────────────────────────────────────────────

@Composable
private fun StepsBar(current: Int) {
    val labels = listOf("Información", "Fotos y precio", "Vista previa")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BklnBgCard)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { i, label ->
            val idx = i + 1
            val done   = idx < current
            val active = idx == current

            // Circle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        when {
                            done   -> Color(0x1A3DDC97)
                            active -> Color(0x1AE8431A)
                            else   -> BklnBgInput
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            done   -> BklnSuccess
                            active -> BklnOrange
                            else   -> BklnStroke
                        },
                        androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (done) "✓" else idx.toString(),
                    color = when {
                        done   -> BklnSuccess
                        active -> BklnOrange
                        else   -> BklnMuted
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = if (active) BklnWhite else if (done) BklnSuccess else BklnMuted,
                fontSize = 10.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )

            // Connector line
            if (i < labels.size - 1) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(if (done) BklnSuccess else BklnStroke)
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

// ── STEP 1: INFORMACIÓN ───────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step1Info(
    name: String, onName: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    condition: String, onCondition: (String) -> Unit,
    desc: String, onDesc: (String) -> Unit,
    location: String, onLocation: (String) -> Unit,
    errName: Boolean, errCategory: Boolean, errCondition: Boolean, errDesc: Boolean,
    onNext: () -> Unit
) {
    PubLabel("NOMBRE DEL PRODUCTO")
    PubTextField(
        value = name,
        onValueChange = onName,
        placeholder = "Ej: iPhone 13 Pro 256GB Azul",
        isError = errName,
        errorText = "El nombre es obligatorio"
    )

    Spacer(Modifier.height(16.dp))
    PubLabel("CATEGORÍA")
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CATEGORIES.forEach { (emoji, label) ->
            val selected = category == label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (selected) Color(0x1AE8431A) else BklnBgInput)
                    .border(1.dp, if (selected) BklnOrange else BklnStroke, RoundedCornerShape(20.dp))
                    .clickable { onCategory(label) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("$emoji $label", color = if (selected) BklnWhite else BklnMuted, fontSize = 12.sp)
            }
        }
    }
    if (errCategory) {
        Text("Selecciona una categoría", color = BklnError, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }

    Spacer(Modifier.height(16.dp))
    PubLabel("CONDICIÓN")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CONDITIONS.forEach { (key, label) ->
            val selected = condition == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) Color(0x1AE8431A) else BklnBgInput)
                    .border(1.dp, if (selected) BklnOrange else BklnStroke, RoundedCornerShape(6.dp))
                    .clickable { onCondition(key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected) BklnWhite else BklnMuted, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
    if (errCondition) {
        Text("Selecciona la condición", color = BklnError, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }

    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PubLabel("DESCRIPCIÓN")
        Text("${desc.length}/600", color = BklnMuted, fontSize = 10.sp)
    }
    PubTextField(
        value = desc,
        onValueChange = { if (it.length <= 600) onDesc(it) },
        placeholder = "Describe tu producto: características, estado, por qué lo vendes...",
        isError = errDesc,
        errorText = "La descripción es obligatoria",
        minLines = 4,
        maxLines = 6
    )

    Spacer(Modifier.height(16.dp))
    PubLabel("UBICACIÓN (opcional)")
    PubTextField(value = location, onValueChange = onLocation, placeholder = "Ciudad, País")

    Spacer(Modifier.height(24.dp))
    BklnButton(text = "Siguiente: Fotos y precio →", onClick = onNext)
    Spacer(Modifier.height(16.dp))
}

// ── STEP 2: FOTOS Y PRECIO ────────────────────────────────────────────────────

@Composable
private fun Step2Photos(
    imageUris: List<Uri>,
    onPickImages: () -> Unit,
    onRemoveImage: (Uri) -> Unit,
    price: String, onPrice: (String) -> Unit,
    whatsapp: String, onWhatsapp: (String) -> Unit,
    errPhotos: Boolean, errPrice: Boolean,
    photoLimit: Int = 3,
    isPremium: Boolean = false,
    isOffer: Boolean = false,
    onIsOffer: (Boolean) -> Unit = {},
    originalPrice: String = "",
    onOriginalPrice: (String) -> Unit = {},
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    PubLabel("FOTOS (máx. $photoLimit)")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BklnBgInput)
            .border(
                1.dp,
                if (errPhotos) BklnError else BklnStroke,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = imageUris.size < photoLimit) { onPickImages() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📸", fontSize = 28.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                if (imageUris.size >= photoLimit) "Límite alcanzado ($photoLimit fotos)" else "Toca para seleccionar fotos",
                color = BklnMuted,
                fontSize = 13.sp
            )
            Text("JPG, PNG · Máx. 5MB por foto", color = BklnMuted, fontSize = 10.sp)
        }
    }
    if (errPhotos) {
        Text("Sube al menos una foto", color = BklnError, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }

    if (imageUris.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            imageUris.forEachIndexed { i, uri ->
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, if (i == 0) BklnOrange else BklnStroke, RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (i == 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(BklnOrange)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) { Text("PRINCIPAL", color = BklnWhite, fontSize = 7.sp, fontWeight = FontWeight.Bold) }
                    }
                    IconButton(
                        onClick = { onRemoveImage(uri) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(24.dp)
                            .background(Color(0xCC000000), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = BklnWhite, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    PubLabel("PRECIO (XAF) *")
    PubTextField(
        value = price,
        onValueChange = onPrice,
        placeholder = "Ej: 15000",
        isError = errPrice,
        errorText = "Ingresa un precio válido",
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
    )

    if (isPremium) {
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(BklnBgInput)
                .border(1.dp, if (isOffer) BklnOrange else BklnStroke, RoundedCornerShape(6.dp))
                .clickable { onIsOffer(!isOffer) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("🏷️ Activar oferta premium", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text("El precio actual será el precio de oferta", color = BklnMuted, fontSize = 11.sp)
            }
            Switch(
                checked = isOffer, onCheckedChange = onIsOffer,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BklnWhite, checkedTrackColor = BklnOrange,
                    uncheckedTrackColor = BklnStroke, uncheckedThumbColor = BklnMuted
                )
            )
        }
        if (isOffer) {
            Spacer(Modifier.height(12.dp))
            PubLabel("PRECIO ORIGINAL (antes del descuento, XAF)")
            PubTextField(
                value = originalPrice, onValueChange = onOriginalPrice,
                placeholder = "Ej: 20000",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "La oferta expirará en 24 horas automáticamente.",
                color = BklnMuted, fontSize = 10.sp
            )
        }
    }

    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BklnBgInput)
                .border(1.dp, BklnStroke, RoundedCornerShape(4.dp))
                .clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) { Text("← Anterior", color = BklnMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(BklnOrange)
                .clickable { onNext() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) { Text("Siguiente: Vista previa →", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
    Spacer(Modifier.height(16.dp))
}

// ── STEP 3: VISTA PREVIA ──────────────────────────────────────────────────────

@Composable
private fun Step3Preview(
    name: String, category: String, condition: String,
    desc: String, location: String, price: String,
    imageUris: List<Uri>, photoCount: Int,
    isOffer: Boolean = false, originalPrice: String = "",
    error: String, loading: Boolean,
    onBack: () -> Unit,
    onPublish: () -> Unit
) {
    val condLabel = CONDITIONS.find { it.first == condition }?.second ?: condition

    // Preview card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(BklnBgInput),
                contentAlignment = Alignment.Center
            ) {
                if (imageUris.isNotEmpty()) {
                    AsyncImage(
                        model = imageUris.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text("📷", fontSize = 40.sp)
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(category, color = BklnOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(name.ifBlank { "Sin nombre" }, color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(desc.take(100) + if (desc.length > 100) "..." else "", color = BklnMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${price.toDoubleOrNull()?.toLong()?.let { java.text.NumberFormat.getNumberInstance(java.util.Locale("es","ES")).format(it) } ?: price} XAF",
                    color = if (isOffer) BklnOrange else BklnYellow,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (isOffer && originalPrice.toDoubleOrNull()?.let { it > 0 } == true) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${originalPrice.toDoubleOrNull()?.toLong()?.let { java.text.NumberFormat.getNumberInstance(java.util.Locale("es","ES")).format(it) } ?: originalPrice} XAF",
                            color = BklnMuted, fontSize = 12.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(BklnOrange)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) { Text("🏷️ OFERTA 24H", color = BklnWhite, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (condLabel.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BklnBgInput)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) { Text(condLabel, color = BklnMuted, fontSize = 11.sp) }
                    }
                    if (location.isNotBlank()) {
                        Text("📍 $location", color = BklnMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))

    // Summary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Text(
            "📋  $name · $category · $condLabel · $photoCount foto(s)",
            color = BklnMuted,
            fontSize = 13.sp
        )
    }

    if (error.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(error, color = BklnError, fontSize = 13.sp)
    }

    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(BklnBgInput)
                .border(1.dp, BklnStroke, RoundedCornerShape(4.dp))
                .clickable(enabled = !loading) { onBack() }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) { Text("← Anterior", color = BklnMuted, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(if (loading) BklnMuted else BklnOrange)
                .clickable(enabled = !loading) { onPublish() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(color = BklnWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("🚀 Publicar ahora", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

// ── SHARED HELPERS ────────────────────────────────────────────────────────────

@Composable
private fun PubLabel(text: String) {
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
private fun PubTextField(
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
            focusedBorderColor   = BklnOrange,
            unfocusedBorderColor = BklnStroke,
            errorBorderColor     = BklnError,
            focusedTextColor     = BklnWhite,
            unfocusedTextColor   = BklnWhite,
            cursorColor          = BklnOrange,
            errorTextColor       = BklnWhite,
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
