package com.teamam.quiz.roster;

/** One roster line. name may be null when the CSV has only roll numbers. */
public record RosterEntry(String rollNo, String name) {
}
