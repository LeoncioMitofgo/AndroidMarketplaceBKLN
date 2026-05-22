package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.models.Profile
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// ── DATA CLASSES ──────────────────────────────────────────────────────────────

@Serializable
private data class SubscriptionRequest(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val plan: String = "",
    @SerialName("billing_cycle") val billingCycle: String = "monthly",
    val amount: Int = 0,
    val notes: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class SubscriptionRequestInsert(
    @SerialName("user_id") val userId: String,
    val plan: String,
    @SerialName("billing_cycle") val billingCycle: String,
    val amount: Int,
    val notes: String? = null,
    val status: String = "pending",
)

@Serializable
private data class ActivationCode(
    val id: String = "",
    val code: String = "",
    @SerialName("assigned_to") val assignedTo: String = "",
    val plan: String = "",
    @SerialName("billing_cycle") val billingCycle: String = "monthly",
    @SerialName("is_used") val isUsed: Boolean = false,
)

@Serializable
private data class ActivateResult(
    val success: Boolean = false,
    val plan: String? = null,
    val error: String? = null,
)

// ── SCREEN ────────────────────────────────────────────────────────────────────

@Composable
fun PlanesScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var userPlan      by remember { mutableStateOf("free") }
    var currentUserId by remember { mutableStateOf("") }

    var pendingRequest    by remember { mutableStateOf<SubscriptionRequest?>(null) }
    var readyCode         by remember { mutableStateOf<ActivationCode?>(null) }

    var isAnnual          by remember { mutableStateOf(false) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var requestNotes      by remember { mutableStateOf("") }
    var requestLoading    by remember { mutableStateOf(false) }

    var codeInput     by remember { mutableStateOf("") }
    var codeLoading   by remember { mutableStateOf(false) }
    var codeMessage   by remember { mutableStateOf("") }
    var codeIsSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return@LaunchedEffect
        currentUserId = userId
        try {
            val profile = SupabaseClient.client.postgrest
                .from("profiles")
                .select(columns = Columns.raw("plan")) {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeSingleOrNull<Profile>()
            userPlan = profile?.plan ?: "free"
        } catch (_: Exception) {}

        try {
            val requests = SupabaseClient.client.postgrest
                .from("subscription_requests")
                .select {
                    filter { eq("user_id", userId) }
                    order("created_at", Order.DESCENDING)
                    limit(5)
                }
                .decodeList<SubscriptionRequest>()
            pendingRequest = requests.firstOrNull { it.status == "pending" || it.status == "approved" }
        } catch (_: Exception) {}

        try {
            val codes = SupabaseClient.client.postgrest
                .from("activation_codes")
                .select {
                    filter { eq("assigned_to", userId); eq("is_used", false) }
                    limit(1)
                }
                .decodeList<ActivationCode>()
            readyCode = codes.firstOrNull()
            if (readyCode != null && codeInput.isBlank()) codeInput = readyCode!!.code
        } catch (_: Exception) {}
    }

    fun submitRequest() {
        if (requestLoading || currentUserId.isBlank()) return
        scope.launch {
            requestLoading = true
            try {
                val amount = if (isAnnual) 29400 else 3500
                val cycle  = if (isAnnual) "annual" else "monthly"
                SupabaseClient.client.postgrest.from("subscription_requests").insert(
                    SubscriptionRequestInsert(
                        userId       = currentUserId,
                        plan         = "premium",
                        billingCycle = cycle,
                        amount       = amount,
                        notes        = requestNotes.trim().ifBlank { null },
                    )
                )
                pendingRequest = SubscriptionRequest(
                    userId = currentUserId, plan = "premium",
                    billingCycle = cycle, amount = amount, status = "pending"
                )
                showRequestDialog = false
                requestNotes = ""
            } catch (_: Exception) {}
            requestLoading = false
        }
    }

    fun activateCode() {
        val code = codeInput.trim().uppercase()
        if (code.isBlank() || codeLoading || currentUserId.isBlank()) return
        scope.launch {
            codeLoading = true
            codeMessage = ""
            try {
                val result = SupabaseClient.client.postgrest
                    .rpc("activate_plan_with_code", buildJsonObject {
                        put("p_code", code)
                        put("p_user_id", currentUserId)
                    })
                    .decodeSingle<ActivateResult>()
                if (result.success) {
                    userPlan      = result.plan ?: "premium"
                    pendingRequest = null
                    readyCode     = null
                    codeIsSuccess = true
                    codeMessage   = "✓ ¡Plan Premium activado exitosamente!"
                    codeInput     = ""
                } else {
                    codeIsSuccess = false
                    codeMessage   = "✗ ${result.error ?: "Código inválido o ya utilizado"}"
                }
            } catch (_: Exception) {
                codeIsSuccess = false
                codeMessage   = "✗ Código inválido o ya utilizado"
            }
            codeLoading = false
        }
    }

    val isPremium = userPlan == "premium"

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {

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
            Text("PLANES", color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(
                " Y PRECIOS", color = BklnOrange, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = BklnStroke)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── ACTIVE PLAN BANNER ────────────────────────────────────────────
            if (isPremium) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1AF5C842))
                        .border(1.dp, Color(0x4DF5C842), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("👑", fontSize = 20.sp)
                        Column {
                            Text("Plan activo: PREMIUM", color = BklnYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Tienes acceso a todas las funciones.", color = BklnMuted, fontSize = 12.sp)
                        }
                    }
                }
            }

            // ── PENDING REQUEST BANNER ────────────────────────────────────────
            val req = pendingRequest
            if (req != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1A3DDC97))
                        .border(1.dp, Color(0x4D3DDC97), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(if (req.status == "approved") "✅" else "⏳", fontSize = 18.sp)
                            Text(
                                if (req.status == "approved") "Solicitud aprobada" else "Solicitud pendiente",
                                color = BklnSuccess, fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            if (req.status == "approved")
                                "Tu pago fue confirmado. Recibirás tu código de activación muy pronto."
                            else
                                "Revisaremos tu solicitud. Una vez confirmado el pago, recibirás tu código para activar el plan.",
                            color = BklnMuted, fontSize = 12.sp, lineHeight = 16.sp
                        )
                    }
                }
            }

            // ── READY CODE BANNER ─────────────────────────────────────────────
            val rc = readyCode
            if (rc != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1AF5C842))
                        .border(1.dp, Color(0x66F5C842), RoundedCornerShape(8.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚡", fontSize = 18.sp)
                            Text("¡Tu código está listo!", color = BklnYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Tu código de activación para el plan Premium ha sido generado. Tócalo para copiarlo al campo de activación.",
                            color = BklnMuted, fontSize = 12.sp, lineHeight = 16.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x1AFFFFFF))
                                .border(1.dp, Color(0x33F5C842), RoundedCornerShape(6.dp))
                                .clickable { codeInput = rc.code }
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                rc.code, color = BklnYellow,
                                fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.sp
                            )
                        }
                        Text("↓ Toca para rellenar el campo de activación abajo", color = BklnOrange, fontSize = 10.sp, letterSpacing = 0.5.sp)
                    }
                }
            }

            // ── BILLING TOGGLE ────────────────────────────────────────────────
            if (!isPremium) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BklnBgCard)
                        .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Facturación", color = BklnMuted, fontSize = 11.sp)
                        Text(
                            if (isAnnual) "Anual — ahorras 12.600 XAF" else "Mensual",
                            color = if (isAnnual) BklnSuccess else BklnWhite,
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Mensual", color = if (!isAnnual) BklnWhite else BklnMuted, fontSize = 12.sp)
                        Switch(
                            checked = isAnnual,
                            onCheckedChange = { isAnnual = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor   = BklnYellow,
                                checkedTrackColor   = Color(0x4DF5C842),
                                uncheckedThumbColor = BklnMuted,
                                uncheckedTrackColor = BklnBgInput
                            )
                        )
                        Text("Anual", color = if (isAnnual) BklnYellow else BklnMuted, fontSize = 12.sp)
                    }
                }
            }

            // ── PLAN CARDS ────────────────────────────────────────────────────
            PlanCard(
                icon = "🆓", name = "Gratuito",
                price = "0 XAF", period = "siempre gratis",
                isCurrent = !isPremium, isHighlighted = false,
                features = listOf(
                    true  to "Hasta 15 productos",
                    true  to "3 fotos por producto",
                    true  to "Chat con compradores",
                    true  to "Reseñas y seguidores",
                    true  to "Posición normal en búsquedas",
                    false to "Badge Premium dorado 👑",
                    false to "Publicar ofertas / rebajas",
                    false to "Catálogos / colecciones",
                    false to "Prioridad en listados",
                )
            )

            PlanCard(
                icon = "👑", name = "Premium",
                price = "${if (isAnnual) "29.400" else "3.500"} XAF",
                period = if (isAnnual) "/ año" else "/ mes",
                isCurrent = isPremium, isHighlighted = true,
                features = listOf(
                    true to "Productos ilimitados",
                    true to "10 fotos por producto",
                    true to "Chat con compradores",
                    true to "Reseñas y seguidores",
                    true to "Prioridad absoluta en búsquedas 🚀",
                    true to "Badge Premium dorado 👑",
                    true to "Publicar ofertas con precio tachado 🏷️",
                    true to "Catálogos / colecciones 📁",
                    true to "Destacado en página principal",
                    true to "Soporte prioritario 24/7",
                ),
                cta        = if (!isPremium && pendingRequest == null) "Solicitar plan →" else null,
                statusLabel = if (!isPremium && pendingRequest != null) "⏳ Solicitud enviada" else null,
                onCta      = { if (currentUserId.isNotBlank()) showRequestDialog = true }
            )

            // ── ACTIVATE CODE SECTION ─────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BklnBgCard)
                    .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "// activar suscripción", color = BklnOrange,
                    fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    "INGRESA TU CÓDIGO", color = BklnWhite,
                    fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
                )
                Text(
                    "Si tienes un código de activación, introdúcelo aquí para activar tu plan al instante.",
                    color = BklnMuted, fontSize = 12.sp, lineHeight = 16.sp
                )
                OutlinedTextField(
                    value        = codeInput,
                    onValueChange = { if (it.length <= 20) codeInput = it.uppercase() },
                    placeholder  = { Text("BKLN-XXXX-XXXXXX", color = BklnMuted, fontSize = 13.sp) },
                    modifier     = Modifier.fillMaxWidth(),
                    singleLine   = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = BklnOrange,
                        unfocusedBorderColor = BklnStroke,
                        focusedTextColor     = BklnWhite,
                        unfocusedTextColor   = BklnWhite,
                        cursorColor          = BklnOrange
                    )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (codeInput.isNotBlank() && !codeLoading) BklnOrange else BklnBgInput)
                        .clickable(enabled = codeInput.isNotBlank() && !codeLoading) { activateCode() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (codeLoading) {
                        CircularProgressIndicator(color = BklnWhite, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            "Activar plan",
                            color = if (codeInput.isNotBlank()) BklnWhite else BklnMuted,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (codeMessage.isNotBlank()) {
                    Text(
                        codeMessage,
                        color = if (codeIsSuccess) BklnSuccess else Color(0xFFFF5252),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    // ── REQUEST DIALOG ────────────────────────────────────────────────────────
    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            containerColor   = BklnBgCard,
            titleContentColor = BklnWhite,
            title = { Text("Solicitar Plan Premium", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x1AF5C842))
                            .border(1.dp, Color(0x33F5C842), RoundedCornerShape(6.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("👑 Premium", color = BklnYellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(if (isAnnual) "Anual" else "Mensual", color = BklnMuted, fontSize = 11.sp)
                        }
                        Text(
                            "${if (isAnnual) "29.400" else "3.500"} XAF",
                            color = BklnWhite, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        "Te contactaremos con los detalles de pago. Una vez confirmado, recibirás tu código de activación.",
                        color = BklnMuted, fontSize = 12.sp, lineHeight = 16.sp
                    )
                    OutlinedTextField(
                        value = requestNotes,
                        onValueChange = { requestNotes = it },
                        placeholder = {
                            Text("Notas adicionales (opcional)...", color = BklnMuted, fontSize = 12.sp)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = BklnOrange,
                            unfocusedBorderColor = BklnStroke,
                            focusedTextColor     = BklnWhite,
                            unfocusedTextColor   = BklnWhite,
                            cursorColor          = BklnOrange
                        ),
                        maxLines = 3,
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick  = { submitRequest() },
                    enabled  = !requestLoading
                ) {
                    if (requestLoading) {
                        CircularProgressIndicator(color = BklnOrange, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Enviar solicitud", color = BklnOrange, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDialog = false; requestNotes = "" }) {
                    Text("Cancelar", color = BklnMuted)
                }
            }
        )
    }
}

// ── COMPOSABLES ───────────────────────────────────────────────────────────────

@Composable
private fun PlanCard(
    icon: String,
    name: String,
    price: String,
    period: String,
    isCurrent: Boolean,
    isHighlighted: Boolean,
    features: List<Pair<Boolean, String>>,
    cta: String? = null,
    statusLabel: String? = null,
    onCta: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BklnBgCard)
            .border(1.dp, if (isHighlighted) Color(0x4DF5C842) else BklnStroke, RoundedCornerShape(12.dp))
    ) {
        if (isHighlighted) {
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0x1AF5C842)).padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "RECOMENDADO", color = BklnYellow,
                    fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp
                )
            }
        }
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(icon, fontSize = 30.sp)
                Column {
                    Text(
                        name,
                        color = if (isHighlighted) BklnYellow else BklnWhite,
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(price, color = BklnWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(period, color = BklnMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            features.forEach { (included, label) ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (included) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (included) BklnSuccess else BklnMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(label, color = if (included) BklnWhite else BklnMuted, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(20.dp))

            when {
                cta != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnYellow)
                            .clickable { onCta() }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cta, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                statusLabel != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnBgInput)
                            .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(statusLabel, color = BklnMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                isCurrent -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(BklnBgInput)
                            .border(1.dp, BklnStroke, RoundedCornerShape(6.dp))
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓ Tu plan actual", color = BklnMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
