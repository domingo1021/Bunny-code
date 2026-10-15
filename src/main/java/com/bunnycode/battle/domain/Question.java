package com.bunnycode.battle.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A battle problem: a description, starter code, the function to implement and its test cases. */
@Entity
@Table(name = "question")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id", columnDefinition = "int unsigned")
    private Long id;

    @Column(name = "question_name", nullable = false, length = 30)
    private String name;

    @Column(name = "question_url", nullable = false, length = 100)
    private String descriptionKey;

    @Convert(converter = QuestionLevelConverter.class)
    @Column(name = "question_level", nullable = false, columnDefinition = "tinyint unsigned")
    private QuestionLevel level;

    @Column(name = "base_url", nullable = false, length = 100)
    private String starterCodeKey;

    @Column(name = "entry_function", nullable = false, length = 50)
    private String entryFunction;

    @OneToMany(mappedBy = "question")
    @OrderBy("number ASC")
    private List<TestCase> testCases = new ArrayList<>();

    protected Question() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public QuestionLevel getLevel() {
        return level;
    }

    public String getStarterCodeKey() {
        return starterCodeKey;
    }

    public String getEntryFunction() {
        return entryFunction;
    }

    public List<TestCase> getTestCases() {
        return Collections.unmodifiableList(testCases);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Question that && id != null && id.equals(that.getId());
    }

    @Override
    public int hashCode() {
        return Question.class.hashCode();
    }

    @Override
    public String toString() {
        return "Question[id=" + id + ", name=" + name + ", level=" + level + "]";
    }
}
