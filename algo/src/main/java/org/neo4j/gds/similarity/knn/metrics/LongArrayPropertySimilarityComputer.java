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
package org.neo4j.gds.similarity.knn.metrics;

import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.api.properties.nodes.LongArrayNodePropertyValues;
import org.neo4j.gds.api.properties.nodes.NodePropertyValues;
import org.neo4j.gds.core.utils.paged.HugeObjectArray;

import java.util.Arrays;

final class LongArrayPropertySimilarityComputer implements SimilarityComputer {
    private final NodePropertyValues nodePropertyValues;
    private final LongArraySimilarityMetric metric;

    LongArrayPropertySimilarityComputer(NodePropertyValues nodePropertyValues, LongArraySimilarityMetric metric) {
        if (nodePropertyValues.valueType() != ValueType.LONG_ARRAY) {
            throw new IllegalArgumentException("The property is not of type LONG_ARRAY");
        }
        this.nodePropertyValues = nodePropertyValues;
        this.metric = metric;
    }

    @Override
    public double similarity(long firstNodeId, long secondNodeId) {
        var left = nodePropertyValues.longArrayValue(firstNodeId);
        var right = nodePropertyValues.longArrayValue(secondNodeId);
        return metric.compute(left, right);
    }

    @Override
    public boolean isSymmetric() {
        return metric.isSymmetric();
    }

    static final class SortedLongArrayPropertyValues implements LongArrayNodePropertyValues {

        private final HugeObjectArray<long[]> properties;

        SortedLongArrayPropertyValues(NodePropertyValues nodePropertyValues) {
            this.properties = HugeObjectArray.newArray(long[].class, nodePropertyValues.size());
            this.properties.setAll(i -> {
                var value = nodePropertyValues.longArrayValue(i).clone();
                Arrays.parallelSort(value);
                return value;
            });
        }

        @Override
        public long size() {
            return properties.size();
        }

        @Override
        public long[] longArrayValue(long nodeId) {
            return properties.get(nodeId);
        }
    }
}
