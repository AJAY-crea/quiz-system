package com.teamam.quiz.export;

import com.teamam.quiz.attendance.AttendanceReport;
import com.teamam.quiz.attendance.AttendanceRow;
import com.teamam.quiz.attendance.AttendanceService;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.domain.Student;
import com.teamam.quiz.repository.QuestionRepository;
import com.teamam.quiz.repository.QuizSessionRepository;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.repository.StudentRepository;
import com.teamam.quiz.scoring.ScoringService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Module 11, basic (mid-demo) version: one row per rostered student in roll-number order,
 * absentees included. Marks follow ScoringService.marksFor; a typed answer shows "review" until
 * the instructor marks it. Analytics columns come at final.
 */
@Service
public class ExportService {

    static final String TYPED_PENDING = "review";

    private final QuizSessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ResponseRepository responseRepository;
    private final StudentRepository studentRepository;
    private final AttendanceService attendanceService;

    public ExportService(
            QuizSessionRepository sessionRepository,
            QuestionRepository questionRepository,
            ResponseRepository responseRepository,
            StudentRepository studentRepository,
            AttendanceService attendanceService) {
        this.sessionRepository = sessionRepository;
        this.questionRepository = questionRepository;
        this.responseRepository = responseRepository;
        this.studentRepository = studentRepository;
        this.attendanceService = attendanceService;
    }

    /** The score sheet as an Excel file. */
    @Transactional(readOnly = true)
    public byte[] xlsx(Long sessionId) {
        return toXlsx(build(sessionId));
    }

    /** The same score sheet as CSV (backup if Excel ever misbehaves). */
    @Transactional(readOnly = true)
    public byte[] csv(Long sessionId) {
        return toCsv(build(sessionId));
    }

    ScoreSheet build(Long sessionId) {
        QuizSession session = sessionRepository
                .findById(sessionId)
                .orElseThrow(() -> new NoSuchElementException("session not found: " + sessionId));
        List<Question> questions = questionRepository.findBySessionIdOrderByOrderIndex(sessionId).stream()
                .filter(q -> q.getReleasedAt() != null)
                .toList();
        AttendanceReport attendance = attendanceService.derive(sessionId);

        Map<String, Response> responses = new HashMap<>();
        for (Response r : responseRepository.findBySessionId(sessionId)) {
            responses.put(r.getRollNo() + "#" + r.getQuestion().getId(), r);
        }
        Map<String, String> names = studentRepository.findAllById(session.getRosterRollNumbers()).stream()
                .filter(s -> s.getName() != null)
                .collect(Collectors.toMap(Student::getRollNo, Student::getName));

        List<String> header = new ArrayList<>(List.of("Roll No", "Name"));
        questions.forEach(q -> header.add("Q" + q.getOrderIndex()));
        header.addAll(List.of("Total", "Attendance", "Flags"));

        List<List<Object>> rows = new ArrayList<>();
        Map<String, AttendanceRow> byRoll = attendance.rows().stream()
                .collect(Collectors.toMap(AttendanceRow::rollNo, Function.identity()));
        for (AttendanceRow student : attendance.rows()) {
            List<Object> row = new ArrayList<>();
            row.add(student.rollNo());
            row.add(names.getOrDefault(student.rollNo(), ""));
            double total = 0;
            for (Question q : questions) {
                Object mark = markFor(q, responses.get(student.rollNo() + "#" + q.getId()));
                if (mark instanceof Double d) {
                    total += d;
                }
                row.add(mark);
            }
            row.add(total);
            row.add(byRoll.get(student.rollNo()).status().name());
            row.add(String.join("; ", student.flags()));
            rows.add(row);
        }

        long awaitingMarks = rows.stream()
                .flatMap(List::stream)
                .filter(TYPED_PENDING::equals)
                .count();
        List<List<String>> about = List.of(
                List.of("Session", "#" + session.getId() + " " + session.getTitle()),
                List.of("Generated", Instant.now().toString()),
                List.of("Marking", "Multiple choice: the question's marks if correct and on time, else 0. Blank = not answered."),
                List.of("Late answers", "Kept as evidence but scored 0."),
                List.of("Typed answers", "Marked by the instructor. \"" + TYPED_PENDING + "\" = not marked yet; not in Total. "
                        + "Still to mark when this file was made: " + awaitingMarks + "."),
                List.of("Attendance", attendance.basis().equals(AttendanceReport.BASIS_JOINED_ONLY)
                        ? "No question released: present = joined."
                        : "Present = answered at least " + Math.round(attendance.threshold() * 100)
                                + "% of released questions (late answers count)."),
                List.of("Flags", "Advisory signals for review, never penalties."));
        return new ScoreSheet("Session " + session.getId(), header, rows, about);
    }

    /** One cell: the marks earned, "review" for a typed answer not marked yet, blank if not answered. */
    private static Object markFor(Question question, Response response) {
        if (response != null && ScoringService.awaitsMarking(question, response)) {
            return TYPED_PENDING;
        }
        return ScoringService.marksFor(question, response);
    }

    /** Writes the sheet to .xlsx: bold header, frozen first row, sensible column widths, an About sheet. */
    private static byte[] toXlsx(ScoreSheet sheet) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            bold.setFont(font);

            Sheet scores = workbook.createSheet("Scores");
            writeRow(scores.createRow(0), new ArrayList<>(sheet.header()), bold);
            for (int i = 0; i < sheet.rows().size(); i++) {
                writeRow(scores.createRow(i + 1), sheet.rows().get(i), null);
            }
            scores.createFreezePane(2, 1);
            for (int c = 0; c < sheet.header().size(); c++) {
                scores.setColumnWidth(c, Math.min(60, Math.max(8, widestText(sheet, c) + 2)) * 256);
            }

            Sheet about = workbook.createSheet("About");
            for (int i = 0; i < sheet.about().size(); i++) {
                writeRow(about.createRow(i), new ArrayList<>(sheet.about().get(i)), i == 0 ? bold : null);
            }
            about.setColumnWidth(0, 16 * 256);
            about.setColumnWidth(1, 90 * 256);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** setCellValue(String) always stores text, never a formula, so "=..." in a name is harmless in .xlsx. */
    private static void writeRow(Row row, List<Object> values, CellStyle style) {
        for (int c = 0; c < values.size(); c++) {
            Object value = values.get(c);
            if (value == null) {
                continue;
            }
            Cell cell = row.createCell(c);
            if (value instanceof Double d) {
                cell.setCellValue(d);
            } else {
                cell.setCellValue(value.toString());
            }
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    /** Longest text in a column, to set its width. */
    private static int widestText(ScoreSheet sheet, int column) {
        int widest = sheet.header().get(column).length();
        for (List<Object> row : sheet.rows()) {
            Object value = row.get(column);
            if (value != null) {
                widest = Math.max(widest, value.toString().length());
            }
        }
        return widest;
    }

    /** Writes the sheet as CSV text (with a marker so Excel reads names correctly). */
    private static byte[] toCsv(ScoreSheet sheet) {
        StringBuilder csv = new StringBuilder("﻿"); // BOM so Excel reads UTF-8 names correctly
        csv.append(sheet.header().stream().map(ExportService::csvField).collect(Collectors.joining(","))).append("\r\n");
        for (List<Object> row : sheet.rows()) {
            csv.append(row.stream().map(ExportService::csvField).collect(Collectors.joining(","))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * CSV has no "this is text" marker, so a value starting with = + - @ would run as a formula when
     * the instructor opens the file (CSV injection). Prefixing a quote makes spreadsheets show it as text.
     */
    static String csvField(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double d) {
            return d == Math.rint(d) ? String.valueOf(d.longValue()) : d.toString();
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
