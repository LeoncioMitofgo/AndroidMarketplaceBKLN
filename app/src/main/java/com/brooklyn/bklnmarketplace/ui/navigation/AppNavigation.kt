package com.brooklyn.bklnmarketplace.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.brooklyn.bklnmarketplace.AuthState
import com.brooklyn.bklnmarketplace.ui.screens.*
import com.brooklyn.bklnmarketplace.ui.theme.*

data class NavItem(val route: String, val label: String, val icon: ImageVector)

val bottomNavItems = listOf(
    NavItem("home",       "Inicio",   Icons.Default.Home),
    NavItem("productos",  "Explorar", Icons.Default.Search),
    NavItem("publicar",   "Publicar", Icons.Default.Add),
    NavItem("vendedores", "Tiendas",  Icons.Default.Store),
    NavItem("perfil",     "Perfil",   Icons.Default.Person),
)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route
    val showBottomBar = currentRoute != "login" && currentRoute != null

    val awaitingReset by AuthState.awaitingPasswordReset.collectAsState()
    LaunchedEffect(awaitingReset) {
        if (awaitingReset) {
            navController.navigate("cambiar_contrasena") {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = BklnBgDark,
        bottomBar = {
            AnimatedVisibility(visible = showBottomBar) {
                NavigationBar(containerColor = BklnBgCard) {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor   = BklnOrange,
                                selectedTextColor   = BklnOrange,
                                unselectedIconColor = BklnMuted,
                                unselectedTextColor = BklnMuted,
                                indicatorColor      = BklnAccentTint,
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("login")      { LoginScreen(navController) }
            composable("home")       { HomeScreen(navController) }
            composable(
                route = "productos?cat={cat}",
                arguments = listOf(navArgument("cat") { type = NavType.StringType; defaultValue = "" })
            ) { backStackEntry ->
                ExplorarScreen(navController, backStackEntry.arguments?.getString("cat") ?: "")
            }
            composable("publicar")   { PublicarScreen(navController) }
            composable("vendedores") { VendedoresScreen(navController) }
            composable("perfil")     { PerfilScreen(navController) }
            composable("resumen")       { ResumenScreen(navController) }
            composable("mis_productos") { MisProductosScreen(navController) }
            composable("vendedor/{sellerId}") { backStackEntry ->
                VendedorScreen(navController, backStackEntry.arguments?.getString("sellerId") ?: "")
            }
            composable("producto/{productId}") { backStackEntry ->
                ProductoScreen(navController, backStackEntry.arguments?.getString("productId") ?: "")
            }
            composable("favoritos")     { FavoritosScreen(navController) }
            composable("editar_perfil") { EditarPerfilScreen(navController) }
            composable("editar_producto/{productId}") { backStackEntry ->
                EditarProductoScreen(navController, backStackEntry.arguments?.getString("productId") ?: "")
            }
            composable("tendencias") { TendenciasScreen(navController) }
            composable("mensajes")   { MensajesScreen(navController) }
            composable("chat/{otherUserId}") { backStackEntry ->
                ChatScreen(navController, backStackEntry.arguments?.getString("otherUserId") ?: "")
            }
            composable("planes")               { PlanesScreen(navController) }
            composable("recuperar_contrasena") { RecuperarContrasenaScreen(navController) }
            composable("cambiar_contrasena")   { CambiarContrasenaScreen(navController) }
            composable("colecciones") { ColeccionesScreen(navController) }
            composable("coleccion/{catalogId}") { backStackEntry ->
                ColeccionScreen(navController, backStackEntry.arguments?.getString("catalogId") ?: "")
            }
            composable("nueva_coleccion") { NuevaColeccionScreen(navController) }
            composable("editar_coleccion/{catalogId}") { backStackEntry ->
                NuevaColeccionScreen(navController, editCatalogId = backStackEntry.arguments?.getString("catalogId") ?: "")
            }
        }
    }
}
