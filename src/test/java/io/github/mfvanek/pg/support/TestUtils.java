/*
 * Copyright (c) 2019-2022. Ivan Vakhrushev and others.
 * https://github.com/mfvanek/pg-index-health
 *
 * This file is a part of "pg-index-health" - a Java library for
 * analyzing and maintaining indexes health in PostgreSQL databases.
 *
 * Licensed under the Apache License 2.0
 */

package io.github.mfvanek.pg.support;

import io.github.mfvanek.pg.utils.PgSqlException;
import org.assertj.core.api.Assertions;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.sql.DataSource;

public final class TestUtils {

    private TestUtils() {
        throw new UnsupportedOperationException();
    }

    @SuppressWarnings("checkstyle:IllegalThrows")
    public static <T> void invokePrivateConstructor(@Nonnull final Class<T> type)
            throws Throwable {
        final Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }
    }

    public static void executeOnDatabase(@Nonnull final DataSource dataSource,
                                         @Nonnull final DbCallback callback) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            callback.execute(statement);
        } catch (SQLException e) {
            throw new PgSqlException(e);
        }
    }

    public static void executeInTransaction(@Nonnull final DataSource dataSource,
                                            @Nonnull final DbCallback callback) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            callback.execute(statement);
            connection.commit();
        } catch (SQLException e) {
            throw new PgSqlException(e);
        }
    }

    public static void waitForStatisticsCollector() {
        IntStream.of(1, 2, 3, 4, 5, 6).forEach(i -> {
            try {
                // see PGSTAT_STAT_INTERVAL at https://github.com/postgres/postgres/blob/master/src/backend/postmaster/pgstat.c
                Thread.sleep(500L); //NOSONAR
            } catch (InterruptedException e) {
                Assertions.fail("unknown failure", e);
            }
        });
    }
}
