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
 * Parses a roster file of one roll number per line, optionally "ROLLNO,Name" and an
 * optional header row (rollno / roll_no / roll number).
 */
public final class RosterFileParser {

    private static final Set<String> HEADER_TOKENS = Set.of("rollno", "roll_no", "roll number", "roll no");

    private RosterFileParser() {
    }

    public static List<String> parseRollNumbers(MultipartFile file) throws IOException {
        List<String> rollNumbers = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String rollNo = trimmed.split(",")[0].trim();
                if (firstLine) {
                    firstLine = false;
                    if (HEADER_TOKENS.contains(rollNo.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                }
                rollNumbers.add(rollNo.toUpperCase(Locale.ROOT));
            }
        }
        return rollNumbers;
    }
}
