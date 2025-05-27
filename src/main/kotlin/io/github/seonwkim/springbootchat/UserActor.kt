package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.seonwkim.core.SpringActor
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.javadsl.ActorContext
import org.apache.pekko.actor.typed.javadsl.Behaviors
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@Component
class UserActor : SpringActor {
    companion object {
        private val sessions = ConcurrentHashMap<String, WebSocketSession>()
        private var objectMapper: ObjectMapper? = null

        @JvmStatic
        fun setObjectMapper(mapper: ObjectMapper) {
            objectMapper = mapper
        }

        @JvmStatic
        fun registerSession(userId: String, session: WebSocketSession) {
            sessions[userId] = session
        }
    }

    override fun commandClass(): Class<*> = ChatRoomActor.ChatEvent::class.java

    override fun create(id: String): Behavior<ChatRoomActor.ChatEvent> {
        return Behaviors.setup { context ->
            context.log.info("Creating user actor with ID: {}", id)
            val userId = if (id.startsWith("user-")) id.substring(5) else id
            val session = sessions[userId]
            val mapper = objectMapper

            if (session == null || mapper == null) {
                context.log.error("Session or ObjectMapper not found for user ID: {}", userId)
                Behaviors.empty()
            } else {
                UserActorBehavior(context, session, mapper).create()
            }
        }
    }

    private class UserActorBehavior(
        private val context: ActorContext<ChatRoomActor.ChatEvent>,
        private val session: WebSocketSession,
        private val objectMapper: ObjectMapper
    ) {
        fun create(): Behavior<ChatRoomActor.ChatEvent> {
            return Behaviors.receive(ChatRoomActor.ChatEvent::class.java)
                .onMessage(ChatRoomActor.UserJoined::class.java, this::onUserJoined)
                .onMessage(ChatRoomActor.UserLeft::class.java, this::onUserLeft)
                .onMessage(ChatRoomActor.MessageReceived::class.java, this::onMessageReceived)
                .build()
        }

        private fun onUserJoined(event: ChatRoomActor.UserJoined): Behavior<ChatRoomActor.ChatEvent> {
            sendEvent("user_joined") {
                put("userId", event.userId)
                put("roomId", event.roomId)
            }
            return Behaviors.same()
        }

        private fun onUserLeft(event: ChatRoomActor.UserLeft): Behavior<ChatRoomActor.ChatEvent> {
            sendEvent("user_left") {
                put("userId", event.userId)
                put("roomId", event.roomId)
            }
            return Behaviors.same()
        }

        private fun onMessageReceived(event: ChatRoomActor.MessageReceived): Behavior<ChatRoomActor.ChatEvent> {
            sendEvent("message") {
                put("userId", event.userId)
                put("message", event.message)
                put("roomId", event.roomId)
            }
            return Behaviors.same()
        }

        private fun sendEvent(type: String, builder: ObjectNode.() -> Unit) {
            try {
                val eventNode = objectMapper.createObjectNode()
                eventNode.put("type", type)
                eventNode.builder()
                if (session.isOpen) {
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(eventNode)))
                }
            } catch (e: IOException) {
                context.log.error("Failed to send message to WebSocket", e)
            }
        }
    }
}
