package com.financetracker.backend.migration;

import java.net.URI;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Fails before datasource initialization, even if an environment override points elsewhere. */
public class DisposableDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        requireDisposable(context.getEnvironment().getProperty("spring.datasource.url", ""));
    }

    public static void requireDisposable(String jdbcUrl) {
        URI uri;
        try { uri = URI.create(jdbcUrl.replaceFirst("^jdbc:", "")); }
        catch (IllegalArgumentException e) { throw new IllegalStateException("Invalid isolated test database URL"); }
        if (!"postgresql".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort() == 5432 || uri.getPort() <= 0 || uri.getUserInfo() != null || uri.getQuery() != null
                || uri.getPath() == null || !uri.getPath().matches("/ft_test_[a-z0-9_]+"))
            throw new IllegalStateException("Tests require an isolated loopback PostgreSQL instance on a non-5432 port "
                    + "and an ft_test_ database. Run run-tests.ps1. No connection attempted.");
    }
}
