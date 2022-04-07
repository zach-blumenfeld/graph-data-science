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
package org.neo4j.gds.ml.pipeline;

import org.neo4j.gds.ml.metrics.BestMetricData;
import org.neo4j.gds.ml.metrics.BestModelStats;
import org.neo4j.gds.ml.metrics.Metric;
import org.neo4j.gds.ml.metrics.ModelStats;
import org.neo4j.gds.ml.models.TrainerConfig;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class BestMetricsProducer {

    private BestMetricsProducer() {}

    public static Map<Metric, BestMetricData> computeBestMetrics(
        ModelSelectResult modelSelectResult,
        Map<? extends Metric, Double> outerTrainMetrics,
        Map<? extends Metric, Double> testMetrics
    ) {
        var metrics = modelSelectResult.validationStats().keySet();

        return metrics.stream().collect(Collectors.toMap(
            Function.identity(),
            metric -> BestMetricData.of(
                findBestModelStats(modelSelectResult.trainStats().get(metric), modelSelectResult.bestParameters()),
                findBestModelStats(modelSelectResult.validationStats().get(metric), modelSelectResult.bestParameters()),
                outerTrainMetrics.get(metric),
                testMetrics.get(metric)
            )
        ));
    }

    private static BestModelStats findBestModelStats(
        List<ModelStats> metricStatsForModels,
        TrainerConfig bestParams
    ) {
        return metricStatsForModels.stream()
            .filter(metricStatsForModel -> metricStatsForModel.params() == bestParams)
            .findFirst()
            .map(BestModelStats::of)
            .orElseThrow();
    }
}
