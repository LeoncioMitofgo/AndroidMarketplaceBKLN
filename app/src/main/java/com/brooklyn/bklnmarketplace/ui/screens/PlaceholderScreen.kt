package com.brooklyn.bklnmarketplace.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.brooklyn.bklnmarketplace.ui.theme.BklnBgDark
import com.brooklyn.bklnmarketplace.ui.theme.BklnMuted

@Composable
fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(BklnBgDark),
        contentAlignment = Alignment.Center
    ) {
        Text("$name — próximamente", color = BklnMuted, fontSize = 15.sp)
    }
}
