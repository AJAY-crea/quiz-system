package com.teamam.quiz.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints the instructor's link when the app starts. On the instructor's own laptop the plain
 * address works too, but inside Docker (or opened from another device) the browser is not "this
 * laptop", so the link is how the instructor, or a grader, gets in.
 */
@Component
public class InstructorLinkLog {

    private static final Logger LOG = LoggerFactory.getLogger(InstructorLinkLog.class);

    private final InstructorKey instructorKey;

    public InstructorLinkLog(InstructorKey instructorKey) {
        this.instructorKey = instructorKey;
    }

    /** Runs once the web server is listening, so the port in the link is the real one. */
    @EventListener
    public void printLink(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof WebServerApplicationContext web) {
            LOG.warn("Instructor dashboard: http://localhost:{}/instructor.html?key={}  (keep this link private)",
                    web.getWebServer().getPort(), instructorKey.value());
        }
    }
}
