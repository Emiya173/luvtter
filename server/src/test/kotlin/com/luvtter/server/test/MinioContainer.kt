package com.luvtter.server.test

import org.testcontainers.containers.MinIOContainer

object MinioContainer {
    val container: MinIOContainer by lazy {
        MinIOContainer("minio/minio:latest").apply {
            withUserName("testuser")
            withPassword("testpass1234")
            start()
            Runtime.getRuntime().addShutdownHook(Thread { runCatching { stop() } })
        }
    }

    val endpoint: String get() = container.s3URL
    val accessKey: String get() = container.userName
    val secretKey: String get() = container.password
    const val BUCKET = "letter-test"
}
