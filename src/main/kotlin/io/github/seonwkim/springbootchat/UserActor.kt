package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.seonwkim.core.SpringActor
import io.github.seonwkim.core.SpringActorContext
import io.github.seonwkim.core.serialization.JsonSerializable
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException

/**
 * Actor that represents a user in the chat system. It receives commands and forwards
 * them to the user's WebSocket session.
 */
@Component
class UserActor(private val objectMapper: ObjectMapper) : SpringActor {

    interface Command : JsonSerializable

    data class JoinRoom @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : Command

    data class LeaveRoom @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : Command

    data class SendMessage @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("message") val message: String,
        @JsonProperty("roomId") val roomId: String
    ) : Command

    data class ReceiveMessage @JsonCreator constructor(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("message") val message: String,
        @JsonProperty("roomId") val roomId: String
    ) : Command

    override fun commandClass(): Class<*> = Command::class.java

    override fun create(actorContext: SpringActorContext): Behavior<Command> {
        require(actorContext is UserActorContext) {
            "Expected UserActorContext but got ${actorContext::class.java.name}"
        }

        val id = actorContext.actorId()
        val session = actorContext.session

        return Behaviors.setup { context ->
            context.log.info("Creating user actor with ID: {}", id)
            UserActorBehavior(context, session, objectMapper).create()
        }
    }

    private class UserActorBehavior(
        private val context: ActorContext<Command>,
        private val session: WebSocketSession,
        private val objectMapper: ObjectMapper
    ) {

        fun create(): Behavior<Command> {
            return Behaviors.receive(Command::class.java)
                .onMessage(JoinRoom::class.java, ::onJoinRoom)
                .onMessage(LeaveRoom::class.java, ::onLeaveRoom)
                .onMessage(SendMessage::class.java, ::onSendMessage)
                .onMessage(ReceiveMessage::class.java, ::onReceiveMessage)
                .build()
        }

        private fun onJoinRoom(command: JoinRoom): Behavior<Command> {
            sendEvent("user_joined") {
                put("userId", command.userId)
                put("roomId", command.roomId)
            }
            return Behaviors.same()
        }

        private fun onLeaveRoom(command: LeaveRoom): Behavior<Command> {
            sendEvent("user_left") {
                put("userId", command.userId)
                put("roomId", command.roomId)
            }
            return Behaviors.same()
        }

        private fun onSendMessage(command: SendMessage): Behavior<Command> {
            // No-op for sending
            return Behaviors.same()
        }

        private fun onReceiveMessage(command: ReceiveMessage): Behavior<Command> {
            sendEvent("message") {
                put("userId", command.userId)
                put("message", command.message)
                put("roomId", command.roomId)
            }
            return Behaviors.same()
        }

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
