package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Message
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
private data class MessageInsert(
    @SerialName("sender_id") val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    val content: String,
    @SerialName("product_id") val productId: String? = null,
)

@Serializable
private data class ReadUpdate(val read: Boolean = true)

@Composable
fun ChatScreen(navController: NavController, otherUserId: String) {
    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val listState    = rememberLazyListState()

    val currentUserId = remember { SupabaseClient.client.auth.currentUserOrNull()?.id ?: "" }
    var otherProfile  by remember { mutableStateOf<Profile?>(null) }
    var messages      by remember { mutableStateOf<List<Message>>(emptyList()) }
    var input         by remember { mutableStateOf("") }
    var sending       by remember { mutableStateOf(false) }
    var loading       by remember { mutableStateOf(true) }

    fun isRelevant(msg: Message) =
        (msg.senderId == currentUserId && msg.receiverId == otherUserId) ||
        (msg.senderId == otherUserId   && msg.receiverId == currentUserId)

    suspend fun markAsRead() {
        try {
            SupabaseClient.client.postgrest
                .from("messages")
                .update(ReadUpdate()) {
                    filter {
                        eq("sender_id", otherUserId)
                        eq("receiver_id", currentUserId)
                        eq("read", false)
                    }
                }
        } catch (_: Exception) {}
    }

    // Load other user's profile + messages
    LaunchedEffect(otherUserId) {
        try {
            otherProfile = SupabaseClient.client.postgrest
                .from("profiles")
                .select(columns = Columns.raw("id, full_name, avatar_url, plan")) {
                    filter { eq("id", otherUserId) }
                    limit(1)
                }
                .decodeList<Profile>()
                .firstOrNull()

            messages = SupabaseClient.client.postgrest
                .from("messages")
                .select(columns = Columns.raw("id, sender_id, receiver_id, content, read, created_at, product_id")) {
                    filter {
                        or {
                            eq("sender_id", currentUserId)
                            eq("receiver_id", currentUserId)
                        }
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<Message>()
                .filter { isRelevant(it) }

            markAsRead()
        } catch (_: Exception) {}
        loading = false
    }

    // Realtime subscription
    DisposableEffect(otherUserId) {
        val rtChannel = SupabaseClient.client.channel("chat-$currentUserId-$otherUserId")

        val job = rtChannel
            .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
            }
            .onEach { action ->
                val msg = action.decodeRecord<Message>()
                if (isRelevant(msg) && messages.none { it.id == msg.id }) {
                    messages = messages + msg
                    if (msg.senderId == otherUserId) markAsRead()
                }
            }
            .launchIn(scope)

        scope.launch { rtChannel.subscribe() }

        onDispose {
            job.cancel()
            scope.launch { rtChannel.unsubscribe() }
        }
    }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun sendMessage() {
        val text = input.trim()
        if (text.isBlank() || sending || currentUserId.isBlank()) return
        scope.launch {
            sending = true
            focusManager.clearFocus()
            try {
                SupabaseClient.client.postgrest
                    .from("messages")
                    .insert(MessageInsert(senderId = currentUserId, receiverId = otherUserId, content = text))
                input = ""
                // Reload to get db timestamp/id
                messages = SupabaseClient.client.postgrest
                    .from("messages")
                    .select(columns = Columns.raw("id, sender_id, receiver_id, content, read, created_at, product_id")) {
                        filter {
                            or {
                                eq("sender_id", currentUserId)
                                eq("receiver_id", currentUserId)
                            }
                        }
                        order("created_at", Order.ASCENDING)
                    }
                    .decodeList<Message>()
                    .filter { isRelevant(it) }
            } catch (_: Exception) {}
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {

        // ── HEADER ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = BklnWhite)
            }
            val avatar = otherProfile?.avatarUrl
            if (!avatar.isNullOrBlank()) {
                AsyncImage(
                    model = avatar, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(BklnBgInput)
                )
            } else {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(BklnBgInput),
                    contentAlignment = Alignment.Center
                ) { Text("👤", fontSize = 16.sp) }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                otherProfile?.fullName ?: "...",
                color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = BklnStroke)

        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BklnOrange)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(40.dp))
                                Text("💬", fontSize = 36.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Empieza la conversación", color = BklnMuted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(msg, isOwn = msg.senderId == currentUserId)
                    }
                }
            }
        }

        // ── INPUT BAR ────────────────────────────────────────────────────────
        HorizontalDivider(color = BklnStroke)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Escribe un mensaje...", color = BklnMuted, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor     = BklnOrange,
                    unfocusedBorderColor   = BklnStroke,
                    focusedTextColor       = BklnWhite,
                    unfocusedTextColor     = BklnWhite,
                    cursorColor            = BklnOrange,
                    focusedContainerColor  = BklnBgInput,
                    unfocusedContainerColor = BklnBgInput,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
            )
            IconButton(
                onClick = { sendMessage() },
                enabled = input.isNotBlank() && !sending,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank()) BklnOrange else BklnBgInput)
            ) {
                if (sending) {
                    CircularProgressIndicator(color = BklnWhite, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Send, contentDescription = "Enviar", tint = BklnWhite, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: Message, isOwn: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isOwn) 16.dp else 4.dp,
                        bottomEnd   = if (isOwn) 4.dp else 16.dp
                    )
                )
                .background(if (isOwn) BklnOrange else BklnBgCard)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
        ) {
            Text(message.content, color = BklnWhite, fontSize = 14.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                formatMsgTime(message.createdAt),
                color = if (isOwn) BklnWhite.copy(alpha = 0.7f) else BklnMuted,
                fontSize = 10.sp
            )
        }
    }
}

private fun formatMsgTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return try {
        val sdf  = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(iso.take(19)) ?: return ""
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } catch (_: Exception) { "" }
}
