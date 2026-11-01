package com.teamam.quiz.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoadTestRunnerTest {

    // The design doc fixes the count "once we have her class list". We don't have one yet, so the
    // test uses a large lecture class; bigger runs use the command line (see the README).
    private static final int CLASS_SIZE = 150;

    @Autowired
    private LoadTestRunner runner;

    // Guards against: answers lost, doubled or rejected when a whole class submits at the same moment,
    // through real WebSockets (each client also resends once, so the dedup path is loaded too).
    @Test
    void sustainsFullRosterConcurrentSubmit() throws Exception {
        LoadReport report = runner.runFresh(CLASS_SIZE);

        System.out.println(report.summary());
        assertThat(report.errors()).isEmpty();
        assertThat(report.joined()).isEqualTo(CLASS_SIZE);
        assertThat(report.connected()).isEqualTo(CLASS_SIZE);
        assertThat(report.acceptedOnTime()).isEqualTo(CLASS_SIZE);
        assertThat(report.duplicates()).isEqualTo(CLASS_SIZE);
        assertThat(report.storedAnswers()).isEqualTo(CLASS_SIZE);
        assertThat(report.allGood()).isTrue();
    }
}
