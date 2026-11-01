package com.teamam.quiz.export;

import java.util.List;

/**
 * The score sheet as plain data, before it becomes .xlsx or .csv. Each cell is a String, a Double
 * (a mark or total), or null (blank: not answered). Extra sheets appear only in the .xlsx.
 */
record ScoreSheet(
        String title, List<String> header, List<List<Object>> rows, List<List<String>> about, List<Extra> extras) {

    /** One more worksheet: its name, heading row and rows. */
    record Extra(String name, List<String> header, List<List<Object>> rows) {
    }
}
