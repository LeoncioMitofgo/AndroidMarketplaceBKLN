package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.brooklyn.bklnmarketplace.AuthState
import com.brooklyn.bklnmarketplace.SupabaseClient
import com.brooklyn.bklnmarketplace.ui.components.BklnTextField
import com.brooklyn.bklnmarketplace.ui.theme.*
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun CambiarContrasenaScreen(navController: NavController) {
    val scope     = rememberCoroutineScope()
    var password  by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var pass1Vis  by remember { mutableStateOf(false) }
    var pass2Vis  by remember { mutableStateOf(false) }
    var loading   by remember { mutableStateOf(false) }
    var error     by remember { mutableStateOf("") }
    var done      by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(BklnBgDark)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).background(BklnBgCard).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "NUEVA CONTRASEÑA",
                color = BklnWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp
            )
        }
        HorizontalDivider(color = BklnStroke)

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text("🔐", fontSize = 52.sp)
            Spacer(Modifier.height(16.dp))

            if (!done) {
                Text(
                    "Crea tu nueva contraseña",
                    color = BklnWhite, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(24.dp))

                Text(
                    "NUEVA CONTRASEÑA",
                    color = BklnMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                BklnTextField(
                    value = password, onValueChange = { password = it },
                    placeholder = "••••••••",
                    visualTransformation = if (pass1Vis) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pass1Vis = !pass1Vis }) {
                            Icon(
                                if (pass1Vis) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = BklnMuted
                            )
                        }
                    }
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "CONFIRMAR CONTRASEÑA",
                    color = BklnMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(6.dp))
                BklnTextField(
                    value = password2, onValueChange = { password2 = it },
                    placeholder = "••••••••",
                    visualTransformation = if (pass2Vis) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { pass2Vis = !pass2Vis }) {
                            Icon(
                                if (pass2Vis) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                null, tint = BklnMuted
                            )
                        }
                    }
                )

                if (error.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = BklnError, fontSize = 13.sp)
                }
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (loading) BklnMuted else BklnOrange)
                        .clickable(enabled = !loading) {
                            if (password.length < 6) {
                                error = "La contraseña debe tener al menos 6 caracteres."
                                return@clickable
                            }
                            if (password != password2) {
                                error = "Las contraseñas no coinciden."
                                return@clickable
                            }
                            loading = true; error = ""
                            scope.launch {
                                try {
                                    SupabaseClient.client.auth.updateUser {
                                        this.password = password
                                    }
                                    AuthState.awaitingPasswordReset.value = false
                                    done = true
                                } catch (e: Exception) {
                                    error = "Error al actualizar la contraseña."
                                } finally { loading = false }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = BklnWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            "Guardar nueva contraseña →",
                            color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    "¡Contraseña actualizada!",
                    color = BklnWhite, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tu contraseña ha sido restablecida correctamente.",
                    color = BklnMuted, fontSize = 13.sp, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(BklnOrange)
                        .clickable {
                            navController.navigate("home") { popUpTo("login") { inclusive = true } }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Ir al inicio →", color = BklnWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
