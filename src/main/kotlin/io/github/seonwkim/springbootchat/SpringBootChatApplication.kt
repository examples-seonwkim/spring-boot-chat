package io.github.seonwkim.springbootchat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SpringBootChatApplication

fun main(args: Array<String>) {
    runApplication<SpringBootChatApplication>(*args)
}
