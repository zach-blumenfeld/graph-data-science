/*
 * Copyright (c) 2017-2021 "Neo4j,"
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
package org.neo4j.graphalgo.beta.paths.sourcetarget;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.AlgoBaseProc;
import org.neo4j.graphalgo.GdsCypher;
import org.neo4j.graphalgo.beta.paths.PathFactory;
import org.neo4j.graphalgo.beta.paths.astar.AStar;
import org.neo4j.graphalgo.beta.paths.astar.config.ShortestPathAStarStreamConfig;
import org.neo4j.graphalgo.beta.paths.dijkstra.DijkstraResult;
import org.neo4j.graphalgo.compat.GraphDatabaseApiProxy;
import org.neo4j.graphalgo.core.CypherMapWrapper;
import org.neo4j.graphdb.RelationshipType;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.neo4j.graphalgo.beta.paths.StreamResult.COST_PROPERTY_NAME;
import static org.neo4j.graphalgo.beta.paths.astar.config.ShortestPathAStarBaseConfig.LATITUDE_PROPERTY_KEY;
import static org.neo4j.graphalgo.beta.paths.astar.config.ShortestPathAStarBaseConfig.LONGITUDE_PROPERTY_KEY;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

class ShortestPathAStarStreamProcTest extends ShortestPathAStarProcTest<ShortestPathAStarStreamConfig> {

    @Override
    public Class<? extends AlgoBaseProc<AStar, DijkstraResult, ShortestPathAStarStreamConfig>> getProcedureClazz() {
        return ShortestPathAStarStreamProc.class;
    }

    @Override
    public ShortestPathAStarStreamConfig createConfig(CypherMapWrapper mapWrapper) {
        return ShortestPathAStarStreamConfig.of("", Optional.empty(), Optional.empty(), mapWrapper);
    }

    @Test
    void testStream() {
        var config = createConfig(createMinimalConfig(CypherMapWrapper.empty()));

        var query = GdsCypher.call().explicitCreation("graph")
            .algo("gds.beta.shortestPath.astar")
            .streamMode()
            .addParameter("sourceNode", config.sourceNode())
            .addParameter("targetNode", config.targetNode())
            .addParameter(LATITUDE_PROPERTY_KEY, config.latitudeProperty())
            .addParameter(LONGITUDE_PROPERTY_KEY, config.longitudeProperty())
            .addParameter("relationshipWeightProperty", "cost")
            .addParameter("path", true)
            .yields();

        GraphDatabaseApiProxy.runInTransaction(db, tx -> {
            var expectedPath = PathFactory.create(
                tx,
                -1,
                ids0,
                costs0,
                RelationshipType.withName(formatWithLocale("PATH_0")), COST_PROPERTY_NAME
            );
            var expected = Map.of(
                "index", 0L,
                "sourceNode", idA,
                "targetNode", idX,
                "totalCost", 2979.0D,
                "costs", Arrays.stream(costs0).boxed().collect(Collectors.toList()),
                "nodeIds", Arrays.stream(ids0).boxed().collect(Collectors.toList()),
                "path", expectedPath
            );

            assertCypherResult(query, List.of(expected));
        });
    }
}
