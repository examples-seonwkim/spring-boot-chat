package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.seonwkim.core.SpringActor
import io.github.seonwkim.core.SpringActorContext
import io.github.seonwkim.core.SpringActorSystem
import io.github.seonwkim.core.SpringShardedActorRef
import io.github.seonwkim.core.serialization.JsonSerializable
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException

@Component
class UserActor : SpringActor {

    interface Command : JsonSerializable

    class Connect : Command

    data class JoinRoom(val roomId: String) : Command

    class LeaveRoom : Command

    data class SendMessage(val message: String) : Command

    data class JoinRoomEvent(val userId: String) : Command

    data class LeaveRoomEvent(val userId: String) : Command

    data class SendMessageEvent(
        val userId: String,
        val message: String
    ) : Command

    override fun commandClass(): Class<*> = Command::class.java

    class UserActorContext(
        val actorSystem: SpringActorSystem,
        val objectMapper: ObjectMapper,
        val userId: String,
        val session: WebSocketSession
    ) : SpringActorContext {
        override fun actorId(): String = userId
    }

    override fun create(actorContext: SpringActorContext): Behavior<Command> {
        val userActorContext = actorContext as? UserActorContext
            ?: throw IllegalStateException("Must be UserActorContext")

        return Behaviors.setup { context ->
            UserActorBehavior(
                context,
                userActorContext.actorSystem,
                userActorContext.objectMapper,
                userActorContext.userId,
                userActorContext.session
            ).create()
        }
    }

    private class UserActorBehavior(
        private val context: ActorContext<Command>,
        private val actorSystem: SpringActorSystem,
        private val objectMapper: ObjectMapper,
        private val userId: String,
        private val session: WebSocketSession
    ) {
        private var currentRoomId: String? = null

        fun create(): Behavior<Command> = Behaviors.receive(Command::class.java)
            .onMessage(Connect::class.java, ::onConnect)
            .onMessage(JoinRoom::class.java, ::onJoinRoom)
            .onMessage(LeaveRoom::class.java, ::onLeaveRoom)
            .onMessage(SendMessage::class.java, ::onSendMessage)
            .onMessage(JoinRoomEvent::class.java, ::onJoinRoomEvent)
            .onMessage(LeaveRoomEvent::class.java, ::onLeaveRoomEvent)
            .onMessage(SendMessageEvent::class.java, ::onSendMessageEvent)
            .build()

        private fun onConnect(connect: Connect): Behavior<Command> {
            sendEvent("connected") {
                put("userId", userId)
            }
            return Behaviors.same()
        }

        private fun onJoinRoom(command: JoinRoom): Behavior<Command> {
            currentRoomId = command.roomId
            val roomActor = getRoomActor()
            sendEvent("joined") {
                put("roomId", currentRoomId)
            }

            roomActor.tell(ChatRoomActor.JoinRoom(userId, context.self))
            return Behaviors.same()
        }

        private fun onLeaveRoom(command: LeaveRoom): Behavior<Command> {
            if (currentRoomId == null) {
                context.log.info("$userId user has not joined any room.")
                return Behaviors.same()
            }

            sendEvent("left") {
                put("roomId", currentRoomId)
            }

            val roomActor = getRoomActor()
            roomActor.tell(ChatRoomActor.LeaveRoom(userId))

            return Behaviors.same()
        }

        private fun onSendMessage(command: SendMessage): Behavior<Command> {
            if (currentRoomId == null) {
                context.log.info("$userId user has not joined any room.")
                return Behaviors.same()
            }

            val roomActor = getRoomActor()
            roomActor.tell(ChatRoomActor.SendMessage(userId, command.message))

            return Behaviors.same()
        }

        private fun onJoinRoomEvent(event: JoinRoomEvent): Behavior<Command> {
            sendEvent("user_joined") {
                put("userId", event.userId)
                put("roomId", currentRoomId)
            }
            return Behaviors.same()
        }

        private fun onLeaveRoomEvent(event: LeaveRoomEvent): Behavior<Command> {
            sendEvent("user_left") {
                put("userId", event.userId)
                put("roomId", currentRoomId)
            }
            return Behaviors.same()
        }

        private fun onSendMessageEvent(event: SendMessageEvent): Behavior<Command> {
            sendEvent("message") {
                put("userId", event.userId)
                put("message", event.message)
                put("roomId", currentRoomId)
            }
            return Behaviors.same()
        }

        private fun getRoomActor(): SpringShardedActorRef<ChatRoomActor.Command> =
            actorSystem.entityRef(ChatRoomActor.Companion.TYPE_KEY, currentRoomId!!)

        private fun sendEvent(type: String, builder: ObjectNode.() -> Unit) {
            try {
                val eventNode = objectMapper.createObjectNode().apply {
                    put("type", type)
                    builder()
                }

                if (session.isOpen) {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(eventNode)))
                }
            } catch (e: IOException) {
                context.log.error("Failed to send message to WebSocket", e)
            }
        }
    }
}