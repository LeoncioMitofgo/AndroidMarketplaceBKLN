package com.brooklyn.bklnmarketplace

import kotlinx.coroutines.flow.MutableStateFlow

object AuthState {
    val awaitingPasswordReset = MutableStateFlow(false)
}
