package com.bunnycode.battle.infrastructure;

import com.bunnycode.battle.application.BattleProperties;
import com.bunnycode.battle.application.JudgeDispatcher;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * A virtual thread per submission, capped at {@code bunny.battle.judge-concurrency} in flight so
 * a burst cannot overwhelm the sandbox hosts. Deliberately not exposed as an {@code Executor}
 * bean, which would replace Spring Boot's application task executor.
 */
@Component
class VirtualThreadJudgeDispatcher implements JudgeDispatcher, AutoCloseable {

    private static final long SHUTDOWN_GRACE_MILLIS = 15_000;

    private final SimpleAsyncTaskExecutor executor;

    VirtualThreadJudgeDispatcher(BattleProperties properties) {
        this.executor = new SimpleAsyncTaskExecutor("battle-judge-");
        this.executor.setVirtualThreads(true);
        this.executor.setConcurrencyLimit(properties.judgeConcurrency());
        this.executor.setTaskTerminationTimeout(SHUTDOWN_GRACE_MILLIS);
    }

    @Override
    public void dispatch(Runnable judging) {
        executor.execute(judging);
    }

    @Override
    public void close() {
        executor.close();
    }
}
