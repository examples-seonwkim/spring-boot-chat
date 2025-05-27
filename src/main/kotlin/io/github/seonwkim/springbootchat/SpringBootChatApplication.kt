package io.github.seonwkim.springbootchat

import io.github.seonwkim.core.EnableActorSupport
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableActorSupport
class SpringBootChatApplication

fun main(args: Array<String>) {
    runApplication<SpringBootChatApplication>(*args)
}
