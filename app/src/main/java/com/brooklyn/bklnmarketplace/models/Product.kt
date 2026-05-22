package com.brooklyn.bklnmarketplace.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val plan: String? = null,
    @SerialName("followers_count") val followersCount: Int? = 0,
    @SerialName("following_count") val followingCount: Int? = 0,
    val rating: Double? = null,
    @SerialName("review_count") val reviewCount: Int? = 0,
    val bio: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val whatsapp: String? = null,
)

@Serializable
data class Product(
    val id: String = "",
    val name: String = "",
    val price: Double? = null,
    @SerialName("original_price") val originalPrice: Double? = null,
    val description: String? = null,
    val category: String? = null,
    val images: List<String>? = null,
    @SerialName("user_id") val userId: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null,
    val views: Int? = 0,
    val active: Boolean? = true,
    val condition: String? = null,
    val location: String? = null,
)

@Serializable
data class CatalogProduct(
    @SerialName("catalog_id") val catalogId: String = "",
    @SerialName("product_id") val productId: String = "",
    @SerialName("sort_order") val sortOrder: Int = 0,
    val products: Product? = null,
)

@Serializable
data class Catalog(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    val description: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val active: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null,
    @SerialName("catalog_products") val catalogProducts: List<CatalogProduct>? = null,
)

@Serializable
data class Message(
    val id: String = "",
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("receiver_id") val receiverId: String = "",
    val content: String = "",
    val read: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("product_id") val productId: String? = null,
)
