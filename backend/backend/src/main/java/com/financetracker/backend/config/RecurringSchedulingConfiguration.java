package com.financetracker.backend.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class RecurringSchedulingConfiguration {

    @Bean
    Clock recurringClock(@Value("${recurring.scheduler.zone:America/Toronto}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
