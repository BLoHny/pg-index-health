/*
 * Copyright (c) 2019-2023. Ivan Vakhrushev and others.
 * https://github.com/mfvanek/pg-index-health
 *
 * This file is a part of "pg-index-health" - a Java library for
 * analyzing and maintaining indexes health in PostgreSQL databases.
 *
 * Licensed under the Apache License 2.0
 */

package io.github.mfvanek.pg.support;

import javax.annotation.Nonnull;

@FunctionalInterface
public interface DatabaseConfigurer {

    @Nonnull
    DatabasePopulator configure(@Nonnull DatabasePopulator databasePopulator);
}