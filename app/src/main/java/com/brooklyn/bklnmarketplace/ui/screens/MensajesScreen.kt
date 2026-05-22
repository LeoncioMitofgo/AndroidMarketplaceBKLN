package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.R
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Message
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.text.SimpleDateFormat
import java.util.Locale

private data class ConvPreview(
    val otherId: String,
    val otherProfile: Profile?,
    val lastMessage: Message,
    val unreadCount: Int,
)

@Composable
fun MensajesScreen(navController: NavController) {
    val currentUserId = remember { SupabaseClient.client.auth.currentUserOrNull()?.id ?: "" }
    var convPreviews by remember { mutableStateOf<List<ConvPreview>>(emptyList()) }
    var loading      by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (currentUserId.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            // Load all messages involving current user
            val allMessages = SupabaseClient.client.postgrest
                .from("messages")
                .select(columns = Columns.raw("id, sender_id, receiver_id, content, read, created_at, product_id")) {
                    filter {
                        or {
                            eq("sender_id", currentUserId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                    order("created_at", Order.DESCENDING)
                }
                .decodeList<Message>()

            // Group by the other participant
            val grouped = linkedMapOf<String, MutableList<Message>>()
            allMessages.forEach { msg ->
                val otherId = if (msg.senderId == currentUserId) msg.receiverId else msg.senderId
                grouped.getOrPut(otherId) { mutableListOf() }.add(msg)
            }

            // Fetch profiles for each unique other participant
            val previews = mutableListOf<ConvPreview>()
            for ((otherId, msgs) in grouped) {
                val profile = try {
                    SupabaseClient.client.postgrest
                        .from("profiles")
                        .select(columns = Columns.raw("id, full_name, avatar_url, plan")) {
                            filter { eq("id", otherId) }
                            limit(1)
                        }
                        .decodeList<Profile>()
                        .firstOrNull()
                } catch (_: Exception) { null }

                val unread = msgs.count { !it.read && it.receiverId == currentUserId }
                previews.add(ConvPreview(otherId, profile, msgs.first(), unread))
            }
            convPreviews = previews
        } catch (_: Exception) {}
        loading = false
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
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("BKLN ", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("MENSAJES", color = BklnOrange, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        }
        HorizontalDivider(color = BklnStroke)

        if (!loading && currentUserId.isBlank()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Inicia sesión para ver tus mensajes", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnOrange)
                            .clickable { navController.navigate("login") }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    ) { Text("Entrar →", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
            return@Column
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
            return@Column
        }

        if (convPreviews.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Sin mensajes aún", color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Escribe al vendedor desde la página de un producto.", color = BklnMuted, fontSize = 13.sp)
                }
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(convPreviews, key = { it.otherId }) { conv ->
                ConversationRow(conv) {
                    navController.navigate("chat/${conv.otherId}")
                }
                HorizontalDivider(color = BklnStroke, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: ConvPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val avatar = conv.otherProfile?.avatarUrl
        Box {
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(BklnBgInput)
                )
            } else {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(BklnBgInput),
                    contentAlignment = Alignment.Center
                ) { Text("👤", fontSize = 20.sp) }
            }
            if (conv.unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(BklnOrange),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (conv.unreadCount > 9) "9+" else conv.unreadCount.toString(),
                        color = BklnWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                conv.otherProfile?.fullName ?: "Usuario",
                color = BklnWhite,
                fontSize = 14.sp,
                fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val preview = conv.lastMessage.content
            Text(
                preview,
                color = if (conv.unreadCount > 0) BklnWhite else BklnMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val ts = conv.lastMessage.createdAt
        if (!ts.isNullOrBlank()) {
            Text(formatConvTime(ts), color = BklnMuted, fontSize = 10.sp)
        }
    }
}

private fun formatConvTime(iso: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(iso.take(19)) ?: return ""
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
    } catch (_: Exception) { "" }
}
