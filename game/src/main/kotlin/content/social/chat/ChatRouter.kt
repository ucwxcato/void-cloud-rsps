package content.social.chat

import content.area.wilderness.daemonheim.DungeoneeringParty.Companion.dungeonMembers
import content.area.wilderness.daemonheim.DungeoneeringParty.Companion.inDungeoneering
import content.social.clan.chatType
import content.social.ignore.ignores
import net.pearx.kasechange.toTitleCase
import world.gregs.voidps.cache.secure.Huffman
import world.gregs.voidps.engine.client.message
import world.gregs.voidps.engine.client.update.view.Viewport.Companion.VIEW_RADIUS
import world.gregs.voidps.engine.entity.character.player.Player
import world.gregs.voidps.engine.entity.character.player.Players
import world.gregs.voidps.engine.entity.character.player.chat.ChatType
import world.gregs.voidps.engine.entity.character.player.name
import world.gregs.voidps.engine.entity.character.player.rights
import world.gregs.voidps.engine.event.AuditLog
import world.gregs.voidps.network.login.protocol.encode.publicChat

/** Routes public player chat while keeping the protocol handling in [Chat]. */
class ChatRouter(private val huffman: Huffman) {

    fun send(player: Player, text: String, effects: Int) {
        if (player.chatType == "public" && text.startsWith(GLOBAL_PREFIX)) {
            sendGlobal(player, text.removePrefix(GLOBAL_PREFIX).trim())
            return
        }

        val normalized = normalize(text)
        if (normalized.isEmpty()) {
            return
        }
        AuditLog.event(player, "said", normalized)
        ChatHistory.add(player, "proximity", normalized)
        val compressed = huffman.compress("$PROXIMITY_TAG $normalized")
        if (player.inDungeoneering) {
            for (member in player.dungeonMembers) {
                if (!member.ignores(player)) {
                    member.client?.publicChat(player.index, effects, player.rights.ordinal, compressed)
                }
            }
        } else {
            Players.filter { it.tile.within(player.tile, VIEW_RADIUS) && !it.ignores(player) }.forEach {
                it.client?.publicChat(player.index, effects, player.rights.ordinal, compressed)
            }
        }
    }

    private fun sendGlobal(player: Player, text: String) {
        if (text.isEmpty()) {
            player.message("Usage: . <message>")
            return
        }
        if (text.length > MAX_MESSAGE_LENGTH) {
            player.message("Global messages can be at most $MAX_MESSAGE_LENGTH characters.")
            return
        }
        val normalized = normalize(text)
        if (normalized.isEmpty()) {
            player.message("Usage: . <message>")
            return
        }
        AuditLog.event(player, "global_said", normalized)
        ChatHistory.add(player, "global", normalized)
        val message = "$GLOBAL_TAG ${player.name}: $normalized"
        Players.filterNot { it.ignores(player) }.forEach { it.message(message, ChatType.Chat) }
    }

    private fun normalize(text: String): String = if (text.all { it.isUpperCase() }) {
        text.toTitleCase()
    } else {
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private companion object {
        const val GLOBAL_PREFIX = "."
        const val GLOBAL_TAG = "[GLOBAL]"
        const val PROXIMITY_TAG = "[PROXIMITY]"
        const val MAX_MESSAGE_LENGTH = 80
    }
}
