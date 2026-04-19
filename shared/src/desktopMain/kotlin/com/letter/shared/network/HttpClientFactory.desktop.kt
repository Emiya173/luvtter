package com.letter.shared.network

import io.ktor.client.*
import io.ktor.client.engine.java.*

actual fun platformHttpClient(): HttpClient = HttpClient(Java)
