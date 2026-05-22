package com.brooklyn.bklnmarketplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.brooklyn.bklnmarketplace.models.Product
import com.brooklyn.bklnmarketplace.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ProductCard(product: Product, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(5.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BklnBgCard)
            .border(1.dp, BklnStroke, RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        // Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(BklnBgInput),
            contentAlignment = Alignment.Center
        ) {
            val firstImage = product.images?.firstOrNull()
            if (!firstImage.isNullOrBlank()) {
                AsyncImage(
                    model = firstImage,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(categoryEmoji(product.category), fontSize = 32.sp)
            }
        }

        // Info
        Column(modifier = Modifier.padding(10.dp)) {
            if (!product.category.isNullOrBlank()) {
                Text(
                    text = product.category.uppercase(),
                    color = BklnMuted,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Text(
                text = product.name,
                color = BklnWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = formatPrice(product.price),
                color = BklnYellow,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = product.profiles?.fullName ?: product.profiles?.username ?: "Vendedor",
                color = BklnMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatPrice(price: Double?): String {
    if (price == null) return ""
    val fmt = NumberFormat.getNumberInstance(Locale("es", "ES"))
    return "${fmt.format(price.toLong())} XAF"
}

private fun categoryEmoji(category: String?): String = when (category?.lowercase()) {
    "ropa", "moda"               -> "👗"
    "electrónica", "tecnología"  -> "📱"
    "hogar", "casa"              -> "🏠"
    "alimentos", "comida"        -> "🍎"
    "belleza", "cosméticos"      -> "💄"
    "deportes"                   -> "⚽"
    "juguetes"                   -> "🧸"
    "libros"                     -> "📚"
    "vehículos"                  -> "🚗"
    else                         -> "📦"
}
