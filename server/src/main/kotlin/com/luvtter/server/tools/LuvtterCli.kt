package com.luvtter.server.tools

import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.server.auth.AuthService
import com.luvtter.server.auth.JwtConfig
import com.luvtter.server.auth.jwtConfig
import com.luvtter.server.config.configureDatabase
import com.luvtter.server.config.runMigrations
import com.luvtter.server.db.AuthCredentials
import com.luvtter.server.db.Users
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.yaml.YamlConfig
import org.jetbrains.exposed.v1.core.SortOrder
import org.slf4j.Logger as Slf4jLogger
import org.slf4j.LoggerFactory
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * 维护用 CLI。后续要加新命令直接往 `commands` 表里挂即可,无需改 Gradle 任务。
 *
 * 用法(开发期):
 *   ./gradlew :server:cli --args="register-user --email=foo@bar.com --password=12345678 --name=Foo"
 *   ./gradlew :server:cli --args="list-users"
 *   ./gradlew :server:cli --args="help"
 *
 * 部署后:
 *   java -cp <classpath> com.luvtter.server.tools.LuvtterCliKt <command> [args]
 *
 * 所有命令都直接连后端配置(application.yaml + 环境变量)的数据库,绕过 HTTP 层,不受 auth.allowRegistration 影响。
 */
fun main(args: Array<String>) {
    val verbose = System.getenv("LUVTTER_CLI_VERBOSE") == "1" || args.contains("--verbose")
    quietenLogs(verbose)

    val cleanArgs = args.filterNot { it == "--verbose" }.toTypedArray()
    if (cleanArgs.isEmpty() || cleanArgs[0] in listOf("help", "-h", "--help")) {
        printHelp()
        return
    }
    val cmdName = cleanArgs[0]
    val cmdArgs = parseArgs(cleanArgs.copyOfRange(1, cleanArgs.size))
    val command = commands[cmdName] ?: run {
        System.err.println("未知命令: $cmdName")
        printHelp()
        kotlin.system.exitProcess(2)
    }

    val config = loadConfig()
    runMigrations(config)
    val ds = configureDatabase(config)
    try {
        command.run(CliCtx(config, cmdArgs))
    } finally {
        ds.close()
    }
}

private fun quietenLogs(verbose: Boolean) {
    val ctx = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
    ctx.getLogger(Slf4jLogger.ROOT_LOGGER_NAME).detachAppender("FILE")
    if (verbose) return
    val targets = listOf(
        Slf4jLogger.ROOT_LOGGER_NAME,
        "com.luvtter",
        "com.zaxxer.hikari",
        "org.flywaydb",
        "Exposed",
        "io.ktor",
    )
    targets.forEach { ctx.getLogger(it).level = Level.WARN }
}

private val commands: Map<String, CliCommand> = listOf(
    RegisterUserCommand,
    ListUsersCommand,
).associateBy { it.name }

private fun printHelp() {
    println("luvtter cli 命令:")
    commands.values.forEach { println("  ${it.name.padEnd(16)} ${it.summary}") }
    println()
    println("通用参数: 命令后跟 --key=value 形式,如 --email=foo@bar.com")
}

private fun parseArgs(args: Array<String>): Map<String, String> {
    val out = mutableMapOf<String, String>()
    args.forEach { raw ->
        val s = raw.removePrefix("--")
        val eq = s.indexOf('=')
        if (eq <= 0) return@forEach
        out[s.substring(0, eq)] = s.substring(eq + 1)
    }
    return out
}

private fun loadConfig(): ApplicationConfig =
    YamlConfig(null) ?: YamlConfig("application.yaml") ?: error("找不到 application.yaml")

internal class CliCtx(val config: ApplicationConfig, val args: Map<String, String>) {
    fun require(key: String): String = args[key] ?: run {
        System.err.println("缺少参数 --$key")
        kotlin.system.exitProcess(2)
    }
}

internal interface CliCommand {
    val name: String
    val summary: String
    fun run(ctx: CliCtx)
}

internal object RegisterUserCommand : CliCommand {
    override val name = "register-user"
    override val summary = "创建账号: --email --password --name"
    override fun run(ctx: CliCtx) {
        val email = ctx.require("email")
        val password = ctx.require("password")
        val name = ctx.args["name"] ?: ctx.args["display-name"] ?: ctx.require("name")
        val jwt: JwtConfig = ctx.config.jwtConfig()
        val tokens = AuthService(jwt).register(
            RegisterRequest(email = email, password = password, displayName = name),
            deviceName = "cli",
            platform = "cli",
        )
        println("OK ${tokens.user.handle} ${tokens.user.id}")
    }
}

internal object ListUsersCommand : CliCommand {
    override val name = "list-users"
    override val summary = "列出已注册账号(可选 --limit=N)"
    override fun run(ctx: CliCtx) {
        val limit = ctx.args["limit"]?.toIntOrNull() ?: 50
        transaction {
            Users.selectAll()
                .orderBy(Users.createdAt, SortOrder.DESC)
                .limit(limit)
                .forEach { row ->
                    val uid = row[Users.id]
                    val email = AuthCredentials.selectAll()
                        .where { (AuthCredentials.userId eq uid) and (AuthCredentials.provider eq "email") }
                        .firstOrNull()?.get(AuthCredentials.identifier) ?: "-"
                    println("${uid}  ${row[Users.handle].padEnd(20)}  ${email.padEnd(32)}  ${row[Users.displayName]}")
                }
        }
    }
}

private infix fun org.jetbrains.exposed.v1.core.Op<Boolean>.and(
    other: org.jetbrains.exposed.v1.core.Op<Boolean>,
): org.jetbrains.exposed.v1.core.Op<Boolean> = org.jetbrains.exposed.v1.core.AndOp(listOf(this, other))
