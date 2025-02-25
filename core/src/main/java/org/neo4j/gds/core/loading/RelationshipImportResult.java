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
package org.neo4j.gds.core.loading;

import org.neo4j.gds.PropertyMappings;
import org.neo4j.gds.RelationshipProjection;
import org.neo4j.gds.RelationshipType;
import org.neo4j.gds.annotation.ValueClass;
import org.neo4j.gds.api.AdjacencyProperties;
import org.neo4j.gds.api.ImmutableProperties;
import org.neo4j.gds.api.ImmutableTopology;
import org.neo4j.gds.api.PropertyState;
import org.neo4j.gds.api.RelationshipProperty;
import org.neo4j.gds.api.RelationshipPropertyStore;
import org.neo4j.gds.api.Relationships;
import org.neo4j.gds.api.ValueTypes;
import org.neo4j.gds.api.schema.Direction;
import org.neo4j.gds.core.loading.construction.RelationshipsAndDirection;
import org.neo4j.values.storable.NumberType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ValueClass
public interface RelationshipImportResult {

    Map<RelationshipType, SingleTypeRelationshipImportResult> importResults();

    static ImmutableRelationshipImportResult.Builder builder() {
        return ImmutableRelationshipImportResult.builder();
    }

    static RelationshipImportResult of(
        Map<RelationshipType, Relationships.Topology> topologies,
        Map<RelationshipType, RelationshipPropertyStore> properties,
        Map<RelationshipType, Direction> directions
    ) {
        var relationshipImportResultBuilder = RelationshipImportResult.builder();

        topologies.forEach((relationshipType, topology) -> relationshipImportResultBuilder.putImportResult(
            relationshipType,
            SingleTypeRelationshipImportResult.builder()
                .topology(topology)
                .properties(Optional.ofNullable(properties.get(relationshipType)))
                .direction(directions.get(relationshipType))
                .build()
        ));

        return relationshipImportResultBuilder.build();
    }

    static RelationshipImportResult of(Map<RelationshipTypeAndProjection, List<RelationshipsAndDirection>> relationshipsByType) {
        var relationshipImportResultBuilder = RelationshipImportResult.builder();

        relationshipsByType.forEach(((relationshipTypeAndProjection, relationshipsAndDirections) -> {
            var relationshipType = relationshipTypeAndProjection.relationshipType();
            var direction = Direction.fromOrientation(relationshipTypeAndProjection
                .relationshipProjection()
                .orientation());

            var topology = relationshipsAndDirections.get(0).relationships().topology();

            var properties = relationshipsAndDirections
                .stream()
                .map(RelationshipsAndDirection::relationships)
                .map(Relationships::properties)
                .map(props -> props.map(Relationships.Properties::propertiesList))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

            var propertyStore = properties.isEmpty()
                ? Optional.<RelationshipPropertyStore>empty()
                : Optional.of(constructRelationshipPropertyStore(
                    relationshipTypeAndProjection.relationshipProjection(),
                    properties,
                    topology.elementCount()
                ));

            relationshipImportResultBuilder.putImportResult(
                relationshipType,
                SingleTypeRelationshipImportResult.builder()
                    .topology(topology)
                    .properties(propertyStore)
                    .direction(direction)
                    .build()
            );
        }));

        return relationshipImportResultBuilder.build();
    }

    /**
     * This method creates the final {@link RelationshipImportResult} in preparation
     * for the {@link org.neo4j.gds.api.GraphStore}.
     * <p>
     * The method is used in the context of native projection, where each projected relationship type (and its
     * properties) is represented by a {@link org.neo4j.gds.core.loading.SingleTypeRelationshipImporter.SingleTypeRelationshipImportContext}.
     *
     * @param importContexts each import context maps to a relationship type being created
     * @return a wrapper type ready to be consumed by a {@link org.neo4j.gds.api.GraphStore}
     */
    static RelationshipImportResult of(Collection<SingleTypeRelationshipImporter.SingleTypeRelationshipImportContext> importContexts) {
        var builders = new HashMap<RelationshipType, ImmutableSingleTypeRelationshipImportResult.Builder>(importContexts.size());

        importContexts.forEach((importContext) -> {
            var adjacencyListsWithProperties = importContext.singleTypeRelationshipImporter().build();
            var isInverseRelationship = importContext.inverseOfRelationshipType().isPresent();

            var direction = Direction.fromOrientation(importContext.relationshipProjection().orientation());

            var topology = ImmutableTopology.builder()
                .adjacencyList(adjacencyListsWithProperties.adjacency())
                .elementCount(adjacencyListsWithProperties.relationshipCount())
                .isMultiGraph(importContext.relationshipProjection().isMultiGraph())
                .build();

            var properties = (importContext.relationshipProjection().properties().isEmpty())
                ? Optional.<RelationshipPropertyStore>empty()
                : Optional.of(constructRelationshipPropertyStore(
                    importContext.relationshipProjection(),
                    adjacencyListsWithProperties.properties(),
                    adjacencyListsWithProperties.relationshipCount()
                ));

            var importResultBuilder = builders.computeIfAbsent(
                importContext.relationshipType(),
                relationshipType -> SingleTypeRelationshipImportResult.builder().direction(direction)
            );

            if (isInverseRelationship) {
                importResultBuilder.inverseTopology(topology).inverseProperties(properties);
            } else {
                importResultBuilder.topology(topology).properties(properties);
            }
        });

        var importResults = builders.entrySet().stream().collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().build()
            ));

        return ImmutableRelationshipImportResult.builder()
            .importResults(importResults)
            .build();
    }

    private static RelationshipPropertyStore constructRelationshipPropertyStore(
        RelationshipProjection projection,
        Iterable<AdjacencyProperties> properties,
        long relationshipCount
    ) {
        PropertyMappings propertyMappings = projection.properties();
        RelationshipPropertyStore.Builder propertyStoreBuilder = RelationshipPropertyStore.builder();

        var propertiesIter = properties.iterator();
        propertyMappings.mappings().forEach(propertyMapping -> {
            var propertiesList = propertiesIter.next();
            propertyStoreBuilder.putIfAbsent(
                propertyMapping.propertyKey(),
                RelationshipProperty.of(
                    propertyMapping.propertyKey(),
                    NumberType.FLOATING_POINT,
                    PropertyState.PERSISTENT,
                    ImmutableProperties.of(
                        propertiesList,
                        relationshipCount,
                        // This is fine because relationships currently only support doubles
                        propertyMapping.defaultValue().doubleValue()
                    ),
                    propertyMapping.defaultValue().isUserDefined()
                        ? propertyMapping.defaultValue()
                        : ValueTypes.fromNumberType(NumberType.FLOATING_POINT).fallbackValue(),
                    propertyMapping.aggregation()
                )
            );
        });

        return propertyStoreBuilder.build();
    }

    @ValueClass
    interface RelationshipTypeAndProjection {
        RelationshipType relationshipType();

        RelationshipProjection relationshipProjection();

        static RelationshipTypeAndProjection of(
            RelationshipType relationshipType,
            RelationshipProjection relationshipProjection
        ) {
            return ImmutableRelationshipTypeAndProjection.of(relationshipType, relationshipProjection);
        }
    }

}
