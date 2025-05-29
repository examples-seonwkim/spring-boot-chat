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

/**
 * Actor that manages a chat room. Each chat room is a separate entity identified by a room ID. The
 * actor maintains a list of connected users and broadcasts messages to all users in the room.
 */
@Component
class ChatRoomActor : ShardedActor<ChatRoomActor.Command> {

    companion object {
        val TYPE_KEY: EntityTypeKey<Command> = EntityTypeKey.create(Command::class.java, "ChatRoomActor")
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

    override fun typeKey(): EntityTypeKey<Command> = TYPE_KEY

    override fun create(ctx: EntityContext<Command>): Behavior<Command> = Behaviors.setup {
        val roomId = ctx.entityId
        chatRoom(roomId, HashMap())
    }

    /**
     * Creates the behavior for a chat room with the given room ID and connected users.
     *
     * @param roomId The ID of the chat room
     * @param connectedUsers Map of user IDs to their actor references
     * @return The behavior for the chat room
     */
    private fun chatRoom(
        roomId: String,
        connectedUsers: MutableMap<String, ActorRef<UserActor.Command>>
    ): Behavior<Command> = Behaviors.receive(Command::class.java)
        .onMessage(JoinRoom::class.java) { msg ->
            // Add the user to the connected users
            connectedUsers[msg.userId] = msg.userRef

            // Notify all users that a new user has joined
            val joinRoomEvent = UserActor.JoinRoomEvent(msg.userId)
            broadcastCommand(connectedUsers, joinRoomEvent)

            chatRoom(roomId, connectedUsers)
        }
        .onMessage(LeaveRoom::class.java) { msg ->
            // Remove the user from connected users
            val userRef = connectedUsers.remove(msg.userId)

            userRef?.let {
                // Notify the user that they left the room
                val leaveRoomEvent = UserActor.LeaveRoomEvent(msg.userId)
                it.tell(leaveRoomEvent)

                // Notify all remaining users that a user has left
                broadcastCommand(connectedUsers, leaveRoomEvent)
            }

            chatRoom(roomId, connectedUsers)
        }
        .onMessage(SendMessage::class.java) { msg ->
            // Create a message received command
            val receiveMessageCmd = UserActor.SendMessageEvent(msg.userId, msg.message)

            // Broadcast the message to all connected users
            broadcastCommand(connectedUsers, receiveMessageCmd)

            Behaviors.same()
        }
        .build()

    /**
     * Broadcasts a command to all connected users.
     *
     * @param connectedUsers Map of user IDs to their actor references
     * @param command The command to broadcast
     */
    private fun broadcastCommand(
        connectedUsers: Map<String, ActorRef<UserActor.Command>>,
        command: UserActor.Command
    ) {
        connectedUsers.values.forEach { it.tell(command) }
    }

    override fun extractor(): ShardingMessageExtractor<ShardEnvelope<Command>, Command> =
        DefaultShardingMessageExtractor(3)
}