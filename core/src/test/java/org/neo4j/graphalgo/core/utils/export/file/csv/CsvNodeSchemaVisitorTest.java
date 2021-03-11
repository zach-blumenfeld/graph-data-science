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
package org.neo4j.graphalgo.core.utils.export.file.csv;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.NodeLabel;
import org.neo4j.graphalgo.api.DefaultValue;
import org.neo4j.graphalgo.api.GraphStore;
import org.neo4j.graphalgo.api.nodeproperties.ValueType;

import java.io.IOException;
import java.util.List;

import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvNodeSchemaVisitor.DEFAULT_VALUE_COLUMN_NAME;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvNodeSchemaVisitor.LABEL_COLUMN_NAME;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvNodeSchemaVisitor.NODE_SCHEMA_FILE_NAME;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvNodeSchemaVisitor.PROPERTY_KEY_COLUMN_NAME;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvNodeSchemaVisitor.STATE_COLUMN_NAME;
import static org.neo4j.graphalgo.core.utils.export.file.csv.CsvNodeSchemaVisitor.VALUE_TYPE_COLUMN_NAME;

class CsvNodeSchemaVisitorTest extends CsvVisitorTest {

    public static final List<String> LABEL_SCHEMA_COLUMNS = List.of(
        LABEL_COLUMN_NAME,
        PROPERTY_KEY_COLUMN_NAME,
        VALUE_TYPE_COLUMN_NAME,
        DEFAULT_VALUE_COLUMN_NAME,
        STATE_COLUMN_NAME
    );

    @Test
    void writesVisitedNodeSchema() throws IOException {
        var nodeSchemaVisitor = new CsvNodeSchemaVisitor(tempDir);
        NodeLabel labelA = NodeLabel.of("A");
        nodeSchemaVisitor.nodeLabel(labelA);
        nodeSchemaVisitor.key("prop1");
        nodeSchemaVisitor.valueType(ValueType.LONG);
        nodeSchemaVisitor.defaultValue(DefaultValue.of(42L));
        nodeSchemaVisitor.state(GraphStore.PropertyState.PERSISTENT);
        nodeSchemaVisitor.endOfEntity();

        NodeLabel labelB = NodeLabel.of("B");
        nodeSchemaVisitor.nodeLabel(labelB);
        nodeSchemaVisitor.key("prop2");
        nodeSchemaVisitor.valueType(ValueType.DOUBLE);
        nodeSchemaVisitor.defaultValue(DefaultValue.of(13.37D));
        nodeSchemaVisitor.state(GraphStore.PropertyState.TRANSIENT);
        nodeSchemaVisitor.endOfEntity();

        nodeSchemaVisitor.close();
        assertCsvFiles(List.of(NODE_SCHEMA_FILE_NAME));
        assertDataContent(
            NODE_SCHEMA_FILE_NAME,
            List.of(
                defaultHeaderColumns(),
                List.of("A", "prop1", "long", "DefaultValue(42)", "PERSISTENT"),
                List.of("B", "prop2", "double", "DefaultValue(13.37)", "TRANSIENT")
            )
        );
    }

    @Override
    protected List<String> defaultHeaderColumns() {
        return LABEL_SCHEMA_COLUMNS;
    }
}
