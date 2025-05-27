package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.seonwkim.core.SpringActor
import io.github.seonwkim.core.SpringActorContext
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException

@Component
class UserActor(
    private val objectMapper: ObjectMapper
) : SpringActor {

    override fun commandClass(): Class<*> = Command::class.java

    interface Command {}

    data class SetWebSocketSession(
        @JsonProperty("session") val session: WebSocketSession
    ) : Command

    data class JoinRoom(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : Command

    data class LeaveRoom(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("roomId") val roomId: String
    ) : Command

    data class SendMessage(
        @JsonProperty("userId") val userId: String,
        @JsonProperty("message") val message: String,
        @JsonProperty("roomId") val roomId: String
    ) : Command

    override fun create(actorContext: SpringActorContext): Behavior<Command> {
        return Behaviors.setup { context ->
            val userId = actorContext.actorId()
            context.log.info("Creating UserActor for user ID: {}", userId)
            UserActorBehavior(context, null, objectMapper).create()
        }
    }

    private class UserActorBehavior(
        private val context: ActorContext<Command>,
        private var session: WebSocketSession?,
        private val objectMapper: ObjectMapper
    ) {
        fun create(): Behavior<Command> {
            return Behaviors.receive(Command::class.java)
                .onMessage(SetWebSocketSession::class.java, this::onSetWebSocketSession)
                .onMessage(JoinRoom::class.java, this::onUserJoined)
                .onMessage(LeaveRoom::class.java, this::onUserLeft)
                .onMessage(SendMessage::class.java, this::onMessageReceived)
                .build()
        }

        private fun onSetWebSocketSession(command: SetWebSocketSession): Behavior<Command> {
            context.log.info("Setting WebSocketSession for actor")
            session = command.session
            return Behaviors.same()
        }

        private fun onUserJoined(event: JoinRoom): Behavior<Command> {
            sendEvent("user_joined") {
                put("userId", event.userId)
                put("roomId", event.roomId)
            }
            return Behaviors.same()
        }

        private fun onUserLeft(event: LeaveRoom): Behavior<Command> {
            sendEvent("user_left") {
                put("userId", event.userId)
                put("roomId", event.roomId)
            }
            return Behaviors.same()
        }

        private fun onMessageReceived(event: SendMessage): Behavior<Command> {
            sendEvent("message") {
                put("userId", event.userId)
                put("message", event.message)
                put("roomId", event.roomId)
            }
            return Behaviors.same()
        }

        private fun sendEvent(type: String, builder: ObjectNode.() -> Unit) {
            if (session == null) {
                context.log.error("Cannot send event: WebSocketSession is not set")
                return
            }

            try {
                val eventNode = objectMapper.createObjectNode()
                eventNode.put("type", type)
                eventNode.builder()
                if (session!!.isOpen) {
                    session!!.sendMessage(TextMessage(objectMapper.writeValueAsString(eventNode)))
                } else {
                    context.log.warn("Cannot send event: WebSocketSession is closed")
                }
            } catch (e: IOException) {
                context.log.error("Failed to send message to WebSocket", e)
            }
        }
    }
}
