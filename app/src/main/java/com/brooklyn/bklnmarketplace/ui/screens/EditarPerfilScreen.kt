package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class ProfileUpdate(
    @SerialName("full_name") val fullName: String,
    val username: String,
    val bio: String?,
    val whatsapp: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
)

@Composable
fun EditarPerfilScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var fullName   by remember { mutableStateOf("") }
    var username   by remember { mutableStateOf("") }
    var bio        by remember { mutableStateOf("") }
    var whatsapp   by remember { mutableStateOf("") }
    var avatarUrl  by remember { mutableStateOf("") }

    var loading    by remember { mutableStateOf(true) }
    var saving     by remember { mutableStateOf(false) }
    var saved      by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf("") }
    var userId     by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: ""
        if (userId.isBlank()) { loading = false; return@LaunchedEffect }
        try {
            data class RawProfile(
                @SerialName("full_name") val fn: String? = null,
                val username: String? = null,
                val bio: String? = null,
                val whatsapp: String? = null,
                @SerialName("avatar_url") val av: String? = null,
            )
            @Serializable
            data class RawProfileS(
                @SerialName("full_name") val fn: String? = null,
                val username: String? = null,
                val bio: String? = null,
                val whatsapp: String? = null,
                @SerialName("avatar_url") val av: String? = null,
            )
            val p = SupabaseClient.client.postgrest
                .from("profiles")
                .select {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<RawProfileS>()
                .firstOrNull()
            fullName  = p?.fn ?: ""
            username  = p?.username ?: ""
            bio       = p?.bio ?: ""
            whatsapp  = p?.whatsapp ?: ""
            avatarUrl = p?.av ?: ""
        } catch (_: Exception) {}
        loading = false
    }

    fun save() {
        if (saving) return
        errorMsg = ""
        scope.launch {
            saving = true
            try {
                SupabaseClient.client.postgrest
                    .from("profiles")
                    .update(ProfileUpdate(
                        fullName  = fullName.trim(),
                        username  = username.trim(),
                        bio       = bio.trim().ifBlank { null },
                        whatsapp  = whatsapp.trim().ifBlank { null },
                        avatarUrl = avatarUrl.trim().ifBlank { null },
                    )) {
                        filter { eq("id", userId) }
                    }
                saved = true
            } catch (_: Exception) {
                errorMsg = "Error al guardar. Inténtalo de nuevo."
            }
            saving = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BklnBgDark)
    ) {
        // ── TOP BAR ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BklnBgCard)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = BklnWhite)
            }
            Text(
                "EDITAR PERFIL",
                color = BklnWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { save() },
                enabled = !saving && !loading
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = BklnOrange,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Default.Check,
                        "Guardar",
                        tint = if (saved) BklnSuccess else BklnOrange
                    )
                }
            }
        }

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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── AVATAR PREVIEW ────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(BklnBgInput)
                        .border(2.dp, BklnOrange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            fullName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            color = BklnWhite,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Foto de perfil", color = BklnMuted, fontSize = 12.sp)
            }

            // ── CAMPOS ────────────────────────────────────────────────────
            ProfileField(
                label     = "URL de foto de perfil",
                value     = avatarUrl,
                onValueChange = { avatarUrl = it; saved = false },
                placeholder   = "https://...",
                keyboardType  = KeyboardType.Uri,
            )

            HorizontalDivider(color = BklnStroke)

            ProfileField(
                label     = "Nombre completo",
                value     = fullName,
                onValueChange = { fullName = it; saved = false },
                placeholder   = "Tu nombre",
                capitalization = KeyboardCapitalization.Words,
            )
            ProfileField(
                label     = "Nombre de usuario",
                value     = username,
                onValueChange = { v ->
                    // lowercase, no spaces
                    username = v.lowercase().filter { it.isLetterOrDigit() || it == '_' || it == '.' }
                    saved = false
                },
                placeholder = "tu_usuario",
                prefix      = "@",
                keyboardType = KeyboardType.Ascii,
            )
            ProfileField(
                label         = "Biografía",
                value         = bio,
                onValueChange = { bio = it; saved = false },
                placeholder   = "Cuéntanos algo sobre ti…",
                maxLines      = 4,
                capitalization = KeyboardCapitalization.Sentences,
            )
            ProfileField(
                label     = "WhatsApp",
                value     = whatsapp,
                onValueChange = { whatsapp = it; saved = false },
                placeholder   = "+34 600 000 000",
                keyboardType  = KeyboardType.Phone,
                helper        = "Los compradores te contactarán aquí desde el producto.",
            )

            // ── GUARDAR ───────────────────────────────────────────────────
            if (errorMsg.isNotBlank()) {
                Text(errorMsg, color = BklnError, fontSize = 12.sp)
            }

            if (saved) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Check, null, tint = BklnSuccess, modifier = Modifier.size(16.dp))
                    Text("Cambios guardados", color = BklnSuccess, fontSize = 13.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (saving) BklnBgInput else BklnOrange)
                    .clickable(enabled = !saving) { save() }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = BklnWhite,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        "Guardar cambios",
                        color = BklnWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── COMPONENTE DE CAMPO ───────────────────────────────────────────────────────

@Composable
private fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    prefix: String? = null,
    maxLines: Int = 1,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            label,
            color = BklnMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = BklnMuted, fontSize = 14.sp) },
            prefix = prefix?.let { { Text(it, color = BklnMuted, fontSize = 14.sp) } },
            maxLines = maxLines,
            minLines = if (maxLines > 1) 3 else 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor    = BklnOrange,
                unfocusedBorderColor  = BklnStroke,
                focusedTextColor      = BklnWhite,
                unfocusedTextColor    = BklnWhite,
                cursorColor           = BklnOrange,
                focusedContainerColor    = BklnBgInput,
                unfocusedContainerColor  = BklnBgInput,
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            shape = RoundedCornerShape(8.dp),
        )
        if (!helper.isNullOrBlank()) {
            Text(helper, color = BklnMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}
