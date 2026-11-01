package com.teamam.quiz.loadtest;

import java.util.List;

/**
 * What a load test measured. acceptedOnTime should equal clients, and storedAnswers proves each
 * answer was stored once even though every client sent it twice. Latency is send-to-ack on this machine.
 */
public record LoadReport(
        int clients, int joined, int connected, int acceptedOnTime, int acceptedLate, int duplicates, int rejected,
        int noAck, long storedAnswers, long medianAckMs, long p95AckMs, long maxAckMs, long totalMs, List<String> errors) {

    /** True if every simulated student got in, was accepted on time and was stored exactly once. */
    public boolean allGood() {
        return joined == clients && connected == clients && acceptedOnTime == clients && acceptedLate == 0
                && rejected == 0 && noAck == 0 && duplicates == clients && storedAnswers == clients;
    }

    /** A plain summary for the console. */
    public String summary() {
        return String.format(
                "Load test: %d clients | joined %d | connected %d | accepted on time %d, late %d | resends recognised %d"
                        + " | rejected %d | no reply %d | stored %d%nFirst-answer reply time: median %d ms, 95%% within %d ms, slowest %d ms."
                        + " All sent and confirmed in %d ms. %s",
                clients, joined, connected, acceptedOnTime, acceptedLate, duplicates, rejected, noAck, storedAnswers,
                medianAckMs, p95AckMs, maxAckMs, totalMs, allGood() ? "PASS" : "FAIL " + errors);
    }
}
