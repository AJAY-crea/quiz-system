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
