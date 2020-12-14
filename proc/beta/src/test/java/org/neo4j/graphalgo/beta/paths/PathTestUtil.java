/*
 * Copyright (c) 2017-2020 "Neo4j,"
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
package org.neo4j.graphalgo.beta.paths;

import static org.neo4j.graphalgo.beta.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.COSTS_KEY;
import static org.neo4j.graphalgo.beta.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.NODE_IDS_KEY;
import static org.neo4j.graphalgo.beta.paths.dijkstra.config.ShortestPathDijkstraWriteConfig.TOTAL_COST_KEY;
import static org.neo4j.graphalgo.utils.StringFormatting.formatWithLocale;

public final class PathTestUtil {

    public static final String WRITE_RELATIONSHIP_TYPE = "PATH";

    public static String validationQuery(long sourceId) {
        return formatWithLocale(
            "MATCH (a)-[r:%s]->() " +
            "WHERE id(a) = %d " +
            "RETURN r.%s AS totalCost, r.%s AS nodeIds, r.%s AS costs " +
            "ORDER BY totalCost ASC",
            WRITE_RELATIONSHIP_TYPE,
            sourceId,
            TOTAL_COST_KEY,
            NODE_IDS_KEY,
            COSTS_KEY
        );
    }

    private PathTestUtil() {}

}
