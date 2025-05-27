package io.github.seonwkim.springbootchat

import io.github.seonwkim.core.SpringActorSystem
import io.github.seonwkim.core.SpringShardedActorRef
import org.apache.pekko.actor.typed.ActorRef
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that handles interactions with chat rooms.
 * It serves as an intermediary between WebSocket sessions and the actor system.
 */
@Service
class ChatService(
    private val actorSystem: SpringActorSystem
) {
    private val sessions: MutableMap<String, WebSocketSession> = ConcurrentHashMap()
    private val userRooms: MutableMap<String, String> = ConcurrentHashMap()

    /**
     * Registers a WebSocket session for a user.
     *
     * @param userId The ID of the user
     * @param session The WebSocket session
     */
    fun registerSession(userId: String, session: WebSocketSession) {
        sessions[userId] = session
    }

    /**
     * Removes a WebSocket session for a user.
     *
     * @param userId The ID of the user
     */
    fun removeSession(userId: String) {
        sessions.remove(userId)
        val roomId = userRooms.remove(userId)
        if (roomId != null) {
            leaveRoom(userId, roomId)
        }
    }

    /**
     * Joins a chat room.
     *
     * @param userId The ID of the user
     * @param roomId The ID of the room
     * @param userRef The actor reference for the user
     */
    fun joinRoom(userId: String, roomId: String, userRef: ActorRef<UserActor.Command>) {
        val roomRef: SpringShardedActorRef<ChatRoomActor.Command> =
            actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId)

        roomRef.tell(ChatRoomActor.JoinRoom(userId, userRef))
        userRooms[userId] = roomId
    }

    /**
     * Leaves a chat room.
     *
     * @param userId The ID of the user
     * @param roomId The ID of the room
     */
    fun leaveRoom(userId: String, roomId: String) {
        val roomRef: SpringShardedActorRef<ChatRoomActor.Command> =
            actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId)

        roomRef.tell(ChatRoomActor.LeaveRoom(userId))
        userRooms.remove(userId)
    }

    /**
     * Sends a message to a chat room.
     *
     * @param userId The ID of the user
     * @param roomId The ID of the room
     * @param message The message to send
     */
    fun sendMessage(userId: String, roomId: String, message: String) {
        val roomRef: SpringShardedActorRef<ChatRoomActor.Command> =
            actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId)

        roomRef.tell(ChatRoomActor.SendMessage(userId, message))
    }

    /**
     * Gets the room ID for a user.
     *
     * @param userId The ID of the user
     * @return The room ID, or null if the user is not in a room
     */
    fun getUserRoom(userId: String): String? = userRooms[userId]
}
