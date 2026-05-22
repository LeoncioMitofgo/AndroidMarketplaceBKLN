package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.R
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order

@Composable
fun VendedoresScreen(navController: NavController) {
    fun openSeller(id: String?) { if (!id.isNullOrBlank()) navController.navigate("vendedor/$id") }
    val focusManager = LocalFocusManager.current

    var allSellers     by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var loading        by remember { mutableStateOf(true) }
    var searchInput    by remember { mutableStateOf("") }
    var activeSearch   by remember { mutableStateOf("") }
    var planFilter     by remember { mutableStateOf("all") }  // all | premium | free
    var sortBy         by remember { mutableStateOf("rating") } // rating | products | recent

    LaunchedEffect(Unit) {
        try {
            allSellers = SupabaseClient.client.postgrest
                .from("profiles")
                .select(columns = Columns.raw(
                    "id, full_name, username, avatar_url, plan, rating, review_count, " +
                    "followers_count, following_count, bio, created_at"
                )) {
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Profile>()
        } catch (_: Exception) {}
        loading = false
    }

    // ── CLIENT-SIDE FILTER + SORT ──────────────────────────────────────────
    val filtered = remember(allSellers, activeSearch, planFilter, sortBy) {
        var list = allSellers.filter { it.followersCount != null || it.plan != null } // skip empty rows

        if (planFilter != "all") list = list.filter { it.plan == planFilter }
        if (activeSearch.isNotBlank()) {
            val q = activeSearch.lowercase()
            list = list.filter {
                (it.fullName ?: "").lowercase().contains(q) ||
                (it.username ?: "").lowercase().contains(q)
            }
        }

        list.sortedWith(compareByDescending {
            when (sortBy) {
                "rating" -> {
                    val M = 10.0; val C = 3.5
                    val rc = (it.reviewCount ?: 0)
                    if (rc > 0) (rc / (rc + M)) * (it.rating ?: 0.0) + (M / (rc + M)) * C else 0.0
                }
                "recent" -> 0.0  // already ordered by created_at from DB
                else     -> (it.followersCount ?: 0).toDouble()
            }
        })
    }

    val premiumSellers = remember(filtered) { filtered.filter { it.plan == "premium" } }
    val showFeatured   = planFilter != "free" && activeSearch.isBlank() && premiumSellers.isNotEmpty()
    val listSellers    = if (showFeatured && planFilter == "all")
        filtered.filter { it.plan != "premium" }
    else
        filtered

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                placeholder = { Text("Buscar por nombre o @usuario...", color = BklnMuted, fontSize = 13.sp) },
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
                    focusedBorderColor      = BklnOrange,
                    unfocusedBorderColor    = BklnStroke,
                    focusedTextColor        = BklnWhite,
                    unfocusedTextColor      = BklnWhite,
                    cursorColor             = BklnOrange,
                    focusedContainerColor   = BklnBgInput,
                    unfocusedContainerColor = BklnBgInput,
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(50.dp)
            )
        }

        HorizontalDivider(color = BklnStroke)

        // ── FILTER CHIPS + SORT ──────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("all" to "Todos", "premium" to "👑 Premium", "free" to "Gratuitos").forEach { (key, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (planFilter == key) BklnOrange else BklnBgInput)
                        .border(1.dp, if (planFilter == key) BklnOrange else BklnStroke, RoundedCornerShape(20.dp))
                        .clickable { planFilter = key }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(label, color = if (planFilter == key) BklnWhite else BklnMuted, fontSize = 12.sp,
                        fontWeight = if (planFilter == key) FontWeight.Bold else FontWeight.Normal)
                }
            }

            Spacer(Modifier.weight(1f))

            // Sort mini-selector
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("rating" to "★", "recent" to "🕐").forEach { (key, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (sortBy == key) BklnOrange else BklnBgInput)
                            .border(1.dp, if (sortBy == key) BklnOrange else BklnStroke, RoundedCornerShape(4.dp))
                            .clickable { sortBy = key }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(label, color = if (sortBy == key) BklnWhite else BklnMuted, fontSize = 12.sp)
                    }
                }
            }
        }

        HorizontalDivider(color = BklnStroke)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {

                // ── STATS ──────────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BklnBgCard)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "${allSellers.size} vendedores activos",
                            color = BklnMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "👑 ${allSellers.count { it.plan == "premium" }} Premium",
                            color = BklnYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${filtered.size} resultado${if (filtered.size != 1) "s" else ""}",
                            color = BklnMuted,
                            fontSize = 11.sp
                        )
                    }
                    HorizontalDivider(color = BklnStroke)
                }

                // ── PREMIUM FEATURED ───────────────────────────────────────
                if (showFeatured) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BklnBgDark)
                                .padding(top = 20.dp, bottom = 8.dp)
                        ) {
                            Text(
                                "// de confianza · premium",
                                color = BklnMuted,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "👑 VENDEDORES PREMIUM",
                                color = BklnWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(premiumSellers, key = { it.id ?: it.hashCode().toString() }) { seller ->
                                    FeaturedSellerCard(seller, onClick = { openSeller(seller.id) })
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)); HorizontalDivider(color = BklnStroke) }
                }

                // ── LIST HEADER ────────────────────────────────────────────
                item {
                    Text(
                        when (planFilter) {
                            "premium" -> "VENDEDORES PREMIUM"
                            "free"    -> "VENDEDORES GRATUITOS"
                            else      -> if (showFeatured) "TODOS LOS VENDEDORES" else "VENDEDORES"
                        },
                        color = BklnMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                // ── SELLERS LIST ───────────────────────────────────────────
                if (listSellers.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🔍", fontSize = 36.sp)
                            Spacer(Modifier.height(10.dp))
                            Text("Sin resultados", color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Prueba con otro nombre o filtro.", color = BklnMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    itemsIndexed(listSellers, key = { _, s -> s.id ?: s.hashCode().toString() }) { index, seller ->
                        SellerRow(rank = index + 1, seller = seller, onClick = { openSeller(seller.id) })
                        HorizontalDivider(color = BklnStroke, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── FEATURED CARD (Premium) ───────────────────────────────────────────────────

@Composable
private fun FeaturedSellerCard(seller: Profile, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(BklnBgCard)
            .border(1.dp, Color(0x33F5C842), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Crown badge
        Text("👑", fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))

        // Avatar
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(BklnBgInput)
                .border(2.dp, BklnYellow, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!seller.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = seller.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    (seller.fullName ?: "?").first().uppercaseChar().toString(),
                    color = BklnWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            seller.fullName ?: "Vendedor",
            color = BklnWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!seller.username.isNullOrBlank()) {
            Text("@${seller.username}", color = BklnMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(6.dp))

        // Stars
        val rating = seller.rating ?: 0.0
        Text(
            starsText(rating),
            color = BklnYellow,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
        Text(
            if (rating > 0) "${"%.1f".format(rating)} (${seller.reviewCount ?: 0})"
            else "Sin reseñas",
            color = BklnMuted,
            fontSize = 9.sp
        )
    }
}

// ── SELLER ROW ────────────────────────────────────────────────────────────────

@Composable
private fun SellerRow(rank: Int, seller: Profile, onClick: () -> Unit = {}) {
    val isPremium = seller.plan == "premium"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rank
        Text(
            "#$rank",
            color = BklnMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp)
        )

        // Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(BklnBgInput)
                .border(1.5.dp, if (isPremium) BklnYellow else BklnStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (!seller.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = seller.avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    (seller.fullName ?: "?").first().uppercaseChar().toString(),
                    color = BklnWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    seller.fullName ?: "Vendedor",
                    color = BklnWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isPremium) {
                    Spacer(Modifier.width(4.dp))
                    Text("👑", fontSize = 11.sp)
                }
            }
            if (!seller.username.isNullOrBlank()) {
                Text("@${seller.username}", color = BklnMuted, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.width(8.dp))

        // Rating
        Column(horizontalAlignment = Alignment.End) {
            val rating = seller.rating ?: 0.0
            if (rating > 0) {
                Text(
                    "${"%.1f".format(rating)}★",
                    color = BklnYellow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${seller.reviewCount ?: 0} reseñas",
                    color = BklnMuted,
                    fontSize = 9.sp
                )
            } else {
                Text("—", color = BklnMuted, fontSize = 13.sp)
            }
        }
    }
}

private fun starsText(rating: Double): String {
    val full  = rating.toInt().coerceIn(0, 5)
    val empty = 5 - full
    return "★".repeat(full) + "☆".repeat(empty)
}
