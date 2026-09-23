package content.social.chat

import WorldTest
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import world.gregs.voidps.engine.entity.character.player.chat.ChatType
import world.gregs.voidps.network.client.instruction.ChatPublic
import world.gregs.voidps.network.login.protocol.encode.message
import world.gregs.voidps.network.login.protocol.encode.publicChat
import world.gregs.voidps.type.Tile

internal class ChatTest : WorldTest() {

    @BeforeAll
    fun mockChatEncoder() {
        mockkStatic("world.gregs.voidps.network.login.protocol.encode.ChatEncoderKt")
    }

    @AfterAll
    fun unmockChatEncoder() {
        unmockkStatic("world.gregs.voidps.network.login.protocol.encode.ChatEncoderKt")
    }

    @Test
    fun `Global chat reaches players outside proximity`() = runTest {
        val (sender, senderClient) = createClient("alice", Tile(3200, 3200))
        val (_, recipientClient) = createClient("bob", Tile(3300, 3300))

        sender.instructions.send(ChatPublic(".hello everyone", 0))
        tick()

        verify { senderClient.message("[GLOBAL] alice: Hello everyone", ChatType.Chat.id) }
        verify { recipientClient.message("[GLOBAL] alice: Hello everyone", ChatType.Chat.id) }
    }

    @Test
    fun `Normal chat only reaches nearby players and uses proximity packet`() = runTest {
        val (sender, senderClient) = createClient("alice", Tile(3200, 3200))
        val (_, nearbyClient) = createClient("nearby", Tile(3205, 3205))
        val (_, distantClient) = createClient("distant", Tile(3300, 3300))

        sender.instructions.send(ChatPublic("hello nearby", 0))
        tick()

        verify(exactly = 1) { senderClient.publicChat(any(), any(), any(), any()) }
        verify(exactly = 1) { nearbyClient.publicChat(any(), any(), any(), any()) }
        verify(exactly = 0) { distantClient.publicChat(any(), any(), any(), any()) }
    }

    @Test
    fun `Invalid global prefix does not broadcast`() = runTest {
        val (sender, senderClient) = createClient("alice", Tile(3200, 3200))
        val (_, recipientClient) = createClient("bob", Tile(3300, 3300))

        sender.instructions.send(ChatPublic(".", 0))
        tick()

        verify { senderClient.message("Usage: . <message>", ChatType.Game.id) }
        verify(exactly = 0) { recipientClient.message(any(), any()) }
    }
}
