package com.brooklyn.bklnmarketplace.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.brooklyn.bklnmarketplace.R
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.ui.components.BklnTextField
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.Phone
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val SUPABASE_PROJECT_URL = "https://uptnfmamtceqmrzckisc.supabase.co"

@Composable
fun LoginScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    var isLoginTab by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (SupabaseClient.client.auth.currentUserOrNull() != null) {
            navController.navigate("home") { popUpTo("login") { inclusive = true } }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BklnBgCard)
            .verticalScroll(rememberScrollState())
    ) {
        // ── HEADER ──────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = "BKLN",
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row {
                Text("COMPRA. ", color = BklnWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text("VENDE.", color = BklnOrange, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Text(" CONECTA.", color = BklnWhite, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text("Donde los mejores vendedores ya están.", color = BklnMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = BklnStroke)
        }

        // ── AUTH BOX ─────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("BIENVENIDO", color = BklnWhite, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
            Spacer(Modifier.height(4.dp))
            Text("Accede a tu cuenta o crea una nueva", color = BklnMuted, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BklnBgInput)
                    .padding(4.dp)
            ) {
                BklnTab("Iniciar sesión", isLoginTab) { isLoginTab = true }
                BklnTab("Registrarse", !isLoginTab) { isLoginTab = false }
            }

            Spacer(Modifier.height(16.dp))

            val onSuccess = {
                navController.navigate("home") { popUpTo("login") { inclusive = true } }
            }

            if (isLoginTab) {
                LoginForm(
                    onSuccess = onSuccess,
                    onForgotPassword = { navController.navigate("recuperar_contrasena") }
                )
            } else {
                RegisterForm(onSuccess = { isLoginTab = true })
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Al continuar aceptas nuestros Términos y Privacidad",
                color = BklnMuted, fontSize = 11.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── TAB ──────────────────────────────────────────────────────────────────────

@Composable
private fun RowScope.BklnTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f).fillMaxHeight()
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) BklnBgGray3 else Color.Transparent)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) BklnWhite else BklnMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ── METHOD SELECTOR ───────────────────────────────────────────────────────────

@Composable
private fun MethodSelector(active: String, onEmail: () -> Unit, onPhone: () -> Unit, onGoogle: () -> Unit) {
    val methods = listOf(
        Triple("✉", "Email", onEmail),
        Triple("📱", "Teléfono", onPhone),
        Triple("G", "Google", onGoogle),
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        methods.forEach { (icon, label, action) ->
            val id = label.lowercase().trimStart('é').let {
                when (label) {
                    "Email"    -> "email"
                    "Teléfono" -> "phone"
                    else       -> "google"
                }
            }
            val isActive = active == id
            Column(
                modifier = Modifier
                    .weight(1f).height(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isActive) BklnAccentTint else BklnBgInput)
                    .border(1.dp, if (isActive) BklnOrange else BklnStroke, RoundedCornerShape(6.dp))
                    .clickable { action() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 14.sp,
                    color = if (id == "google") Color(0xFFEA4335) else Color.Unspecified
                )
                Text(
                    text = label,
                    color = if (isActive) BklnWhite else BklnMuted,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── LOGIN FORM ────────────────────────────────────────────────────────────────

@Composable
private fun LoginForm(onSuccess: () -> Unit, onForgotPassword: () -> Unit = {}) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    var method      by remember { mutableStateOf("email") }

    // Email fields
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    // Phone fields
    var phone       by remember { mutableStateOf("") }
    var phoneStep   by remember { mutableIntStateOf(1) }
    var otpCode     by remember { mutableStateOf("") }

    var error   by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    MethodSelector(
        active   = method,
        onEmail  = { method = "email"; error = "" },
        onPhone  = { method = "phone"; error = "" },
        onGoogle = {
            val url = "$SUPABASE_PROJECT_URL/auth/v1/authorize?provider=google" +
                      "&redirect_to=bklnmarketplace%3A%2F%2Fauth%2Fcallback"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    )

    when (method) {
        "email" -> {
            FormLabel("CORREO ELECTRÓNICO")
            BklnTextField(value = email, onValueChange = { email = it }, placeholder = "tu@correo.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(14.dp))
            FormLabel("CONTRASEÑA")
            BklnTextField(
                value = password, onValueChange = { password = it }, placeholder = "••••••••",
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = BklnMuted)
                    }
                }
            )
            Text("¿Olvidaste tu contraseña?", color = BklnOrange, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 16.dp)
                    .wrapContentWidth(Alignment.End)
                    .clickable { onForgotPassword() })
            if (error.isNotBlank()) Text(error, color = BklnError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            BklnButton(text = if (loading) "Cargando..." else "Iniciar sesión →", enabled = !loading) {
                if (email.isBlank() || password.isBlank()) { error = "Completa todos los campos."; return@BklnButton }
                loading = true; error = ""
                scope.launch {
                    try {
                        SupabaseClient.client.auth.signInWith(Email) {
                            this.email = email.trim(); this.password = password
                        }
                        onSuccess()
                    } catch (e: Exception) {
                        error = if (e.message?.contains("invalid_credentials") == true)
                            "Correo o contraseña incorrectos." else "Error al iniciar sesión."
                    } finally { loading = false }
                }
            }
        }

        "phone" -> {
            if (phoneStep == 1) {
                FormLabel("NÚMERO DE TELÉFONO")
                BklnTextField(
                    value = phone, onValueChange = { phone = it },
                    placeholder = "+240 555 000 000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Text("Incluye el código de país, ej: +240 (Guinea Ecuatorial)", color = BklnMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                if (error.isNotBlank()) Text(error, color = BklnError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                BklnButton(text = if (loading) "Enviando..." else "Enviar código SMS →", enabled = !loading) {
                    val trimmed = phone.trim().replace(" ", "")
                    if (trimmed.length < 8) { error = "Ingresa un número válido con código de país."; return@BklnButton }
                    loading = true; error = ""
                    scope.launch {
                        try {
                            SupabaseClient.client.auth.signInWith(Phone) { phone = trimmed }
                            phoneStep = 2
                        } catch (e: Exception) {
                            error = "Error al enviar SMS. Verifica el número."
                        } finally { loading = false }
                    }
                }
            } else {
                Text("← Cambiar número", color = BklnOrange, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp).clickable { phoneStep = 1; otpCode = ""; error = "" })
                Text("Ingresa el código de 6 dígitos enviado a $phone", color = BklnMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 14.dp))
                FormLabel("CÓDIGO DE VERIFICACIÓN")
                BklnTextField(
                    value = otpCode, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                    placeholder = "123456",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(16.dp))
                if (error.isNotBlank()) Text(error, color = BklnError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                BklnButton(text = if (loading) "Verificando..." else "Verificar código →", enabled = !loading) {
                    if (otpCode.length < 6) { error = "Ingresa el código completo de 6 dígitos."; return@BklnButton }
                    loading = true; error = ""
                    scope.launch {
                        try {
                            val trimmed = phone.trim().replace(" ", "")
                            SupabaseClient.client.auth.verifyPhoneOtp(
                                phone = trimmed, token = otpCode, type = OtpType.Phone.SMS
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            error = "Código incorrecto o expirado."
                        } finally { loading = false }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("¿No llegó el código? Vuelve atrás y reintenta.", color = BklnMuted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── REGISTER FORM ─────────────────────────────────────────────────────────────

@Composable
private fun RegisterForm(onSuccess: () -> Unit) {
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    var method      by remember { mutableStateOf("email") }

    // Email fields
    var name        by remember { mutableStateOf("") }
    var email       by remember { mutableStateOf("") }
    var password    by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var termsChecked by remember { mutableStateOf(false) }

    // Phone fields
    var regName     by remember { mutableStateOf("") }
    var phone       by remember { mutableStateOf("") }
    var phoneStep   by remember { mutableIntStateOf(1) }
    var otpCode     by remember { mutableStateOf("") }

    var error   by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    MethodSelector(
        active   = method,
        onEmail  = { method = "email"; error = "" },
        onPhone  = { method = "phone"; error = "" },
        onGoogle = {
            val url = "$SUPABASE_PROJECT_URL/auth/v1/authorize?provider=google" +
                      "&redirect_to=bklnmarketplace%3A%2F%2Fauth%2Fcallback"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    )

    when (method) {
        "email" -> {
            FormLabel("NOMBRE COMPLETO")
            BklnTextField(value = name, onValueChange = { name = it }, placeholder = "Tu nombre")
            Spacer(Modifier.height(14.dp))
            FormLabel("CORREO ELECTRÓNICO")
            BklnTextField(value = email, onValueChange = { email = it }, placeholder = "tu@correo.com",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Spacer(Modifier.height(14.dp))
            FormLabel("CONTRASEÑA")
            BklnTextField(
                value = password, onValueChange = { password = it }, placeholder = "Mínimo 8 caracteres",
                visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = BklnMuted)
                    }
                }
            )
            if (password.isNotEmpty()) {
                val (strengthText, strengthColor) = when {
                    password.length < 6 -> "⚠ Muy corta" to BklnError
                    password.length < 8 -> "● Débil" to Color(0xFFFF8C00)
                    password.any { it.isUpperCase() } && password.any { it.isDigit() } -> "✓ Segura" to BklnSuccess
                    else -> "● Aceptable" to BklnYellow
                }
                Text(strengthText, color = strengthColor, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = termsChecked, onCheckedChange = { termsChecked = it },
                    colors = CheckboxDefaults.colors(checkedColor = BklnOrange, uncheckedColor = BklnMuted))
                Text("Acepto los Términos de uso y la Política de privacidad", color = BklnMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 4.dp))
            }
            Spacer(Modifier.height(14.dp))
            if (error.isNotBlank()) Text(error, color = BklnError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
            BklnButton(text = if (loading) "Cargando..." else "Crear cuenta →", enabled = !loading) {
                if (name.isBlank() || email.isBlank() || password.isBlank()) { error = "Completa todos los campos."; return@BklnButton }
                if (password.length < 8) { error = "La contraseña debe tener al menos 8 caracteres."; return@BklnButton }
                if (!termsChecked) { error = "Acepta los términos para continuar."; return@BklnButton }
                loading = true; error = ""
                scope.launch {
                    try {
                        SupabaseClient.client.auth.signUpWith(Email) {
                            this.email = email.trim(); this.password = password
                            data = buildJsonObject { put("full_name", JsonPrimitive(name.trim())) }
                        }
                        onSuccess()
                    } catch (e: Exception) {
                        error = if (e.message?.contains("already registered") == true)
                            "Este correo ya está registrado." else "Error al crear la cuenta."
                    } finally { loading = false }
                }
            }
        }

        "phone" -> {
            if (phoneStep == 1) {
                FormLabel("NOMBRE COMPLETO")
                BklnTextField(value = regName, onValueChange = { regName = it }, placeholder = "Tu nombre")
                Spacer(Modifier.height(14.dp))
                FormLabel("NÚMERO DE TELÉFONO")
                BklnTextField(
                    value = phone, onValueChange = { phone = it },
                    placeholder = "+240 555 000 000",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Text("Incluye el código de país, ej: +240 (Guinea Ecuatorial)", color = BklnMuted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                if (error.isNotBlank()) Text(error, color = BklnError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                BklnButton(text = if (loading) "Enviando..." else "Enviar código SMS →", enabled = !loading) {
                    if (regName.isBlank()) { error = "Ingresa tu nombre."; return@BklnButton }
                    val trimmed = phone.trim().replace(" ", "")
                    if (trimmed.length < 8) { error = "Ingresa un número válido con código de país."; return@BklnButton }
                    loading = true; error = ""
                    scope.launch {
                        try {
                            SupabaseClient.client.auth.signInWith(Phone) {
                                phone = trimmed
                                data = buildJsonObject { put("full_name", JsonPrimitive(regName.trim())) }
                            }
                            phoneStep = 2
                        } catch (e: Exception) {
                            error = "Error al enviar SMS. Verifica el número."
                        } finally { loading = false }
                    }
                }
            } else {
                Text("← Cambiar número", color = BklnOrange, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp).clickable { phoneStep = 1; otpCode = ""; error = "" })
                Text("Ingresa el código de 6 dígitos enviado a $phone", color = BklnMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 14.dp))
                FormLabel("CÓDIGO DE VERIFICACIÓN")
                BklnTextField(
                    value = otpCode, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) otpCode = it },
                    placeholder = "123456",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(Modifier.height(16.dp))
                if (error.isNotBlank()) Text(error, color = BklnError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 10.dp))
                BklnButton(text = if (loading) "Verificando..." else "Verificar y crear cuenta →", enabled = !loading) {
                    if (otpCode.length < 6) { error = "Ingresa el código completo de 6 dígitos."; return@BklnButton }
                    loading = true; error = ""
                    scope.launch {
                        try {
                            val trimmed = phone.trim().replace(" ", "")
                            SupabaseClient.client.auth.verifyPhoneOtp(
                                phone = trimmed, token = otpCode, type = OtpType.Phone.SMS
                            )
                            onSuccess()
                        } catch (e: Exception) {
                            error = "Código incorrecto o expirado."
                        } finally { loading = false }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("¿No llegó el código? Vuelve atrás y reintenta.", color = BklnMuted, fontSize = 11.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── SHARED HELPERS ────────────────────────────────────────────────────────────

@Composable
private fun FormLabel(text: String) {
    Text(text, color = BklnMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun BklnButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth().height(50.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) BklnOrange else BklnMuted)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
