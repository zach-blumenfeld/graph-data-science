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
package org.neo4j.gds.paths.sourcetarget;

import org.neo4j.gds.AlgorithmFactory;
import org.neo4j.gds.core.CypherMapWrapper;
import org.neo4j.gds.paths.ShortestPathWriteProc;
import org.neo4j.gds.paths.yens.Yens;
import org.neo4j.gds.paths.yens.YensFactory;
import org.neo4j.gds.paths.yens.config.ShortestPathYensWriteConfig;
import org.neo4j.gds.results.MemoryEstimateResult;
import org.neo4j.gds.results.StandardWriteRelationshipsResult;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.neo4j.gds.paths.sourcetarget.ShortestPathYensProc.YENS_DESCRIPTION;
import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.WRITE;

public class ShortestPathYensWriteProc extends ShortestPathWriteProc<Yens, ShortestPathYensWriteConfig> {

    @Procedure(name = "gds.shortestPath.yens.write", mode = WRITE)
    @Description(YENS_DESCRIPTION)
    public Stream<StandardWriteRelationshipsResult> write(
        @Name(value = "graphName") String graphName,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return write(compute(graphName, configuration, false, true));
    }

    @Procedure(name = "gds.shortestPath.yens.write.estimate", mode = READ)
    @Description(ESTIMATE_DESCRIPTION)
    public Stream<MemoryEstimateResult> writeEstimate(
        @Name(value = "graphName") Object graphNameOrConfig,
        @Name(value = "configuration", defaultValue = "{}") Map<String, Object> configuration
    ) {
        return computeEstimate(graphNameOrConfig, configuration);
    }

    @Override
    protected ShortestPathYensWriteConfig newConfig(
        String username,
        Optional<String> graphName,
        CypherMapWrapper config
    ) {
        return ShortestPathYensWriteConfig.of(graphName, config);
    }

    @Override
    protected AlgorithmFactory<Yens, ShortestPathYensWriteConfig> algorithmFactory() {
        return new YensFactory<>();
    }
}
