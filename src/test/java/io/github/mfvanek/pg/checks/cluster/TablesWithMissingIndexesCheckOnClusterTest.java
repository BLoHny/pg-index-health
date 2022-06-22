/*
 * Copyright (c) 2019-2022. Ivan Vakhrushev and others.
 * https://github.com/mfvanek/pg-index-health
 *
 * This file is a part of "pg-index-health" - a Java library for
 * analyzing and maintaining indexes health in PostgreSQL databases.
 *
 * Licensed under the Apache License 2.0
 */

package io.github.mfvanek.pg.checks.cluster;

import io.github.mfvanek.pg.checks.predicates.FilterTablesByNamePredicate;
import io.github.mfvanek.pg.checks.predicates.FilterTablesBySizePredicate;
import io.github.mfvanek.pg.common.maintenance.DatabaseCheckOnCluster;
import io.github.mfvanek.pg.common.maintenance.Diagnostic;
import io.github.mfvanek.pg.connection.HighAvailabilityPgConnectionImpl;
import io.github.mfvanek.pg.connection.PgConnectionImpl;
import io.github.mfvanek.pg.embedded.PostgresDbExtension;
import io.github.mfvanek.pg.embedded.PostgresExtensionFactory;
import io.github.mfvanek.pg.model.table.TableWithMissingIndex;
import io.github.mfvanek.pg.utils.DatabaseAwareTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TablesWithMissingIndexesCheckOnClusterTest extends DatabaseAwareTestBase {

    @RegisterExtension
    static final PostgresDbExtension POSTGRES = PostgresExtensionFactory.database();

    private final DatabaseCheckOnCluster<TableWithMissingIndex> check;

    TablesWithMissingIndexesCheckOnClusterTest() {
        super(POSTGRES.getTestDatabase());
        this.check = new TablesWithMissingIndexesCheckOnCluster(
                HighAvailabilityPgConnectionImpl.of(PgConnectionImpl.ofPrimary(POSTGRES.getTestDatabase())));
    }

    @Test
    void shouldSatisfyContract() {
        assertThat(check.getType()).isEqualTo(TableWithMissingIndex.class);
        assertThat(check.getDiagnostic()).isEqualTo(Diagnostic.TABLES_WITH_MISSING_INDEXES);
    }

    @Test
    void onEmptyDatabase() {
        assertThat(check.check())
                .isNotNull()
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "custom"})
    void onDatabaseWithoutThem(final String schemaName) {
        executeTestOnDatabase(schemaName, dbp -> dbp.withReferences().withData(), ctx ->
                assertThat(check.check(ctx))
                        .isNotNull()
                        .isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "custom"})
    void onDatabaseWithThem(final String schemaName) {
        executeTestOnDatabase(schemaName, dbp -> dbp.withReferences().withData(), ctx -> {
            tryToFindAccountByClientId(schemaName);
            assertThat(check.check(ctx))
                    .isNotNull()
                    .hasSize(1)
                    .containsExactly(
                            TableWithMissingIndex.of(ctx.enrichWithSchema("accounts"), 0L, 0L, 0L))
                    .allMatch(t -> t.getSeqScans() >= AMOUNT_OF_TRIES)
                    .allMatch(t -> t.getIndexScans() == 0)
                    .allMatch(t -> t.getTableSizeInBytes() > 1L);

            assertThat(check.check(ctx, FilterTablesByNamePredicate.of(ctx.enrichWithSchema("accounts"))))
                    .isNotNull()
                    .isEmpty();

            assertThat(check.check(ctx, FilterTablesBySizePredicate.of(1L)))
                    .isNotNull()
                    .hasSize(1)
                    .containsExactly(
                            TableWithMissingIndex.of(ctx.enrichWithSchema("accounts"), 0L, 0L, 0L))
                    .allMatch(t -> t.getSeqScans() >= AMOUNT_OF_TRIES)
                    .allMatch(t -> t.getIndexScans() == 0)
                    .allMatch(t -> t.getTableSizeInBytes() > 1L);

            assertThat(check.check(ctx, FilterTablesBySizePredicate.of(1_000_000L)))
                    .isNotNull()
                    .isEmpty();
        });
    }

    @Test
    void getResultAsUnion() {
        final TableWithMissingIndex t1 = TableWithMissingIndex.of("t1", 1L, 10L, 1L);
        final TableWithMissingIndex t2 = TableWithMissingIndex.of("t2", 2L, 30L, 3L);
        final TableWithMissingIndex t3 = TableWithMissingIndex.of("t3", 3L, 40L, 4L);
        final List<List<TableWithMissingIndex>> tablesWithMissingIndexesFromAllHosts = Arrays.asList(
                Collections.emptyList(),
                Arrays.asList(t1, t3),
                Collections.singletonList(t2),
                Arrays.asList(t2, t3)
        );
        final List<TableWithMissingIndex> tablesWithMissingIndexes = TablesWithMissingIndexesCheckOnCluster.getResultAsUnion(
                tablesWithMissingIndexesFromAllHosts);
        assertThat(tablesWithMissingIndexes)
                .hasSize(3)
                .containsExactlyInAnyOrder(t1, t2, t3);
    }
}
