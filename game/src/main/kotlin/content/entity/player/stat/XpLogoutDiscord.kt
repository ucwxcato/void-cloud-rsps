package content.entity.player.stat

import com.github.michaelbull.logging.InlineLogger
import content.bot.isBot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import world.gregs.voidps.engine.Script
import world.gregs.voidps.engine.client.ui.chat.toDigitGroupString
import world.gregs.voidps.engine.client.variable.PlayerVariables
import world.gregs.voidps.engine.data.Settings
import world.gregs.voidps.engine.entity.character.player.skill.Skill
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class XpLogoutDiscord : Script {

    init {
        val loginWebhook = parseWebhook(Settings["DISCORD_LOGIN_WEBHOOK_URL", ""], "login")
        val statusWebhook = parseWebhook(Settings["DISCORD_WEBHOOK_URL", ""], "status")
        val xpUrl = Settings["DISCORD_XP_WEBHOOK_URL", ""].ifBlank { Settings["DISCORD_WEBHOOK_URL", ""] }
        val xpWebhook = parseWebhook(xpUrl, "XP")
        if (loginWebhook != null || statusWebhook != null || xpWebhook != null) {
            playerSpawn {
                if (isBot) {
                    return@playerSpawn
                }
                (variables as? PlayerVariables)?.temp?.set(SESSION_START_XP, experience.experience.copyOf())
                loginWebhook?.let { send(it, "$accountName logged in") }
            }

            playerDespawn {
                if (isBot) {
                    return@playerDespawn
                }
                statusWebhook?.let { send(it, "$accountName logged out") }
                val start = (variables as? PlayerVariables)?.temp?.remove(SESSION_START_XP) as? IntArray ?: return@playerDespawn
                val gains = Skill.entries.mapNotNull { skill ->
                    val delta = experience.direct(skill) - start[skill.ordinal]
                    if (delta > 0) skill to delta else null
                }
                if (gains.isEmpty()) {
                    return@playerDespawn
                }

                val total = gains.sumOf { it.second.toLong() } / 10
                val details = gains.joinToString(" | ") { (skill, rawXp) ->
                    "${skill.name}: +${(rawXp / 10).toDigitGroupString()}"
                }
                val content = "$accountName logged out | XP gained: +${total.toDigitGroupString()}\n$details"
                xpWebhook?.let { send(it, content) }
            }
        }
    }

    private fun parseWebhook(url: String, kind: String): URI? {
        if (url.isBlank()) {
            return null
        }
        val webhook = runCatching { URI.create(url.trim()) }.getOrNull()
        if (webhook == null || webhook.scheme != "https" || webhook.host != "discord.com" || !webhook.path.startsWith("/api/webhooks/")) {
            logger.warn { "Discord $kind webhook is configured with an invalid URL; related notices are disabled." }
            return null
        }
        return webhook
    }

    private fun send(webhook: URI, content: String) {
        val payload = """{"content":"${content.escapeJson()}","allowed_mentions":{"parse":[]}}"""
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val request = HttpRequest.newBuilder(webhook)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build()
                val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())
                if (response.statusCode() !in 200..299) {
                    logger.warn { "Discord XP logout webhook returned HTTP ${response.statusCode()}." }
                }
            }.onFailure { error ->
                logger.warn(error) { "Failed to send Discord XP logout summary." }
            }
        }
    }

    private fun String.escapeJson(): String = buildString(length + 16) {
        for (character in this@escapeJson) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }

    companion object {
        private const val SESSION_START_XP = "xp_session_start"
        private val logger = InlineLogger()
        private val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }
}
