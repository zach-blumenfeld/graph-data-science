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
package org.neo4j.gds.pipeline;

import org.neo4j.gds.Algorithm;
import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.AlgorithmMetaData;
import org.neo4j.gds.GraphAlgorithmFactory;
import org.neo4j.gds.GraphStoreAlgorithmFactory;
import org.neo4j.gds.ImmutableComputationResult;
import org.neo4j.gds.api.Graph;
import org.neo4j.gds.api.GraphStore;
import org.neo4j.gds.config.AlgoBaseConfig;
import org.neo4j.gds.core.utils.ProgressTimer;
import org.neo4j.gds.core.utils.TerminationFlag;
import org.neo4j.gds.core.utils.mem.AllocationTracker;

import java.util.Map;
import java.util.function.Supplier;

public class ProcedureExecutor<
    ALGO extends Algorithm<ALGO, ALGO_RESULT>,
    ALGO_RESULT,
    CONFIG extends AlgoBaseConfig,
    RESULT
> {

    private final AlgorithmSpec<ALGO, ALGO_RESULT, CONFIG, RESULT, ?> algoSpec;
    private final PipelineSpec<ALGO, ALGO_RESULT, CONFIG> pipelineSpec;
    private final ExecutionContext executionContext;

    public ProcedureExecutor(
        AlgorithmSpec<ALGO, ALGO_RESULT, CONFIG, RESULT, ?> algoSpec,
        PipelineSpec<ALGO, ALGO_RESULT, CONFIG> pipelineSpec,
        ExecutionContext executionContext
    ) {
        this.algoSpec = algoSpec;
        this.pipelineSpec = pipelineSpec;

        this.executionContext = executionContext;
    }

    public RESULT compute(
        String graphName,
        Map<String, Object> configuration,
        boolean releaseAlgorithm,
        boolean releaseTopology
    ) {
        ImmutableComputationResult.Builder<ALGO, ALGO_RESULT, CONFIG> builder = ImmutableComputationResult.builder();

        CONFIG config = pipelineSpec.configParser(algoSpec.newConfigFunction(), executionContext).processInput(configuration);

        setAlgorithmMetaDataToTransaction(config);

        var graphCreation = pipelineSpec.graphCreationFactory(executionContext).create(config, graphName);

        var memoryEstimationInBytes = graphCreation.validateMemoryEstimation(algoSpec.algorithmFactory());

        GraphStore graphStore;
        Graph graph;

        try (ProgressTimer timer = ProgressTimer.start(builder::createMillis)) {
            var graphCreateConfig = graphCreation.graphCreateConfig();
            var validator = pipelineSpec.validator(algoSpec.validationConfig());
            validator.validateConfigsBeforeLoad(graphCreateConfig, config);
            graphStore = graphCreation.graphStore();
            validator.validateConfigWithGraphStore(graphStore, graphCreateConfig, config);
            graph = graphCreation.createGraph(graphStore);
        }

        if (graph.isEmpty()) {
            var emptyComputationResult = builder
                .isGraphEmpty(true)
                .graph(graph)
                .graphStore(graphStore)
                .config(config)
                .computeMillis(0)
                .result(null)
                .algorithm(null)
                .build();
            return algoSpec.computationResultConsumer().consume(emptyComputationResult, executionContext);
        }

        ALGO algo = newAlgorithm(graph, graphStore, config, executionContext.allocationTracker());

        algo.getProgressTracker().setEstimatedResourceFootprint(memoryEstimationInBytes, config.concurrency());

        ALGO_RESULT result = executeAlgorithm(releaseAlgorithm, releaseTopology, builder, graph, algo);

        executionContext.log().info(algoSpec.name() + ": overall memory usage %s", executionContext.allocationTracker().getUsageString());

        var computationResult = builder
            .graph(graph)
            .graphStore(graphStore)
            .algorithm(algo)
            .result(result)
            .config(config)
            .build();

        return algoSpec.computationResultConsumer().consume(computationResult, executionContext);
    }

    private ALGO_RESULT executeAlgorithm(
        boolean releaseAlgorithm,
        boolean releaseTopology,
        ImmutableComputationResult.Builder<ALGO, ALGO_RESULT, CONFIG> builder,
        Graph graph,
        ALGO algo
    ) {
        return runWithExceptionLogging(
            "Computation failed",
            () -> {
                try (ProgressTimer ignored = ProgressTimer.start(builder::computeMillis)) {
                    return algo.compute();
                } catch (Throwable e) {
                    algo.getProgressTracker().endSubTaskWithFailure();
                    throw e;
                } finally {
                    if (releaseAlgorithm) {
                        algo.getProgressTracker().release();
                        algo.release();
                    }
                    if (releaseTopology) {
                        graph.releaseTopology();
                    }
                }
            }
        );
    }

    private ALGO newAlgorithm(
        Graph graph,
        GraphStore graphStore,
        CONFIG config,
        AllocationTracker allocationTracker
    ) {
        TerminationFlag terminationFlag = TerminationFlag.wrap(executionContext.transaction());
        return algoSpec.algorithmFactory()
            .accept(new AlgorithmFactory.Visitor<>() {
                @Override
                public ALGO graph(GraphAlgorithmFactory<ALGO, CONFIG> graphAlgorithmFactory) {
                    return graphAlgorithmFactory.build(graph, config, allocationTracker, executionContext.log(), executionContext.taskRegistryFactory());
                }

                @Override
                public ALGO graphStore(GraphStoreAlgorithmFactory<ALGO, CONFIG> graphStoreAlgorithmFactory) {
                    return graphStoreAlgorithmFactory.build(graphStore, config, allocationTracker, executionContext.log(), executionContext.taskRegistryFactory());
                }
            })
            .withTerminationFlag(terminationFlag);
    }

    private void setAlgorithmMetaDataToTransaction(CONFIG algoConfig) {
        if (executionContext.transaction() == null) {
            return;
        }
        var metaData = executionContext.transaction().getMetaData();
        if (metaData instanceof AlgorithmMetaData) {
            ((AlgorithmMetaData) metaData).set(algoConfig);
        }
    }

    private <R> R runWithExceptionLogging(String message, Supplier<R> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            executionContext.log().warn(message, e);
            throw e;
        }
    }
}
