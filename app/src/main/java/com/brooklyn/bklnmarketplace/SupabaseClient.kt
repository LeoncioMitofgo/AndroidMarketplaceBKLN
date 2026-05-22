package com.brooklyn.bklnmarketplace

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import kotlin.time.Duration.Companion.seconds

object SupabaseClient {

    val client = createSupabaseClient(
        supabaseUrl = "https://uptnfmamtceqmrzckisc.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVwdG5mbWFtdGNlcW1yemNraXNjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzMzODM5MTUsImV4cCI6MjA4ODk1OTkxNX0.iBf2AWumd07iIg2lPFtVVQr8o2WgVlZndVgzsNCRB2o"
    ) {
        requestTimeout = 30.seconds
        install(Auth)
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
