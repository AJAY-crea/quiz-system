package com.teamam.quiz.questionbank;

import com.teamam.quiz.domain.QuestionType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses a question file, one question per line, with an optional header row:
 * type,seconds,marks,question,correct,option1,option2,...
 * MCQ,60,1,"Capital of India?",2,Mumbai,Delhi,Chennai   (correct counts from 1, as on the dashboard)
 * MULTI,60,2,"Which are prime?",1;3,2,4,5               (several correct: separate them with ;)
 * TYPED,120,5,"Explain Ohm's law",,                     (no correct option or options)
 * A value containing a comma goes in double quotes. Errors name the line, so she can fix the file.
 */
public final class QuestionFileParser {

    /** One parsed line, ready for QuestionBankService. Correct options count from 0. */
    public record ParsedQuestion(
            QuestionType type, int durationSeconds, int maxMarks, String text, List<Integer> correctOptionIndexes,
            List<String> options) {

        /** The single correct option, or null (typed questions). */
        public Integer correctOptionIndex() {
            return correctOptionIndexes.isEmpty() ? null : correctOptionIndexes.get(0);
        }
    }

    private QuestionFileParser() {
    }

    /** Reads every question, or throws IllegalArgumentException naming the first bad line. */
    public static List<ParsedQuestion> parse(InputStream file) throws IOException {
        List<ParsedQuestion> questions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.replace("﻿", "").trim();
                if (trimmed.isEmpty() || (lineNumber == 1 && trimmed.toLowerCase(Locale.ROOT).startsWith("type,"))) {
                    continue;
                }
                try {
                    questions.add(parseLine(splitCsv(trimmed)));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Line " + lineNumber + ": " + e.getMessage());
                }
            }
        }
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("The file has no questions.");
        }
        return questions;
    }

    /** Turns one line's columns into a question. */
    private static ParsedQuestion parseLine(List<String> columns) {
        if (columns.size() < 4) {
            throw new IllegalArgumentException("expected type,seconds,marks,question,...");
        }
        QuestionType type;
        try {
            type = QuestionType.valueOf(columns.get(0).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("type must be MCQ, MULTI or TYPED, not \"" + columns.get(0) + "\"");
        }
        int seconds = number(columns.get(1), "seconds");
        int marks = number(columns.get(2), "marks");
        String text = columns.get(3);
        if (seconds < 5) {
            throw new IllegalArgumentException("seconds must be at least 5");
        }
        if (text.isEmpty()) {
            throw new IllegalArgumentException("the question text is empty");
        }
        if (type == QuestionType.TYPED) {
            return new ParsedQuestion(type, seconds, marks, text, List.of(), List.of());
        }
        if (columns.size() < 5) {
            throw new IllegalArgumentException("a multiple-choice question needs a correct option number");
        }
        List<String> options = columns.subList(5, columns.size()).stream().filter(o -> !o.isEmpty()).toList();
        List<Integer> correct = Arrays.stream(columns.get(4).split("[;\\s]+"))
                .filter(part -> !part.isEmpty())
                .map(part -> number(part, "correct") - 1)
                .toList();
        return new ParsedQuestion(type, seconds, marks, text, correct, options);
    }

    /** Reads a whole number, with a clear message if it isn't one. */
    private static int number(String value, String column) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(column + " must be a whole number, not \"" + value + "\"");
        }
    }

    /** Splits a CSV line, keeping commas inside double quotes; "" inside quotes means one quote. */
    static List<String> splitCsv(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes && c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                current.append('"');
                i++;
            } else if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                columns.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        columns.add(current.toString().trim());
        return columns;
    }
}
