package com.financetracker.backend.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RecurringTransactionSchedulerTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RecurringTransactionProcessor.class, () -> mock(RecurringTransactionProcessor.class))
            .withUserConfiguration(RecurringTransactionScheduler.class);

    @Test
    void schedulerCanBeDisabledByConfiguration() {
        contextRunner.withPropertyValues("recurring.scheduler.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(RecurringTransactionScheduler.class));
    }

    @Test
    void schedulerIsEnabledByDefault() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(RecurringTransactionScheduler.class));
    }
}
