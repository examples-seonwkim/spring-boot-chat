package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.annotation.JsonCreator
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
        val TYPE_KEY: EntityTypeKey<Command> =
            EntityTypeKey.create(Command::class.java, "ChatRoomActor")
    }

    interface Command : JsonSerializable

    data class JoinRoom @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("userRef") val userRef: ActorRef<UserActor.Command>
    ) : Command

    data class LeaveRoom @JsonCreator constructor(
        @JsonProperty("userId") val userId: String
    ) : Command

    data class SendMessage @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("message") val message: String
    ) : Command

    interface ChatEvent : JsonSerializable

    data class UserJoined @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : ChatEvent

    data class UserLeft @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : ChatEvent

    data class MessageReceived @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("message") val message: String,
        @JsonProperty("roomId") val roomId: String
    ) : ChatEvent

    override fun typeKey(): EntityTypeKey<Command> = TYPE_KEY

    override fun create(ctx: EntityContext<Command>): Behavior<Command> {
        val roomId = ctx.entityId
        return chatRoom(roomId, mutableMapOf())
    }

    private fun chatRoom(
        roomId: String,
        connectedUsers: MutableMap<String, ActorRef<UserActor.Command>>
    ): Behavior<Command> {
        return Behaviors.receive(Command::class.java)
            .onMessage(JoinRoom::class.java) { msg ->
                connectedUsers[msg.userId] = msg.userRef
                val joinRoomCmd = UserActor.JoinRoom(msg.userId, roomId)
                broadcastCommand(connectedUsers, joinRoomCmd)
                chatRoom(roomId, connectedUsers)
            }
            .onMessage(LeaveRoom::class.java) { msg ->
                val userRef = connectedUsers.remove(msg.userId)
                userRef?.let {
                    val leaveRoomCmd = UserActor.LeaveRoom(msg.userId, roomId)
                    it.tell(leaveRoomCmd)
                    broadcastCommand(connectedUsers, leaveRoomCmd)
                }
                chatRoom(roomId, connectedUsers)
            }
            .onMessage(SendMessage::class.java) { msg ->
                val receiveMessageCmd = UserActor.ReceiveMessage(msg.userId, msg.message, roomId)
                broadcastCommand(connectedUsers, receiveMessageCmd)
                Behaviors.same()
            }
            .build()
    }

    private fun broadcastCommand(
        connectedUsers: Map<String, ActorRef<UserActor.Command>>,
        command: UserActor.Command
    ) {
        connectedUsers.values.forEach { it.tell(command) }
    }

    override fun extractor(): ShardingMessageExtractor<ShardEnvelope<Command>, Command> {
        return DefaultShardingMessageExtractor(3)
    }
}
