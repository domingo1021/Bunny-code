package com.bunnycode.battle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One judged call of the question's entry function: {@code argumentsJson} is a JSON array spread
 * into the call, {@code expectedJson} the JSON value it must return. (Table {@code answer} in v1.)
 */
@Entity
@Table(name = "answer")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id", columnDefinition = "int unsigned")
    private Long id;

    @Column(name = "answer_number", nullable = false)
    private int number;

    @Column(name = "arguments_json", nullable = false, length = 1000)
    private String argumentsJson;

    @Column(name = "expected_json", nullable = false, length = 500)
    private String expectedJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, columnDefinition = "int unsigned")
    private Question question;

    protected TestCase() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

    public String getExpectedJson() {
        return expectedJson;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof TestCase that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return TestCase.class.hashCode();
    }

    @Override
    public String toString() {
        return "TestCase[id=" + id + ", number=" + number + "]";
    }
}
