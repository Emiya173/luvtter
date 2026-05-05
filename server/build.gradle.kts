plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktor)
    application
}

group = "com.luvtter"
version = "0.1.0"

application {
    applicationName = "luvtter-server"
    mainClass.set("com.luvtter.server.ApplicationKt")
    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

// 让 jar / fatJar / 发行包都用 luvtter-server 命名,避免与 Compose/Android 产物混淆
tasks.named<Jar>("jar") { archiveBaseName.set("luvtter-server") }
tasks.named<Jar>("shadowJar") {
    archiveFileName.set("luvtter-server-${project.version}-all.jar")
}
distributions {
    named("main") { distributionBaseName.set("luvtter-server") }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
    }
}

dependencies {
    implementation(project(":api-contract"))

    // Ktor server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.sse)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.exposed.json)
    implementation(libs.postgresql)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    // DI
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)

    // Security
    implementation(libs.argon2)

    // Object storage
    implementation(libs.minio)

    // Logging
    implementation(libs.logback)
    implementation(libs.kotlin.logging)

    // Test
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.content.negotiation)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.minio)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 维护用 CLI: ./gradlew :server:cli --args="<command> [--k=v ...]"
tasks.register<JavaExec>("cli") {
    group = "application"
    description = "Run luvtter CLI (register-user/list-users/...). 透传 application.yaml 与 ENV。"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.luvtter.server.tools.LuvtterCliKt")
    standardInput = System.`in`
}

// 把 CLI 也打进 application 发行包(installDist/distZip/distTar):
// 同一份 classpath 下生成第二个启动脚本 bin/luvtter-cli(.bat),不重复发布 jar。
val cliStartScripts = tasks.register<CreateStartScripts>("createCliStartScripts") {
    applicationName = "luvtter-cli"
    mainClass.set("com.luvtter.server.tools.LuvtterCliKt")
    outputDir = layout.buildDirectory.dir("cli-scripts").get().asFile
    classpath = tasks.named<CreateStartScripts>("startScripts").get().classpath
    defaultJvmOpts = listOf()
}

fun cliScriptsTree() = fileTree(cliStartScripts.map { it.outputDir }).builtBy(cliStartScripts)

tasks.named<Sync>("installDist") {
    into("bin") {
        from(cliScriptsTree()) {
            filePermissions { unix("rwxr-xr-x") }
        }
    }
}
tasks.named<Tar>("distTar") {
    into("luvtter-server-${project.version}/bin") {
        from(cliScriptsTree()) {
            filePermissions { unix("rwxr-xr-x") }
        }
    }
}
tasks.named<Zip>("distZip") {
    into("luvtter-server-${project.version}/bin") {
        from(cliScriptsTree())
    }
}

tasks.test {
    useJUnitPlatform()
    environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    environment("DOCKER_API_VERSION", "1.41")
    systemProperty("api.version", "1.41")
}
