/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.beta.paths.yens;

import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphalgo.TestLog;
import org.neo4j.graphalgo.TestProgressLogger;
import org.neo4j.graphalgo.TestSupport;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.beta.paths.ImmutablePathResult;
import org.neo4j.graphalgo.beta.paths.PathResult;
import org.neo4j.graphalgo.beta.paths.yens.config.ImmutableShortestPathYensStreamConfig;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.mem.AllocationTracker;
import org.neo4j.graphalgo.extension.GdlExtension;
import org.neo4j.graphalgo.extension.GdlGraph;
import org.neo4j.graphalgo.extension.IdFunction;
import org.neo4j.graphalgo.extension.Inject;
import org.s1ck.gdl.GDLHandler;
import org.s1ck.gdl.model.Edge;
import org.s1ck.gdl.model.Vertex;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

@GdlExtension
class YensTest {

    static ImmutableShortestPathYensStreamConfig.Builder defaultSourceTargetConfigBuilder() {
        return ImmutableShortestPathYensStreamConfig.builder()
            .path(true)
            .concurrency(1);
    }

    static Stream<Arguments> expectedMemoryEstimation() {
        return Stream.of(
            Arguments.of(1_000, 33_064L),
            Arguments.of(1_000_000, 32_250_808L),
            Arguments.of(1_000_000_000, 32_254_883_720L)
        );
    }

    @ParameterizedTest
    @MethodSource("expectedMemoryEstimation")
    void shouldComputeMemoryEstimation(int nodeCount, long expectedBytes) {
        TestSupport.assertMemoryEstimation(
            () -> Yens.memoryEstimation(false),
            nodeCount,
            1,
            expectedBytes,
            expectedBytes
        );
    }

    // https://en.wikipedia.org/wiki/Yen%27s_algorithm#/media/File:Yen's_K-Shortest_Path_Algorithm,_K=3,_A_to_F.gif
    @GdlGraph
    private static final String DB_CYPHER =
        "CREATE" +
        "  (c:C {id: 0})" +
        ", (d:D {id: 1})" +
        ", (e:E {id: 2})" +
        ", (f:F {id: 3})" +
        ", (g:G {id: 4})" +
        ", (h:H {id: 5})" +
        ", (c)-[:REL {cost: 3.0}]->(d)" +
        ", (c)-[:REL {cost: 2.0}]->(e)" +
        ", (d)-[:REL {cost: 4.0}]->(f)" +
        ", (e)-[:REL {cost: 1.0}]->(d)" +
        ", (e)-[:REL {cost: 2.0}]->(f)" +
        ", (e)-[:REL {cost: 3.0}]->(g)" +
        ", (f)-[:REL {cost: 2.0}]->(g)" +
        ", (f)-[:REL {cost: 1.0}]->(h)" +
        ", (g)-[:REL {cost: 2.0}]->(h)";

    @Inject
    private Graph graph;

    @Inject
    private IdFunction idFunction;

    // Each input represents k paths that are expected to be returned by Yen's algorithm.
    // The first node in each path is the start node for the path search, the last node in
    // each path is the target node for each path search. The node property represents the
    // expected cost in the resulting path, the relationship property is the index of the
    // relationship that has been traversed.
    static Stream<List<String>> pathInput() {
        return Stream.of(
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})",
                "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})",
                "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})",
                "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 0}]->(g {cost: 6.0})-[{id: 0}]->(h {cost: 8.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})",
                "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 0}]->(g {cost: 6.0})-[{id: 0}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 0}]->(g {cost: 9.0})-[{id: 0}]->(h {cost: 11.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})",
                "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 0}]->(g {cost: 6.0})-[{id: 0}]->(h {cost: 8.0})",
                "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 0}]->(g {cost: 9.0})-[{id: 0}]->(h {cost: 11.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 0}]->(g {cost: 9.0})-[{id: 0}]->(h {cost: 11.0})"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pathInput")
    void compute(Collection<String> expectedPaths) {
        assertResult(graph, idFunction, expectedPaths, Optional.empty());
    }

    static Stream<List<String>> pathInputWithPathExpression() {
        return Stream.of(
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})"
            ),
            List.of(
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 1}]->(h {cost: 5.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})",
                "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 0}]->(g {cost: 6.0})-[{id: 0}]->(h {cost: 8.0})"
            )
        );
    }

    @ParameterizedTest
    @MethodSource("pathInputWithPathExpression")
    void computeWithPathExpression(Collection<String> expectedPaths) {
        assertResult(graph, idFunction, expectedPaths, Optional.of("^(C).*?(D)"));
    }

    static Stream<Arguments> unexpectedPathsForPathExpression() {
        return Stream.of(
            Arguments.of(
                "^(C).*?(D)",
                List.of(
                    "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                    "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 1}]->(h {cost: 8.0})",
                    "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 0}]->(g {cost: 9.0})-[{id: 0}]->(h {cost: 11.0})",
                    "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 0}]->(g {cost: 9.0})-[{id: 0}]->(h {cost: 11.0})"
                )
            ),
            Arguments.of(
                ".*(E)(F)(G)",
                List.of(
                    "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 0}]->(g {cost: 6.0})-[{id: 0}]->(h {cost: 8.0})"
                )
            ),
            Arguments.of(
                "^(C).*?(G)",
                List.of(
                    "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 2}]->(g {cost: 5.0})-[{id: 0}]->(h {cost: 7.0})",
                    "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 1}]->(f {cost: 4.0})-[{id: 0}]->(g {cost: 6.0})-[{id: 0}]->(h {cost: 8.0})",
                    "(c {cost: 0.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 0}]->(g {cost: 9.0})-[{id: 0}]->(h {cost: 11.0})",
                    "(c {cost: 0.0})-[{id: 1}]->(e {cost: 2.0})-[{id: 0}]->(d {cost: 3.0})-[{id: 0}]->(f {cost: 7.0})-[{id: 0}]->(g {cost: 9.0})-[{id: 0}]->(h {cost: 11.0})"
                )
            )
        );
    }

    @ParameterizedTest
    @MethodSource("unexpectedPathsForPathExpression")
    void negativeWithPathExpression(String pathExpression, Collection<String> unexpectedPaths) {
        assertResult(graph, idFunction, unexpectedPaths, Optional.of(pathExpression), Optional.of(42), false);
    }

    @Test
    void shouldLogProgress() {
        int k = 3;
        var testLogger = new TestProgressLogger(graph.relationshipCount(), "Yens", 1);

        var config = defaultSourceTargetConfigBuilder()
            .sourceNode(idFunction.of("c"))
            .targetNode(idFunction.of("h"))
            .k(k)
            .build();

        var ignored = Yens
            .sourceTarget(graph, config, testLogger, AllocationTracker.empty())
            .compute()
            .pathSet();

        assertEquals(8, testLogger.getProgresses().size());

        // once
        assertTrue(testLogger.containsMessage(TestLog.INFO, "Yens :: Start"));
        assertTrue(testLogger.containsMessage(TestLog.INFO, "Yens :: Finished"));
        // for each k
        for (int i = 1; i <= k; i++) {
            assertTrue(testLogger.containsMessage(
                TestLog.INFO,
                formatWithLocale("Yens :: Start searching path %d of %d", i, k)
            ));
            assertTrue(testLogger.containsMessage(
                TestLog.INFO,
                formatWithLocale("Yens :: Finished searching path %d of %d", i, k)
            ));

        }
        // multiple times within each k
        assertTrue(testLogger.containsMessage(TestLog.INFO, formatWithLocale("Yens :: Start Dijkstra for spur node")));
        assertTrue(testLogger.containsMessage(TestLog.INFO, formatWithLocale("Dijkstra :: Start")));
        assertTrue(testLogger.containsMessage(TestLog.INFO, formatWithLocale("Dijkstra :: Finished")));
    }

    private static void assertResult(
        Graph graph,
        IdFunction idFunction,
        Collection<String> expectedPaths,
        Optional<String> pathExpression
    ) {
        assertResult(graph, idFunction, expectedPaths, pathExpression, Optional.empty(), true);
    }

    private static void assertResult(
        Graph graph,
        IdFunction idFunction,
        Collection<String> expectedPaths,
        Optional<String> pathExpression,
        Optional<Integer> maybeK,
        boolean positiveTest
    ) {
        var expectedPathResults = expectedPathResults(idFunction, expectedPaths);

        var firstResult = expectedPathResults
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("At least one expected path must be provided"));

        if (!expectedPathResults
            .stream()
            .allMatch(p -> p.sourceNode() == firstResult.sourceNode() && p.targetNode() == firstResult.targetNode())) {
            throw new IllegalArgumentException("All expected paths must have the same source and target nodes.");
        }

        var config = defaultSourceTargetConfigBuilder()
            .sourceNode(firstResult.sourceNode())
            .targetNode(firstResult.targetNode())
            .k(maybeK.orElse(expectedPathResults.size()))
            .pathExpression(pathExpression.orElse(null))
            .build();

        var actualPathResults = Yens
            .sourceTarget(graph, config, ProgressLogger.NULL_LOGGER, AllocationTracker.empty())
            .compute()
            .pathSet();

        var assertThatPaths = assertThat(actualPathResults);
        if (positiveTest) {
            assertThatPaths.containsExactlyInAnyOrderElementsOf(expectedPathResults);
        } else {
            assertThatPaths.doesNotContainAnyElementsOf(expectedPathResults);
        }
    }

    @NotNull
    private static Set<PathResult> expectedPathResults(IdFunction idFunction, Collection<String> expectedPaths) {
        var index = new MutableInt(0);
        return expectedPaths.stream()
            .map(expectedPath -> new GDLHandler.Builder()
                .setNextVertexId(variable -> variable
                    .map(idFunction::of)
                    .orElseThrow(() -> new IllegalArgumentException("Path must not contain anonymous nodes.")))
                .buildFromString(expectedPath)
            )
            .map(gdl -> {
                var sourceNode = gdl.getVertices().stream()
                    .filter(v -> gdl.getEdges().stream().allMatch(e -> e.getTargetVertexId() != v.getId()))
                    .findFirst()
                    .orElseThrow();

                var targetNode = gdl.getVertices().stream()
                    .filter(v -> gdl.getEdges().stream().allMatch(e -> e.getSourceVertexId() != v.getId()))
                    .findFirst()
                    .orElseThrow();

                int nodeCount = gdl.getVertices().size();

                var nodeIds = new long[nodeCount];
                var relationshipIds = new long[nodeCount - 1];
                var costs = new double[nodeCount];

                var nextNode = sourceNode;
                var j = 0;
                while (nextNode != targetNode) {
                    var edge = getEdgeBySourceId(gdl.getEdges(), nextNode.getId());
                    nodeIds[j] = nextNode.getId();
                    relationshipIds[j] = (int) edge.getProperties().get("id");
                    costs[j] = (float) nextNode.getProperties().get("cost");
                    nextNode = getVertexById(gdl.getVertices(), edge.getTargetVertexId());
                    j += 1;
                }

                nodeIds[j] = nextNode.getId();
                costs[j] = (float) nextNode.getProperties().get("cost");

                return ImmutablePathResult.builder()
                    .index(index.getAndIncrement())
                    .sourceNode(sourceNode.getId())
                    .targetNode(targetNode.getId())
                    .nodeIds(nodeIds)
                    .relationshipIds(relationshipIds)
                    .costs(costs)
                    .build();
            })
            .collect(Collectors.toSet());
    }

    private static Vertex getVertexById(Collection<Vertex> vertices, long id) {
        return vertices.stream().filter(v -> v.getId() == id).findFirst().orElseThrow();
    }

    private static Edge getEdgeBySourceId(Collection<Edge> elements, long id) {
        return elements.stream().filter(e -> e.getSourceVertexId() == id).findFirst().orElseThrow();
    }

    @Nested
    @TestInstance(value = TestInstance.Lifecycle.PER_CLASS)
    class MultiGraph {

        @GdlGraph
        private static final String DB_CYPHER =
            "CREATE" +
            "  (a { id: 0 })" +
            ", (b { id: 1 })" +
            ", (c { id: 2 })" +
            ", (d { id: 3 })" +
            ", (a)-[:REL { cost: 1.0 }]->(b)" +
            ", (a)-[:REL { cost: 2.0 }]->(b)" +
            ", (b)-[:REL { cost: 3.0 }]->(c)" +
            ", (b)-[:REL { cost: 4.0 }]->(c)" +
            ", (c)-[:REL { cost: 42.0 }]->(d)" +
            ", (c)-[:REL { cost: 42.0 }]->(d)";

        @Inject
        private Graph graph;

        @Inject
        private IdFunction idFunction;

        Stream<List<String>> pathInput() {
            return Stream.of(
                List.of(
                    "(a {cost: 0.0})-[{id: 0}]->(b {cost: 1.0})"
                ),
                List.of(
                    "(a {cost: 0.0})-[{id: 0}]->(b {cost: 1.0})",
                    "(a {cost: 0.0})-[{id: 1}]->(b {cost: 2.0})"
                ),
                List.of(
                    "(a {cost: 0.0})-[{id: 0}]->(b {cost: 1.0})-[{id: 0}]->(c {cost: 4.0})",
                    "(a {cost: 0.0})-[{id: 1}]->(b {cost: 2.0})-[{id: 0}]->(c {cost: 5.0})",
                    "(a {cost: 0.0})-[{id: 0}]->(b {cost: 1.0})-[{id: 1}]->(c {cost: 5.0})"
                ),
                List.of(
                    "(a {cost: 0.0})-[{id: 0}]->(b {cost: 1.0})-[{id: 0}]->(c {cost: 4.0})-[{id: 0}]->(d {cost: 46.0})",
                    "(a {cost: 0.0})-[{id: 0}]->(b {cost: 1.0})-[{id: 0}]->(c {cost: 4.0})-[{id: 1}]->(d {cost: 46.0})"
                )
            );
        }

        @ParameterizedTest
        @MethodSource("pathInput")
        void compute(Collection<String> expectedPaths) {
            assertResult(graph, idFunction, expectedPaths, Optional.empty());
        }
    }
}
