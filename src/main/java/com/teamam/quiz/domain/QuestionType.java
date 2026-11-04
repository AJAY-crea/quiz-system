package com.teamam.quiz.domain;

/**
 * MCQ = one correct option; MULTI = several correct options, scored only if the student picks exactly
 * those; both auto-scored. TYPED = free text marked by the instructor.
 */
public enum QuestionType {
    MCQ,
    TYPED,
    MULTI;

    /** Is this answered by choosing options (as opposed to typing)? */
    public boolean isChoice() {
        return this != TYPED;
    }
}
