package com.brooklyn.bklnmarketplace.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Count
import kotlinx.coroutines.launch

@Composable
fun PerfilScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var displayName  by remember { mutableStateOf("") }
    var email        by remember { mutableStateOf("") }
    var initials     by remember { mutableStateOf("?") }
    var plan         by remember { mutableStateOf("free") }
    var followers    by remember { mutableStateOf(0) }
    var following    by remember { mutableStateOf(0) }
    var productCount by remember { mutableStateOf(0) }
    var favCount     by remember { mutableStateOf(0) }
    var avatarUrl    by remember { mutableStateOf<String?>(null) }
    var loading      by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val user = SupabaseClient.client.auth.currentUserOrNull()
        if (user == null) { loading = false; return@LaunchedEffect }
        val userId = user.id
        email = user.email ?: ""

        // ── 1. Perfil completo desde la tabla profiles ──────────────────
        try {
            val profile = SupabaseClient.client.postgrest
                .from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Profile>()

            val name = profile?.fullName
                ?: user.userMetadata?.get("full_name")?.toString()?.trim('"')
                ?: ""
            displayName = name.ifBlank { email }
            plan        = profile?.plan ?: "free"
            followers   = profile?.followersCount ?: 0
            following   = profile?.followingCount ?: 0
            avatarUrl   = profile?.avatarUrl?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            val name = user.userMetadata?.get("full_name")?.toString()?.trim('"') ?: ""
            displayName = name.ifBlank { email }
        }

        initials = displayName
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { email.firstOrNull()?.uppercaseChar()?.toString() ?: "?" }

        // ── 2. Contar productos publicados por el usuario ────────────────
        try {
            val result = SupabaseClient.client.postgrest
                .from("products")
                .select {
                    count(Count.EXACT)
                    filter { eq("user_id", userId) }
                }
            productCount = result.countOrNull()?.toInt() ?: 0
        } catch (_: Exception) {}

        // ── 3. Contar favoritos del usuario ──────────────────────────────
        try {
            val result = SupabaseClient.client.postgrest
                .from("favorites")
                .select {
                    count(Count.EXACT)
                    filter { eq("user_id", userId) }
                }
            favCount = result.countOrNull()?.toInt() ?: 0
        } catch (_: Exception) {}

        loading = false
    }

    val isPremium = plan == "premium"
    val isGuest   = email.isBlank() && displayName.isBlank()

    // ── GUEST STATE ──────────────────────────────────────────────────────────
    if (isGuest && !loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BklnBgDark),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Text("👤", fontSize = 56.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    "TU PERFIL",
                    color = BklnWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Inicia sesión o regístrate para ver tu perfil, gestionar tus productos y guardar favoritos.",
                    color = BklnMuted,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BklnOrange)
                        .clickable { navController.navigate("login") }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Iniciar sesión", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BklnBgCard)
                        .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                        .clickable { navController.navigate("login") }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Crear cuenta gratis", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BklnBgDark)
            .verticalScroll(rememberScrollState())
    ) {
        // ── TOPBAR ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(BklnBgCard)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MI PERFIL",
                color = BklnWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            Text("BKLN", color = BklnOrange, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }

        HorizontalDivider(color = BklnStroke)

        // ── AVATAR + DATOS ───────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar — foto de perfil o iniciales como fallback
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(BklnBgInput)
                    .border(2.dp, if (isPremium) BklnYellow else BklnOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Foto de perfil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(initials, color = BklnWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(displayName.ifBlank { "Usuario" }, color = BklnWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(email, color = BklnMuted, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))

            // Badge de plan — dinámico
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x1AF5C842))
                        .border(1.dp, Color(0x4DF5C842), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("PREMIUM", color = BklnYellow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text("PLAN FREE", color = BklnMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stats — reales desde profiles + count de products
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FollowStat(followers.toString(), "Seguidores")
                Box(Modifier.width(1.dp).height(36.dp).background(BklnStroke))
                FollowStat(following.toString(), "Siguiendo")
                Box(Modifier.width(1.dp).height(36.dp).background(BklnStroke))
                FollowStat(productCount.toString(), "Productos")
            }

            // Banner "Mejorar a Premium" solo si es free
            if (!isPremium) {
                Spacer(Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x14E8431A))
                        .border(1.dp, Color(0x4DE8431A), RoundedCornerShape(4.dp))
                        .clickable { navController.navigate("planes") }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Mejorar a Premium",
                        color = BklnOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }

        HorizontalDivider(color = BklnStroke)
        Spacer(Modifier.height(8.dp))

        // ── SECCIÓN: Mi cuenta ───────────────────────────────────────────
        MenuSection("Mi cuenta")
        MenuItem(Icons.Default.BarChart, "Resumen") { navController.navigate("resumen") }
        MenuItemWithBadge(Icons.Default.Inventory, "Mis Productos", productCount.toString()) { navController.navigate("mis_productos") }
        MenuItemWithBadge(Icons.Default.Favorite, "Favoritos", favCount.toString()) { navController.navigate("favoritos") }
        MenuItemPremium(Icons.Default.Folder, "Colecciones") { navController.navigate("colecciones") }
        MenuItem(Icons.Default.Message, "Mensajes") { navController.navigate("mensajes") }
        MenuItem(Icons.Default.Edit, "Editar Perfil") { navController.navigate("editar_perfil") }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = BklnStroke, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))

        // ── SECCIÓN: Estadísticas ────────────────────────────────────────
        MenuSection("Estadísticas")
        MenuItemVerPlus(Icons.Default.Visibility, "Visitas")
        MenuItem(Icons.Default.Star, "Reseñas")
        MenuItem(Icons.Default.Group, "Seguidores")

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = BklnStroke, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))

        // ── SECCIÓN: Cuenta ──────────────────────────────────────────────
        MenuSection("Cuenta")
        MenuItem(Icons.Default.Bookmark, "Mis Seguidos")
        MenuItem(Icons.Default.Settings, "Configuración")
        MenuItemPlanesYPrecios { navController.navigate("planes") }

        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = BklnStroke, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))

        // ── SECCIÓN: Acerca de ───────────────────────────────────────────
        MenuSection("Acerca de")
        MenuItemLink(Icons.Default.Info,        "¿Cómo funciona?",  "https://bklnmarketplace.com/como-funciona.html")
        MenuItemLink(Icons.Default.Description, "Términos de uso",  "https://bklnmarketplace.com/terminos.html")
        MenuItemLink(Icons.Default.Lock,        "Privacidad",       "https://bklnmarketplace.com/privacidad.html")
        MenuItemLink(Icons.Default.WorkspacePremium, "Planes y precios", "https://bklnmarketplace.com/planes.html")
        MenuItemEmail("Contacto", "soporte@bklnmarketplace.com")

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = BklnStroke)
        Spacer(Modifier.height(4.dp))

        // ── CERRAR SESIÓN ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    scope.launch {
                        SupabaseClient.client.auth.signOut()
                        navController.navigate("login") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Logout, contentDescription = null, tint = BklnError, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Cerrar sesión", color = BklnError, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ── COMPONENTES ───────────────────────────────────────────────────────────────

@Composable
private fun FollowStat(count: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, color = BklnWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = BklnMuted, fontSize = 11.sp, letterSpacing = 0.5.sp)
    }
}

@Composable
private fun MenuSection(title: String) {
    Text(
        text = title.uppercase(),
        color = BklnMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun MenuItem(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = BklnWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuItemWithBadge(icon: ImageVector, label: String, badge: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = BklnWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(BklnOrange)
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(badge, color = BklnWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuItemPremium(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = BklnWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x26F5C842))
                .border(1.dp, Color(0x4DF5C842), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("PREMIUM", color = BklnYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuItemVerPlus(icon: ImageVector, label: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = BklnWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x1F3DDC97))
                .border(1.dp, Color(0x403DDC97), RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("VER+", color = BklnSuccess, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuItemPlanesYPrecios(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = BklnYellow, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("Planes y precios", color = BklnWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun MenuItemLink(icon: ImageVector, label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = BklnWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun MenuItemEmail(label: String, email: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email"))
                context.startActivity(Intent.createChooser(intent, null))
            }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Email, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = BklnWhite, fontSize = 14.sp)
            Text(email, color = BklnMuted, fontSize = 11.sp)
        }
        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = BklnMuted, modifier = Modifier.size(14.dp))
    }
}
