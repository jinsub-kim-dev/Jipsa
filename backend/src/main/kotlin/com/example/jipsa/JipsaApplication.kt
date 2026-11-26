package com.example.jipsa

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class JipsaApplication

fun main(args: Array<String>) {
	runApplication<JipsaApplication>(*args)
}
