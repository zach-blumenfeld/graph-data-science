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
package org.neo4j.gds.core.utils.progress;

import org.neo4j.gds.core.utils.progress.tasks.Task;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;

import java.util.Queue;

final class ProgressEventQueue extends LifecycleAdapter implements ProgressEventTracker {

    private final Queue<ProgressEvent> queue;
    private final String username;

    // for now a synthetic id, we can change to a more traceable one as and when
    private final JobId jobId = new JobId();

    ProgressEventQueue(
        Queue<ProgressEvent> queue,
        String username
    ) {
        this.queue = queue;
        this.username = username;
    }

    @Override
    public void addTaskProgressEvent(Task task) {
        var progressEvent = ImmutableProgressEvent.of(username, jobId, task);
        this.queue.offer(progressEvent);
    }

    @Override
    public void release() {
        queue.offer(ProgressEvent.endOfStreamEvent(username, jobId));
    }
}
