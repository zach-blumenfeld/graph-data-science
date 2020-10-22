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
package org.neo4j.graphalgo.core.utils.la;

import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.mult.MatrixMatrixMult_DDRM;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EjmlUtilTest {
    @Test
    void multTransBWithMask() {
        var matrix = DMatrixRMaj.wrap(3, 3, new double[]{1,2,3,4,5,6,7,8,9});
        var maskedResult = new DMatrixRMaj(3,3);
        IntPredicate mask = (index) -> (index >= 4);
        EjmlUtil.multTransB(matrix, matrix, maskedResult, mask);

        var originalResult = maskedResult.createLike();
        MatrixMatrixMult_DDRM.multTransB(matrix, matrix, originalResult);

        for (int index = 0; index < originalResult.data.length; index++) {
            if (mask.test(index)) {
                assertEquals(originalResult.get(index), maskedResult.get(index));
            } else {
                assertEquals(0, maskedResult.get(index));
            }
        }
    }

}
