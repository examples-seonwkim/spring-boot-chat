package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.annotation.JsonProperty
import io.github.seonwkim.core.serialization.JsonSerializable
import io.github.seonwkim.core.shard.DefaultShardingMessageExtractor
import io.github.seonwkim.core.shard.ShardEnvelope
import io.github.seonwkim.core.shard.ShardedActor
import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.ShardingMessageExtractor
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityContext
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey
import org.springframework.stereotype.Component

@Component
class ChatRoomActor : ShardedActor<ChatRoomActor.Command> {

    companion object {
        val TYPE_KEY: EntityTypeKey<Command> = EntityTypeKey.create(Command::class.java, "chat-room-actor")
    }

    interface Command : JsonSerializable
    interface ChatEvent : JsonSerializable

    data class JoinRoom(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("userRef") val userRef: ActorRef<ChatEvent>
    ) : Command

    data class LeaveRoom(
        @JsonProperty("userId") val userId: String
    ) : Command

    data class SendMessage(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("message") val message: String
    ) : Command

    data class UserJoined(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : ChatEvent

    data class UserLeft(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : ChatEvent

    data class MessageReceived(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("message") val message: String,
        @JsonProperty("roomId") val roomId: String
    ) : ChatEvent

    override fun typeKey(): EntityTypeKey<Command> = TYPE_KEY

    override fun create(ctx: EntityContext<Command>): Behavior<Command> {
        return Behaviors.setup {
            val roomId = ctx.entityId
            chatRoom(roomId, mutableMapOf())
        }
    }

    private fun chatRoom(
        roomId: String,
        connectedUsers: MutableMap<String, ActorRef<ChatEvent>>
    ): Behavior<Command> {
        return Behaviors.receive(Command::class.java)
            .onMessage(JoinRoom::class.java) { msg ->
                connectedUsers[msg.userId] = msg.userRef
                val event = UserJoined(msg.userId, roomId)
                broadcastEvent(connectedUsers, event)
                chatRoom(roomId, connectedUsers)
            }
            .onMessage(LeaveRoom::class.java) { msg ->
                connectedUsers.remove(msg.userId)
                val event = UserLeft(msg.userId, roomId)
                broadcastEvent(connectedUsers, event)
                chatRoom(roomId, connectedUsers)
            }
            .onMessage(SendMessage::class.java) { msg ->
                val event = MessageReceived(msg.userId, msg.message, roomId)
                broadcastEvent(connectedUsers, event)
                Behaviors.same()
            }
            .build()
    }

    private fun broadcastEvent(connectedUsers: Map<String, ActorRef<ChatEvent>>, event: ChatEvent) {
        connectedUsers.values.forEach { userRef -> userRef.tell(event) }
    }

    override fun extractor(): ShardingMessageExtractor<ShardEnvelope<Command>, Command> {
        return DefaultShardingMessageExtractor(3)
    }
}
