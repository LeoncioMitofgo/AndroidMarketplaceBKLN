package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.ui.components.BklnTextField
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun RecuperarContrasenaScreen(navController: NavController) {
    val scope   = rememberCoroutineScope()
    var email   by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error   by remember { mutableStateOf("") }
    var sent    by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(BklnBgCard).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = BklnWhite)
            }
            Text(
                "RECUPERAR CONTRASEÑA",
                color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
            )
        }
        HorizontalDivider(color = BklnStroke)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("🔑", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))

            if (!sent) {
                Text(
                    "¿Olvidaste tu contraseña?",
                    color = BklnWhite, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ingresa tu correo y te enviaremos un enlace para restablecerla.",
                    color = BklnMuted, fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    "CORREO ELECTRÓNICO",
                    color = BklnMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                BklnTextField(
                    value = email, onValueChange = { email = it },
                    placeholder = "tu@correo.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = BklnError, fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (loading) BklnMuted else BklnOrange)
                        .clickable(enabled = !loading) {
                            if (email.isBlank()) { error = "Ingresa tu correo."; return@clickable }
                            if (!email.contains("@")) { error = "Correo no válido."; return@clickable }
                            loading = true; error = ""
                            scope.launch {
                                try {
                                    SupabaseClient.client.auth.resetPasswordForEmail(
                                        email = email.trim(),
                                        redirectUrl = "bklnmarketplace://reset/callback"
                                    )
                                    sent = true
                                } catch (e: Exception) {
                                    error = "No se pudo enviar el correo. Verifica la dirección."
                                } finally { loading = false }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = BklnWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Enviar enlace →", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    "¡Revisa tu correo!",
                    color = BklnWhite, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hemos enviado un enlace de recuperación a:",
                    color = BklnMuted, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(email, color = BklnOrange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(BklnBgCard).padding(16.dp)
                ) {
                    Text(
                        "Abre el correo y haz clic en el enlace. Volverás a la app para crear una nueva contraseña.",
                        color = BklnMuted, fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "← Volver al inicio de sesión",
                    color = BklnOrange, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        navController.navigate("login") { popUpTo("login") { inclusive = true } }
                    }
                )
            }
        }
    }
}
