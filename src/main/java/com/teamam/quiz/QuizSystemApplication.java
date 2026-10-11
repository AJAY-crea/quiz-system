package com.teamam.quiz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the whole app: web server, WebSocket, database. Run with "java -jar". */
@SpringBootApplication
public class QuizSystemApplication {

    /** Program entry point: boots Spring, which creates every service and controller. */
    public static void main(String[] args) {
        SpringApplication.run(QuizSystemApplication.class, args);
    }
}
