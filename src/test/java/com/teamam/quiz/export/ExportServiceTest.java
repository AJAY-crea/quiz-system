package com.teamam.quiz.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamam.quiz.TestQuiz;
import com.teamam.quiz.domain.Question;
import com.teamam.quiz.domain.QuestionType;
import com.teamam.quiz.domain.QuizSession;
import com.teamam.quiz.domain.Response;
import com.teamam.quiz.questionbank.QuestionBankService;
import com.teamam.quiz.repository.ResponseRepository;
import com.teamam.quiz.roster.RosterEntry;
import com.teamam.quiz.roster.RosterService;
import com.teamam.quiz.scoring.ScoringService;
import com.teamam.quiz.session.SessionEngine;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ExportServiceTest {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private ExportService export;

    @Autowired
    private SessionEngine engine;

    @Autowired
    private RosterService roster;

    @Autowired
    private QuestionBankService bank;

    @Autowired
    private ResponseRepository responses;

    @Autowired
    private ScoringService scoring;

    // Guards against: a sheet that needs manual fixing before use: wrong order, missing absentees,
    // late answers scored, or a file Excel can't open (design doc risk #5: we reopen what we wrote).
    @Test
    void producesValidRosterOrderedXlsx() throws Exception {
        Fixture f = fixture();

        byte[] xlsx = export.xlsx(f.sessionId);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheet("Scores");
            assertThat(texts(sheet.getRow(0)))
                    .containsExactly("Roll No", "Name", "Q1", "Q2", "Q3", "Total", "Attendance", "Flags");
            assertThat(sheet.getLastRowNum()).isEqualTo(5);

            List<String> rollOrder = new ArrayList<>();
            for (int r = 1; r <= 5; r++) {
                rollOrder.add(sheet.getRow(r).getCell(0).getStringCellValue());
            }
            assertThat(rollOrder).containsExactly("CS23B801", "CS23B802", "CS23B803", "CS23B804", "CS23B805");

            Row ajay = sheet.getRow(1);
            assertThat(ajay.getCell(1).getStringCellValue()).isEqualTo("Ajay");
            assertThat(ajay.getCell(2).getNumericCellValue()).isEqualTo(1.0);     // correct, on time
            assertThat(ajay.getCell(3).getNumericCellValue()).isEqualTo(0.0);     // correct but late
            assertThat(ajay.getCell(4).getStringCellValue()).isEqualTo("review"); // typed, not reviewed yet
            assertThat(ajay.getCell(5).getNumericCellValue()).isEqualTo(1.0);
            assertThat(ajay.getCell(6).getStringCellValue()).isEqualTo("PRESENT");

            Row mukesh = sheet.getRow(2);
            assertThat(mukesh.getCell(2).getNumericCellValue()).isEqualTo(0.0);   // wrong
            assertThat(mukesh.getCell(3).getNumericCellValue()).isEqualTo(1.0);
            assertThat(isBlank(mukesh.getCell(4))).isTrue();                       // not answered
            assertThat(mukesh.getCell(5).getNumericCellValue()).isEqualTo(1.0);

            Row absentee = sheet.getRow(3);
            assertThat(isBlank(absentee.getCell(2))).isTrue();
            assertThat(absentee.getCell(5).getNumericCellValue()).isEqualTo(0.0);
            assertThat(absentee.getCell(6).getStringCellValue()).isEqualTo("ABSENT");

            assertThat(workbook.getSheet("About")).isNotNull();
        }
    }

    // Guards against: the teacher's marks not reaching the sheet, or not being added into the total.
    @Test
    void typedMarksReachTheSheetAndTheTotal() throws Exception {
        Fixture f = fixture();
        Response typed = responses.findBySessionId(f.sessionId).stream()
                .filter(r -> r.getTypedAnswer() != null).findFirst().orElseThrow();
        scoring.markOne(f.sessionId, typed.getId(), 0.5);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.xlsx(f.sessionId)))) {
            Row ajay = workbook.getSheet("Scores").getRow(1);
            assertThat(ajay.getCell(4).getNumericCellValue()).isEqualTo(0.5);
            assertThat(ajay.getCell(5).getNumericCellValue()).isEqualTo(1.5);
        }
    }

    /**
     * 20 responses marked by hand. Q1 = 1 mark, Q2 = 2 marks, Q3 = typed out of 5, Q4 = 1 mark.
     *            Q1      Q2           Q3                Q4          Total
     * M801       right   right (2)    4.5               right       8.5
     * M802       wrong   right (2)    3                 right       6
     * M803       right   right, late  late, marks 0     wrong       1
     * M804       wrong   wrong        0                 wrong       0
     * M805       right   wrong        not marked yet    right,late  1
     * Guards against: the generated sheet disagreeing with a teacher's own arithmetic.
     */
    @Test
    void reproducesHandComputedMarksheet() throws Exception {
        QuizSession session = engine.createSession("hand marked");
        Long sid = session.getId();
        roster.uploadRoster(sid, List.of("M801", "M802", "M803", "M804", "M805"));
        Question q1 = bank.add(sid, 1, QuestionType.MCQ, "Q1", TestQuiz.OPTIONS, 0, 60, 1);
        Question q2 = bank.add(sid, 2, QuestionType.MCQ, "Q2", TestQuiz.OPTIONS, 1, 60, 2);
        Question q3 = bank.add(sid, 3, QuestionType.TYPED, "Q3", null, null, 60, 5);
        Question q4 = bank.add(sid, 4, QuestionType.MCQ, "Q4", TestQuiz.OPTIONS, 2, 60, 1);
        for (Question q : List.of(q1, q2, q3, q4)) {
            engine.release(sid, q.getId());
        }
        Object[][] sheet = {
            {"M801", true, true, true, true, 4.5, true, true, true},
            {"M802", false, true, true, true, 3.0, true, true, true},
            {"M803", true, true, true, false, null, false, false, true},
            {"M804", false, true, false, true, 0.0, true, false, true},
            {"M805", true, true, false, true, null, true, true, false},
        };
        for (Object[] s : sheet) {
            String roll = (String) s[0];
            save(session, q1, roll, (Boolean) s[1], (Boolean) s[2], null);
            save(session, q2, roll, (Boolean) s[3], (Boolean) s[4], null);
            save(session, q3, roll, null, (Boolean) s[6], "answer of " + roll);
            save(session, q4, roll, (Boolean) s[7], (Boolean) s[8], null);
            Response typed = responses.findBySessionIdAndQuestionIdAndRollNo(sid, q3.getId(), roll).orElseThrow();
            typed.setAwardedMarks((Double) s[5]);
        }

        Object[][] expected = {
            {"M801", 1.0, 2.0, 4.5, 1.0, 8.5},
            {"M802", 0.0, 2.0, 3.0, 1.0, 6.0},
            {"M803", 1.0, 0.0, 0.0, 0.0, 1.0},
            {"M804", 0.0, 0.0, 0.0, 0.0, 0.0},
            {"M805", 1.0, 0.0, "review", 0.0, 1.0},
        };
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.xlsx(sid)))) {
            Sheet scores = workbook.getSheet("Scores");
            for (int r = 0; r < expected.length; r++) {
                Row row = scores.getRow(r + 1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo(expected[r][0]);
                for (int c = 1; c < expected[r].length; c++) {
                    Cell cell = row.getCell(c + 1);
                    if (expected[r][c] instanceof String text) {
                        assertThat(cell.getStringCellValue()).isEqualTo(text);
                    } else {
                        assertThat(cell.getNumericCellValue()).as("%s column %d", expected[r][0], c).isEqualTo(expected[r][c]);
                    }
                }
            }
        }
    }

    // Guards against: the analytics or typed answers missing from the file, or a student's typed
    // "=formula" running in Excel.
    @Test
    void xlsxCarriesQuestionAnalysisAndTypedAnswers() throws Exception {
        Fixture f = fixture();
        Response typed = responses.findBySessionId(f.sessionId).stream()
                .filter(r -> r.getTypedAnswer() != null).findFirst().orElseThrow();
        typed.setTypedAnswer("=1+1");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(export.xlsx(f.sessionId)))) {
            Sheet analysis = workbook.getSheet("Question analysis");
            assertThat(analysis.getLastRowNum()).isEqualTo(3);
            assertThat(analysis.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Q1");
            assertThat(analysis.getRow(1).getCell(4).getNumericCellValue()).isEqualTo(2.0); // answered on time
            assertThat(analysis.getRow(1).getCell(6).getNumericCellValue()).isEqualTo(50.0); // % correct
            assertThat(analysis.getRow(1).getCell(8).getStringCellValue()).contains("(correct)");

            Row answer = workbook.getSheet("Typed answers").getRow(1);
            assertThat(answer.getCell(1).getStringCellValue()).isEqualTo("CS23B801");
            assertThat(answer.getCell(2).getCellType()).isEqualTo(org.apache.poi.ss.usermodel.CellType.STRING);
            assertThat(answer.getCell(2).getStringCellValue()).isEqualTo("=1+1");
            assertThat(answer.getCell(3).getStringCellValue()).isEqualTo("review");
        }
    }

    // Guards against: a roster name like "=HYPERLINK(...)" running as a formula when the CSV is opened.
    @Test
    void csvEscapesSpreadsheetFormulaInjection() {
        QuizSession session = engine.createSession("csv");
        roster.uploadRosterEntries(session.getId(), List.of(
                new RosterEntry("CS23B811", "=HYPERLINK(\"http://evil\",\"click\")"),
                new RosterEntry("CS23B812", "Reddy, Ajay")));

        String csv = new String(export.csv(session.getId()), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\",\"\"click\"\")\"");
        assertThat(csv).contains("\"Reddy, Ajay\"");
        assertThat(csv).doesNotContain(",=HYPERLINK");
    }

    private record Fixture(Long sessionId) {
    }

    /** 5 rostered students; Ajay and Mukesh answer, the other three are absent. Q3 is a typed question. */
    private Fixture fixture() {
        QuizSession session = engine.createSession("export fixture");
        Long sid = session.getId();
        roster.uploadRosterEntries(sid, List.of(
                new RosterEntry("CS23B803", "Absent One"),
                new RosterEntry("CS23B801", "Ajay"),
                new RosterEntry("CS23B805", "Absent Three"),
                new RosterEntry("CS23B802", "Mukesh"),
                new RosterEntry("CS23B804", "Absent Two")));
        String ticket = TestQuiz.joinTicket(ctx, sid);
        roster.join(sid, "CS23B801", "dev-801", ticket);
        roster.join(sid, "CS23B802", "dev-802", ticket);

        Question q1 = bank.add(sid, 1, QuestionType.MCQ, "Q1", TestQuiz.OPTIONS, 0, 60);
        Question q2 = bank.add(sid, 2, QuestionType.MCQ, "Q2", TestQuiz.OPTIONS, 1, 60);
        Question q3 = bank.add(sid, 3, QuestionType.TYPED, "Q3", null, null, 60);
        for (Question q : List.of(q1, q2, q3)) {
            engine.release(sid, q.getId());
        }

        save(session, q1, "CS23B801", true, true, null);
        save(session, q2, "CS23B801", true, false, null);
        save(session, q3, "CS23B801", null, true, "V = IR");
        save(session, q1, "CS23B802", false, true, null);
        save(session, q2, "CS23B802", true, true, null);
        return new Fixture(sid);
    }

    private void save(QuizSession s, Question q, String rollNo, Boolean correct, boolean onTime, String typed) {
        Response r = new Response(s, q, rollNo);
        r.setSubmissionId(rollNo + "-" + q.getOrderIndex());
        r.setSubmittedAt(Instant.now());
        r.setOnTime(onTime);
        r.setCorrect(correct);
        r.setTypedAnswer(typed);
        responses.save(r);
    }

    private static List<String> texts(Row row) {
        List<String> out = new ArrayList<>();
        row.forEach(cell -> out.add(cell.getStringCellValue()));
        return out;
    }

    private static boolean isBlank(Cell cell) {
        return cell == null || cell.getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK;
    }
}
