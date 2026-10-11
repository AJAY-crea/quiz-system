package com.teamam.quiz.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One injectable server clock, so time-window rules can be tested with a clock the test controls. */
@Configuration
public class ClockConfig {

    /** The server clock. Tests swap in a fake clock to test time windows without waiting. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
