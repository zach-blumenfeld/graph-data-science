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
package org.neo4j.gds.api.schema;

import org.neo4j.gds.Orientation;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.api.nodeproperties.ValueType;
import org.neo4j.gds.core.Aggregation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class RelationshipSchema extends ElementSchema<RelationshipSchema, RelationshipType, RelationshipSchemaEntry, RelationshipPropertySchema> {

    public static RelationshipSchema empty() {
        return new RelationshipSchema(new LinkedHashMap<>());
    }

    public RelationshipSchema(Map<RelationshipType, RelationshipSchemaEntry> entries) {
        super(entries);
    }

    @Override
    public RelationshipSchema filter(Set<RelationshipType> relationshipTypesToKeep) {
        return new RelationshipSchema(filterByElementIdentifier(relationshipTypesToKeep));
    }

    @Override
    public RelationshipSchema union(RelationshipSchema other) {
        return new RelationshipSchema(unionEntries(other));
    }

    public Set<RelationshipType> availableTypes() {
        return entries.keySet();
    }

    public boolean isUndirected() {
        // a graph with no relationships is considered undirected
        // this is because algorithms such as TriangleCount are still well-defined
        // so it is the least restrictive decision
        return entries.values().stream().allMatch(RelationshipSchemaEntry::isUndirected);
    }

    public RelationshipSchemaEntry getOrCreateRelationshipType(
        RelationshipType relationshipType,
        Orientation orientation
    ) {
        return this.entries.computeIfAbsent(relationshipType, (__) -> new RelationshipSchemaEntry(relationshipType, orientation));
    }

    public RelationshipSchema addRelationshipType(RelationshipType relationshipType, Orientation orientation) {
        getOrCreateRelationshipType(relationshipType, orientation);
        return this;
    }

    public RelationshipSchema addProperty(
        RelationshipType relationshipType,
        Orientation orientation,
        String propertyKey,
        RelationshipPropertySchema propertySchema
    ) {
        getOrCreateRelationshipType(relationshipType, orientation).addProperty(propertyKey, propertySchema);
        return this;
    }

    public RelationshipSchema addProperty(
        RelationshipType relationshipType,
        Orientation orientation,
        String propertyKey,
        ValueType valueType
    ) {
        getOrCreateRelationshipType(relationshipType, orientation).addProperty(propertyKey, valueType);
        return this;
    }

    public RelationshipSchema addProperty(
        RelationshipType relationshipType,
        Orientation orientation,
        String propertyKey,
        ValueType valueType,
        Aggregation aggregation
    ) {
        getOrCreateRelationshipType(relationshipType, orientation).addProperty(propertyKey, valueType, aggregation);
        return this;
    }

    Object toMapOld() {
        return entries()
            .stream()
            .collect(Collectors.toMap(
                e -> e.identifier().name(),
                e -> e.properties()
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        innerEntry -> GraphSchema.forPropertySchema(innerEntry.getValue()))
                    )
            ));
    }
}
