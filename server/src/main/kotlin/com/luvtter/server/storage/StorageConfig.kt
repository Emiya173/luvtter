package com.luvtter.server.storage

import io.ktor.server.config.ApplicationConfig

data class StorageConfig(
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String = "us-east-1",
    val uploadTtlSeconds: Int = 600,
    val downloadTtlSeconds: Int = 3600,
    val maxPhotoBytes: Long = 10L * 1024 * 1024,
    val maxScanBytes: Long = 30L * 1024 * 1024,
    val maxHandwritingBytes: Long = 5L * 1024 * 1024,
    val publicBaseUrl: String? = null
)

fun ApplicationConfig.storageConfig(): StorageConfig = StorageConfig(
    endpoint = property("storage.endpoint").getString(),
    accessKey = property("storage.accessKey").getString(),
    secretKey = property("storage.secretKey").getString(),
    bucket = property("storage.bucket").getString(),
    region = propertyOrNull("storage.region")?.getString() ?: "us-east-1",
    uploadTtlSeconds = propertyOrNull("storage.uploadTtlSeconds")?.getString()?.toInt() ?: 600,
    downloadTtlSeconds = propertyOrNull("storage.downloadTtlSeconds")?.getString()?.toInt() ?: 3600,
    maxPhotoBytes = propertyOrNull("storage.maxPhotoBytes")?.getString()?.toLong() ?: (10L * 1024 * 1024),
    maxScanBytes = propertyOrNull("storage.maxScanBytes")?.getString()?.toLong() ?: (30L * 1024 * 1024),
    maxHandwritingBytes = propertyOrNull("storage.maxHandwritingBytes")?.getString()?.toLong() ?: (5L * 1024 * 1024),
    publicBaseUrl = propertyOrNull("storage.publicBaseUrl")?.getString()
)
