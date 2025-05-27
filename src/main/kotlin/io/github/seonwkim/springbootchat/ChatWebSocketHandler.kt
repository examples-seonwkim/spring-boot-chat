package io.github.seonwkim.springbootchat

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.seonwkim.core.SpringActorRef
import io.github.seonwkim.core.SpringActorSystem
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.io.IOException
import java.util.UUID

/**
 * WebSocket handler for chat messages. Handles WebSocket connections and messages,
 * and connects them to the actor system.
 */
@Component
class ChatWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val chatService: ChatService,
    private val actorSystem: SpringActorSystem
) : TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = UUID.randomUUID().toString()
        session.attributes["userId"] = userId

        chatService.registerSession(userId, session)

        try {
            val response: ObjectNode = objectMapper.createObjectNode().apply {
                put("type", "connected")
                put("userId", userId)
            }
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(response)))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as? String ?: return
        val payload = objectMapper.readTree(message.payload)
        val type = payload["type"]?.asText()

        when (type) {
            "join" -> handleJoinRoom(session, userId, payload)
            "leave" -> handleLeaveRoom(session, userId)
            "message" -> handleChatMessage(session, userId, payload)
            else -> sendErrorMessage(session, "Unknown message type: $type")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = session.attributes["userId"] as? String
        if (userId != null) {
            chatService.removeSession(userId)
        }
    }

    private fun handleJoinRoom(session: WebSocketSession, userId: String, payload: JsonNode) {
        val roomId = payload["roomId"]?.asText() ?: return

        try {
            val userActorContext = UserActorContext("user-$userId", session)

            val actorRefFuture = actorSystem.spawn(UserActor.Command::class.java, userActorContext)

            actorRefFuture.thenAccept { actorRef ->
                chatService.joinRoom(userId, roomId, actorRef.ref)

                try {
                    val response: ObjectNode = objectMapper.createObjectNode().apply {
                        put("type", "joined")
                        put("roomId", roomId)
                    }
                    session.sendMessage(TextMessage(objectMapper.writeValueAsString(response)))
                } catch (e: IOException) {
                    e.printStackTrace()
                    sendErrorMessage(session, "Failed to send join confirmation: ${e.message}")
                }
            }.exceptionally { ex ->
                ex.printStackTrace()
                sendErrorMessage(session, "Failed to create actor: ${ex.message}")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sendErrorMessage(session, "Failed to join room: ${e.message}")
        }
    }

    private fun handleLeaveRoom(session: WebSocketSession, userId: String) {
        val roomId = chatService.getUserRoom(userId)
        if (roomId != null) {
            chatService.leaveRoom(userId, roomId)

            try {
                val response: ObjectNode = objectMapper.createObjectNode().apply {
                    put("type", "left")
                    put("roomId", roomId)
                }
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(response)))
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun handleChatMessage(session: WebSocketSession, userId: String, payload: JsonNode) {
        val roomId = chatService.getUserRoom(userId)
        if (roomId != null) {
            val messageText = payload["message"]?.asText()
            if (messageText != null) {
                chatService.sendMessage(userId, roomId, messageText)
            } else {
                sendErrorMessage(session, "Message content missing")
            }
        } else {
            sendErrorMessage(session, "You are not in a room")
        }
    }

    private fun sendErrorMessage(session: WebSocketSession, errorMessage: String) {
        try {
            if (session.isOpen) {
                val response: ObjectNode = objectMapper.createObjectNode().apply {
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
