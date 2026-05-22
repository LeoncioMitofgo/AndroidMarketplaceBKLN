package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import com.brooklyn.bklnmarketplace.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val EXPLORE_CATEGORIES = listOf(
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

private val SORT_OPTIONS = listOf(
    "recent"     to "Recientes",
    "price_asc"  to "↑ Precio",
    "price_desc" to "↓ Precio",
)

private val CONDITION_OPTIONS = listOf(
    "" to "Todos",
    "nuevo" to "Nuevo",
    "usado - como nuevo" to "Como nuevo",
    "usado - buen estado" to "Buen estado",
    "usado - aceptable" to "Aceptable",
)

@Composable
fun ExplorarScreen(navController: NavController, initialCategory: String = "") {
    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchInput    by remember { mutableStateOf("") }
    var activeSearch   by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf(initialCategory) }
    var activeSort     by remember { mutableStateOf("recent") }

    // Advanced filter drafts (bound to UI inputs)
    var filterCondition by remember { mutableStateOf("") }
    var filterPriceMin  by remember { mutableStateOf("") }
    var filterPriceMax  by remember { mutableStateOf("") }
    var filterLocation  by remember { mutableStateOf("") }
    var filterSeller    by remember { mutableStateOf("") }

    // Applied advanced filters (trigger reload)
    var activeCondition by remember { mutableStateOf("") }
    var activePriceMin  by remember { mutableStateOf("") }
    var activePriceMax  by remember { mutableStateOf("") }
    var activeLocation  by remember { mutableStateOf("") }
    var activeSeller    by remember { mutableStateOf("") }

    var showFilters by remember { mutableStateOf(false) }

    val activeFilterCount = listOf(activeCondition, activePriceMin, activePriceMax, activeLocation, activeSeller)
        .count { it.isNotBlank() }

    var products   by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading    by remember { mutableStateOf(true) }
    var totalCount by remember { mutableIntStateOf(0) }

    suspend fun load() {
        loading = true
        try {
            val cols = "id, name, price, original_price, images, category, condition, location, views, created_at, " +
                       "profiles!products_user_id_fkey(full_name, avatar_url, plan)"

            val result = SupabaseClient.client.postgrest
                .from("products")
                .select(columns = Columns.raw(cols)) {
                    filter {
                        eq("active", true)
                        if (activeSearch.isNotBlank())    ilike("name", "%$activeSearch%")
                        if (activeCategory.isNotBlank())  eq("category", activeCategory)
                        if (activeCondition.isNotBlank()) eq("condition", activeCondition)
                        if (activeLocation.isNotBlank())  ilike("location", "%$activeLocation%")
                        val minP = activePriceMin.toDoubleOrNull()
                        val maxP = activePriceMax.toDoubleOrNull()
                        if (minP != null) gte("price", minP)
                        if (maxP != null) lte("price", maxP)
                    }
                    when (activeSort) {
                        "price_asc"  -> order("price", Order.ASCENDING)
                        "price_desc" -> order("price", Order.DESCENDING)
                        else         -> order("created_at", Order.DESCENDING)
                    }
                    limit(80)
                }
                .decodeList<Product>()

            // Client-side seller name filter
            val filtered = if (activeSeller.isNotBlank())
                result.filter { it.profiles?.fullName?.contains(activeSeller, ignoreCase = true) == true }
            else result

            // Premium sellers float to the top (only when not text-searching)
            products = if (activeSearch.isBlank() && activeSeller.isBlank())
                filtered.sortedByDescending { it.profiles?.plan == "premium" }
            else
                filtered
            totalCount = products.size
        } catch (_: Exception) {}
        loading = false
    }

    LaunchedEffect(activeSearch, activeCategory, activeSort, activeCondition, activePriceMin, activePriceMax, activeLocation, activeSeller) {
        load()
    }

    fun applyFilters() {
        activeCondition = filterCondition
        activePriceMin  = filterPriceMin
        activePriceMax  = filterPriceMax
        activeLocation  = filterLocation
        activeSeller    = filterSeller
        showFilters     = false
    }

    fun clearFilters() {
        filterCondition = ""; activeCondition = ""
        filterPriceMin  = ""; activePriceMin  = ""
        filterPriceMax  = ""; activePriceMax  = ""
        filterLocation  = ""; activeLocation  = ""
        filterSeller    = ""; activeSeller    = ""
    }

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {

        // ── BRAND HEADER ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(26.dp)
            )
            Text("BKLN ", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("MARKETPLACE", color = BklnOrange, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }

        // ── SEARCH TOPBAR ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                placeholder = { Text("Buscar productos...", color = BklnMuted, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = BklnMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchInput.isNotBlank()) {
                        IconButton(onClick = { searchInput = ""; activeSearch = "" }) {
                            Icon(Icons.Default.Close, null, tint = BklnMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    activeSearch = searchInput.trim()
                    focusManager.clearFocus()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor    = BklnOrange,
                    unfocusedBorderColor  = BklnStroke,
                    focusedTextColor      = BklnWhite,
                    unfocusedTextColor    = BklnWhite,
                    cursorColor           = BklnOrange,
                    focusedContainerColor   = BklnBgInput,
                    unfocusedContainerColor = BklnBgInput,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(50.dp)
            )

            // Filter button
            Box(
                modifier = Modifier
                    .height(50.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (activeFilterCount > 0) BklnOrange else BklnBgInput)
                    .border(1.dp, if (activeFilterCount > 0) BklnOrange else BklnStroke, RoundedCornerShape(8.dp))
                    .clickable { showFilters = !showFilters }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.FilterList, null, tint = BklnWhite, modifier = Modifier.size(16.dp))
                    if (activeFilterCount > 0) {
                        Text(
                            activeFilterCount.toString(),
                            color = BklnWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── ADVANCED FILTER PANEL ────────────────────────────────────────────
        AnimatedVisibility(
            visible = showFilters,
            enter = expandVertically(),
            exit  = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BklnBgCard)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Condition
                Text("CONDICIÓN", color = BklnMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CONDITION_OPTIONS.forEach { (value, label) ->
                        val selected = filterCondition == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) BklnOrange else BklnBgInput)
                                .border(1.dp, if (selected) BklnOrange else BklnStroke, RoundedCornerShape(20.dp))
                                .clickable { filterCondition = value }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                label,
                                color = if (selected) BklnWhite else BklnMuted,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // Price range
                Text("PRECIO (XAF)", color = BklnMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = filterPriceMin,
                        onValueChange = { if (it.all { c -> c.isDigit() }) filterPriceMin = it },
                        placeholder = { Text("Mín", color = BklnMuted, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(46.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BklnOrange, unfocusedBorderColor = BklnStroke,
                            focusedTextColor     = BklnWhite,  unfocusedTextColor   = BklnWhite,
                            cursorColor          = BklnOrange
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                    Text("—", color = BklnMuted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = filterPriceMax,
                        onValueChange = { if (it.all { c -> c.isDigit() }) filterPriceMax = it },
                        placeholder = { Text("Máx", color = BklnMuted, fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).height(46.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BklnOrange, unfocusedBorderColor = BklnStroke,
                            focusedTextColor     = BklnWhite,  unfocusedTextColor   = BklnWhite,
                            cursorColor          = BklnOrange
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                    )
                }

                // Location
                Text("UBICACIÓN", color = BklnMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = filterLocation,
                    onValueChange = { filterLocation = it },
                    placeholder = { Text("📍 Ciudad, barrio...", color = BklnMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BklnOrange, unfocusedBorderColor = BklnStroke,
                        focusedTextColor     = BklnWhite,  unfocusedTextColor   = BklnWhite,
                        cursorColor          = BklnOrange
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                // Seller name
                Text("VENDEDOR", color = BklnMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = filterSeller,
                    onValueChange = { filterSeller = it },
                    placeholder = { Text("👤 Nombre del vendedor...", color = BklnMuted, fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BklnOrange, unfocusedBorderColor = BklnStroke,
                        focusedTextColor     = BklnWhite,  unfocusedTextColor   = BklnWhite,
                        cursorColor          = BklnOrange
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )

                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnBgInput)
                            .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
                            .clickable { clearFilters() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Limpiar", color = BklnMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }

                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnOrange)
                            .clickable { applyFilters(); focusManager.clearFocus() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Aplicar filtros", color = BklnWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        HorizontalDivider(color = BklnStroke)

        // ── CATEGORY CHIPS ───────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // "Todos" chip
            CategoryChip(
                label = "Todos",
                selected = activeCategory.isBlank(),
                onClick = { activeCategory = "" }
            )
            EXPLORE_CATEGORIES.forEach { (emoji, label) ->
                CategoryChip(
                    label = "$emoji $label",
                    selected = activeCategory == label,
                    onClick = { activeCategory = if (activeCategory == label) "" else label }
                )
            }
        }

        HorizontalDivider(color = BklnStroke)

        // ── GRID ─────────────────────────────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Sort bar + count — full width
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (loading) "Cargando..." else "$totalCount productos",
                        color = BklnMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        SORT_OPTIONS.forEach { (key, label) ->
                            val selected = activeSort == key
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (selected) BklnOrange else BklnBgCard)
                                    .border(1.dp, if (selected) BklnOrange else BklnStroke, RoundedCornerShape(4.dp))
                                    .clickable { activeSort = key }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (selected) BklnWhite else BklnMuted,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            if (loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = BklnOrange) }
                }
            } else if (products.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 60.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("SIN RESULTADOS", color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("No encontramos productos con esos filtros.", color = BklnMuted, fontSize = 13.sp)
                        if (activeCategory.isNotBlank() || activeSearch.isNotBlank() || activeFilterCount > 0) {
                            Spacer(Modifier.height(14.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BklnBgCard)
                                    .border(1.dp, BklnStroke, RoundedCornerShape(4.dp))
                                    .clickable {
                                        activeSearch = ""; searchInput = ""; activeCategory = ""
                                        clearFilters()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) { Text("Quitar filtros", color = BklnWhite, fontSize = 13.sp) }
                        }
                    }
                }
            } else {
                items(products, key = { it.id }) { product ->
                    ExploreProductCard(product = product, onClick = {
                        navController.navigate("producto/${product.id}")
                    })
                }
            }
        }
    }
}

// ── CATEGORY CHIP ─────────────────────────────────────────────────────────────

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) BklnOrange else BklnBgInput)
            .border(1.dp, if (selected) BklnOrange else BklnStroke, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) BklnWhite else BklnMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ── PRODUCT CARD ─────────────────────────────────────────────────────────────

@Composable
private fun ExploreProductCard(product: Product, onClick: () -> Unit = {}) {
    val isPremiumSeller = product.profiles?.plan == "premium"
    val hasOffer        = product.originalPrice != null

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(BklnBgCard)
            .border(
                width = if (isPremiumSeller) 1.dp else 1.dp,
                color = if (isPremiumSeller) Color(0x33F5C842) else BklnStroke,
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
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
                Text(catEmoji(product.category), fontSize = 24.sp)
            }

            // Badge
            val badge: Pair<String, Color>? = when {
                isPremiumSeller && product.originalPrice != null -> "👑 OFERTA" to Color(0xFFF5C842)
                isPremiumSeller -> "👑 PREMIUM" to Color(0xFFF5C842)
                hasOffer        -> "OFERTA" to BklnOrange
                product.condition == "nuevo" -> "NUEVO" to BklnSuccess
                else -> null
            }
            badge?.let { (label, color) ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(color.copy(alpha = 0.9f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(label, color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        // Info
        Column(modifier = Modifier.padding(6.dp)) {
            if (!product.category.isNullOrBlank()) {
                Text(product.category, color = BklnOrange, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                formatExplorePrice(product.price),
                color = BklnYellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (product.originalPrice != null) {
                Text(
                    formatExplorePrice(product.originalPrice),
                    color = BklnMuted,
                    fontSize = 9.sp,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                )
            }
            Spacer(Modifier.height(4.dp))
            // Seller
            val sellerName = product.profiles?.fullName?.takeIf { it.isNotBlank() } ?: "Vendedor"
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPremiumSeller) {
                    Text("👑 ", fontSize = 8.sp)
                }
                Text(
                    sellerName,
                    color = BklnMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun catEmoji(category: String?): String = when (category) {
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
    "Música"          -> "🎵"
    "Libros"          -> "📚"
    "Arte y Colección"-> "🎨"
    "Servicios"       -> "🛠️"
    "Inmuebles"       -> "🏘️"
    else              -> "📦"
}

private fun formatExplorePrice(price: Double?): String {
    if (price == null) return ""
    val fmt = NumberFormat.getNumberInstance(Locale("es", "ES"))
    return "${fmt.format(price.toLong())} XAF"
}
