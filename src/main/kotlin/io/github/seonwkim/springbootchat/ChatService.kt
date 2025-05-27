package io.github.seonwkim.springbootchat

import io.github.seonwkim.core.SpringActorSystem
import io.github.seonwkim.core.SpringShardedActorRef
import org.apache.pekko.actor.typed.ActorRef
import org.springframework.stereotype.Service
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

@Service
class ChatService(
    private val actorSystem: SpringActorSystem
) {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val userRooms = ConcurrentHashMap<String, String>()

    fun registerSession(userId: String, session: WebSocketSession) {
        sessions[userId] = session
    }

    fun removeSession(userId: String) {
        sessions.remove(userId)
        val roomId = userRooms.remove(userId)
        if (roomId != null) {
            leaveRoom(userId, roomId)
        }
    }

    fun joinRoom(userId: String, roomId: String, userRef: ActorRef<UserActor.Command>) {
        val roomRef: SpringShardedActorRef<ChatRoomActor.Command> =
            actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId)
        roomRef.tell(ChatRoomActor.JoinRoom(userId, userRef))
        userRooms[userId] = roomId
    }

    fun leaveRoom(userId: String, roomId: String) {
        val roomRef: SpringShardedActorRef<ChatRoomActor.Command> =
            actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId)
        roomRef.tell(ChatRoomActor.LeaveRoom(userId))
        userRooms.remove(userId)
    }

    fun sendMessage(userId: String, roomId: String, message: String) {
        val roomRef: SpringShardedActorRef<ChatRoomActor.Command> =
            actorSystem.entityRef(ChatRoomActor.TYPE_KEY, roomId)
        roomRef.tell(ChatRoomActor.SendMessage(userId, message))
    }

    fun getSession(userId: String): WebSocketSession? = sessions[userId]

    fun getUserRoom(userId: String): String? = userRooms[userId]

    fun registerUserRoom(userId: String, roomId: String) {
        userRooms[userId] = roomId
    }
}
