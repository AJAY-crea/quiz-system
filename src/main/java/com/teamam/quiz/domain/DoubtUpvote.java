package com.teamam.quiz.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Table: who upvoted which doubt. The unique rule allows one upvote per student per doubt, even in a race. */
@Entity
@Table(name = "doubt_upvotes", uniqueConstraints = @UniqueConstraint(columnNames = {"doubt_id", "roll_no"}))
public class DoubtUpvote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doubt_id", nullable = false)
    private Doubt doubt;

    @Column(name = "roll_no", nullable = false)
    private String rollNo;

    protected DoubtUpvote() {
    }

    public DoubtUpvote(Doubt doubt, String rollNo) {
        this.doubt = doubt;
        this.rollNo = rollNo;
    }
}
