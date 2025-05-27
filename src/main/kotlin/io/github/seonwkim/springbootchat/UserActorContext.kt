package io.github.seonwkim.springbootchat

import io.github.seonwkim.core.SpringActorContext
import org.springframework.web.socket.WebSocketSession

data class UserActorContext(
    val id: String,
    val session: WebSocketSession
) : SpringActorContext {

    override fun actorId(): String = id
}
