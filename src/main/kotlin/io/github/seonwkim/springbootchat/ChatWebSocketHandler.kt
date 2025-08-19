package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.seonwkim.core.SpringActorRef
import io.github.seonwkim.core.SpringActorSystem
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket handler for chat messages. Handles WebSocket connections and messages,
 * and connects them to the actor system.
 */
@Component
class ChatWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val actorSystem: SpringActorSystem
) : TextWebSocketHandler() {

    private val userActors = ConcurrentHashMap<String, SpringActorRef<UserActor.Command>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = UUID.randomUUID().toString()
        session.attributes["userId"] = userId

        val userActorContext = UserActor.UserActorContext(
            actorSystem = actorSystem,
            objectMapper = objectMapper,
            userId = userId,
            session = session
        )

        actorSystem.spawn(UserActor::class.java, userActorContext)
            .thenAccept { userActor ->
                userActors[userId] = userActor
                userActor.tell(UserActor.Connect())
            }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as String
        val payload = objectMapper.readTree(message.payload)

        when (payload.get("type").asText()) {
            "join" -> handleJoinRoom(userId, payload)
            "leave" -> handleLeaveRoom(userId)
            "message" -> handleChatMessage(userId, payload)
            else -> sendErrorMessage(session, "Unknown message type: ${payload.get("type").asText()}")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes["userId"] as String?
        val userActor = getUserActor(userId)

        if (userId != null && userActor != null) {
            actorSystem.stop(UserActor::class.java, userId)
            userActors.remove(userId)
        }
    }

    private fun handleJoinRoom(userId: String, payload: JsonNode) {
        val roomId = payload.get("roomId").asText()
        val userActor = getUserActor(userId)

        if (roomId != null && userActor != null) {
            userActor.tell(UserActor.JoinRoom(roomId))
        }
    }

    private fun handleLeaveRoom(userId: String) {
        getUserActor(userId)?.tell(UserActor.LeaveRoom())
    }

    private fun handleChatMessage(userId: String, payload: JsonNode) {
        val userActor = getUserActor(userId)
        val messageText = payload.get("message").asText()

        if (userActor != null && messageText != null) {
            userActor.tell(UserActor.SendMessage(messageText))
        }
    }

    private fun getUserActor(userId: String?): SpringActorRef<UserActor.Command>? {
        return userId?.let { userActors[it] }
    }

    private fun sendErrorMessage(session: WebSocketSession, errorMessage: String) {
        try {
            if (session.isOpen) {
                val response = objectMapper.createObjectNode().apply {
                    put("type", "error")
                    put("message", errorMessage)
                }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(response)))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
