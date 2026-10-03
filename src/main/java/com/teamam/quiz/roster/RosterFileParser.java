package com.teamam.quiz.roster;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Parses a roster file: one student per line, "ROLLNO" or "ROLLNO,Name" (everything after the
 * first comma is the name, so "CS23B065,Reddy, Ajay" keeps its comma), with an optional header row.
 */
public final class RosterFileParser {

    private static final Set<String> HEADER_TOKENS = Set.of("rollno", "roll_no", "roll number", "roll no");

    private RosterFileParser() {
    }

    public static List<RosterEntry> parse(MultipartFile file) throws IOException {
        List<RosterEntry> entries = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.replace("﻿", "").trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int comma = trimmed.indexOf(',');
                String rollNo = (comma < 0 ? trimmed : trimmed.substring(0, comma)).trim();
                String name = comma < 0 ? null : unquote(trimmed.substring(comma + 1).trim());
                if (firstLine) {
                    firstLine = false;
                    if (HEADER_TOKENS.contains(rollNo.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                }
                entries.add(new RosterEntry(rollNo.toUpperCase(Locale.ROOT), name == null || name.isEmpty() ? null : name));
            }
        }
        return entries;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }
}
