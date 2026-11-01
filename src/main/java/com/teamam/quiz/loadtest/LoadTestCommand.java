package com.teamam.quiz.loadtest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Runs the load test from the command line, prints the report and exits:
 * java -jar quiz-system.jar --quiz.loadtest.clients=300 --server.port=8090 --spring.datasource.url=jdbc:h2:mem:load
 * It writes hundreds of fake students, so it refuses any database that isn't clearly a throwaway one.
 */
@Component
@ConditionalOnProperty("quiz.loadtest.clients")
public class LoadTestCommand implements ApplicationRunner {

    private final LoadTestRunner runner;
    private final ConfigurableApplicationContext context;
    private final int clients;
    private final String databaseUrl;

    public LoadTestCommand(
            LoadTestRunner runner,
            ConfigurableApplicationContext context,
            @Value("${quiz.loadtest.clients}") int clients,
            @Value("${spring.datasource.url}") String databaseUrl) {
        this.runner = runner;
        this.context = context;
        this.clients = clients;
        this.databaseUrl = databaseUrl;
    }

    /** Runs once the server is up, then shuts the app down with exit code 0 (pass) or 1 (fail). */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!databaseUrl.contains("mem:") && !databaseUrl.contains("loadtest")) {
            System.out.println("Refusing to load-test the real database: " + databaseUrl
                    + "\nAdd --spring.datasource.url=jdbc:h2:mem:load (or a file path containing \"loadtest\").");
            System.exit(SpringApplication.exit(context, () -> 2));
        }
        LoadReport report = runner.runFresh(clients);
        System.out.println(report.summary());
        System.exit(SpringApplication.exit(context, () -> report.allGood() ? 0 : 1));
    }
}
