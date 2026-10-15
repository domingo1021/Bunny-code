package com.bunnycode.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Counts JDBC statements Hibernate prepares (requires {@code hibernate.generate_statistics}).
 * Asserting a constant count for 1 row and for many rows is how the suite catches N+1 queries.
 */
public final class QueryCounter {

    private final Statistics statistics;

    public QueryCounter(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        if (!statistics.isStatisticsEnabled()) {
            throw new IllegalStateException("Enable hibernate.generate_statistics for query counting");
        }
    }

    public void reset() {
        statistics.clear();
    }

    public long statements() {
        return statistics.getPrepareStatementCount();
    }

    /** Runs {@code action} and returns how many statements it prepared. */
    public long count(Runnable action) {
        reset();
        action.run();
        return statements();
    }
}
