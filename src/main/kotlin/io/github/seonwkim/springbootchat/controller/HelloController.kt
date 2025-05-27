package io.github.seonwkim.springbootchat.controller

import io.github.seonwkim.core.SpringActorSystem
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HelloController(
    private val actorSystem: SpringActorSystem
) {

    @GetMapping("/hello")
    fun hello(): String {
        return "hello";
    }

}
