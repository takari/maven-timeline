/**
 * Copyright (C) 2013 david@gageot.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.takari.maven.timeline.buildevents;

import io.takari.maven.timeline.Event;
import io.takari.maven.timeline.Timeline;
import io.takari.maven.timeline.TimelineSerializer;
import io.takari.maven.timeline.WebUtils;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;

// adjacent bars should be a different color
// highlight the critical path
// table with build values that are sortable

@Singleton
@Named
public final class TimelineListener extends AbstractEventSpy {
    private static final String METRICS_OUTPUT_FILE = "execution.metrics.output.file";
    private static final String DEFAULT_METRICS_OUTPUT_FILE = "target/execution-metrics.json";
    private static final String TIMELINE_OUTPUT_FILE = "execution.timeline.output.file";
    private static final String DEFAULT_TIMELINE_OUTPUT_FILE = "target/timeline/maven-timeline.js";

    private static final class Data {
        private final long start;
        private final Map<Execution, Metric> executionMetrics = new ConcurrentHashMap<>();
        private final Map<Execution, Event> timelineMetrics = new ConcurrentHashMap<>();
        private final Map<Long, Integer> threadToTrackNum = new ConcurrentHashMap<>();
        private final Map<Long, Integer> threadNumToColour = new ConcurrentHashMap<>();
        private final AtomicInteger trackNum = new AtomicInteger(0);

        public Data(long start) {
            this.start = start;
        }
    }

    private Data getData(RepositorySystemSession session) {
        return (Data)
                session.getData().computeIfAbsent(getClass().getSimpleName() + ".data", () -> new Data(nowInUtc()));
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (event instanceof ExecutionEvent) {
            ExecutionEvent executionEvent = (ExecutionEvent) event;
            switch (executionEvent.getType()) {
                case SessionStarted:
                    sessionStarted(executionEvent);
                    break;
                case SessionEnded:
                    sessionEnded(executionEvent);
                    break;
                case MojoStarted:
                    mojoStarted(executionEvent);
                    break;
                case MojoSkipped:
                    mojoSkipped(executionEvent);
                    break;
                case MojoSucceeded:
                    mojoSucceeded(executionEvent);
                    break;
                case MojoFailed:
                    mojoFailed(executionEvent);
                    break;
            }
        } else if (event instanceof RepositoryEvent) {
            RepositoryEvent repositoryEvent = (RepositoryEvent) event;
            switch (repositoryEvent.getType()) {
                case ARTIFACT_RESOLVING:
                case METADATA_RESOLVING:
                    resolving(repositoryEvent);
                    break;
                case ARTIFACT_RESOLVED:
                case METADATA_RESOLVED:
                    resolved(repositoryEvent);
                    break;
            }
        }
    }

    private void resolving(RepositoryEvent repositoryEvent) {
        Data data = getData(repositoryEvent.getSession());
        Execution key = key(repositoryEvent);
        if (key == null) {
            return;
        }
        Long threadId = Thread.currentThread().getId();
        int threadTrackNum = data.threadToTrackNum.computeIfAbsent(threadId, k -> data.trackNum.getAndIncrement());
        Integer colour = data.threadNumToColour.get(threadId);
        if (colour == null) {
            colour = 0;
            data.threadNumToColour.put(threadId, colour);
        } else {
            colour = 1 - colour;
            data.threadNumToColour.put(threadId, colour);
        }
        data.executionMetrics.put(key, new Metric(key, Thread.currentThread().getId(), millis(data)));
        data.timelineMetrics.put(
                key,
                new Event(
                        threadTrackNum,
                        nowInUtc(),
                        key.groupId,
                        key.artifactId,
                        key.mojoGroupId,
                        key.mojoArtifactId,
                        key.phase,
                        key.goal,
                        key.id));
    }

    private void resolved(RepositoryEvent repositoryEvent) {
        Data data = getData(repositoryEvent.getSession());
        Execution key = key(repositoryEvent);
        if (key == null) {
            return;
        }
        final Event timelineMetric = data.timelineMetrics.get(key);
        final Metric metric = data.executionMetrics.get(key);
        if (metric == null) {
            return;
        }
        metric.setEnd(millis(data));
        timelineMetric.setEnd(System.currentTimeMillis());
        timelineMetric.setDuration(metric.end - metric.start);
    }

    private long nowInUtc() {
        return System.currentTimeMillis();
    }

    private long millis(Data data) {
        return System.currentTimeMillis() - data.start;
    }

    private void mojoStarted(ExecutionEvent event) {
        Data data = getData(event.getSession().getRepositorySession());
        Execution key = key(event);
        Long threadId = Thread.currentThread().getId();
        int threadTrackNum = data.threadToTrackNum.computeIfAbsent(threadId, k -> data.trackNum.getAndIncrement());
        Integer colour = data.threadNumToColour.get(threadId);
        if (colour == null) {
            colour = 0;
            data.threadNumToColour.put(threadId, colour);
        } else {
            colour = 1 - colour;
            data.threadNumToColour.put(threadId, colour);
        }
        data.executionMetrics.put(key, new Metric(key, Thread.currentThread().getId(), millis(data)));
        data.timelineMetrics.put(
                key,
                new Event(
                        threadTrackNum,
                        nowInUtc(),
                        key.groupId,
                        key.artifactId,
                        key.mojoGroupId,
                        key.mojoArtifactId,
                        key.phase,
                        key.goal,
                        key.id));
    }

    private void mojoSkipped(ExecutionEvent event) {
        mojoEnd(event);
    }

    private void mojoSucceeded(ExecutionEvent event) {
        mojoEnd(event);
    }

    private void mojoFailed(ExecutionEvent event) {
        mojoEnd(event);
    }

    private void sessionStarted(ExecutionEvent event) {
        getData(event.getSession().getRepositorySession());
    }

    private void sessionEnded(ExecutionEvent event) throws IOException {
        Data data = getData(event.getSession().getRepositorySession());
        exportExecutionMetrics(data, path(event.getSession(), METRICS_OUTPUT_FILE, DEFAULT_METRICS_OUTPUT_FILE));
        exportTimeline(
                data,
                path(event.getSession(), TIMELINE_OUTPUT_FILE, DEFAULT_TIMELINE_OUTPUT_FILE),
                event.getSession().getTopLevelProject().getGroupId(),
                event.getSession().getTopLevelProject().getArtifactId());
    }

    private void mojoEnd(ExecutionEvent event) {
        Data data = getData(event.getSession().getRepositorySession());
        Execution execution = key(event);
        final Event timelineMetric = data.timelineMetrics.get(execution);
        final Metric metric = data.executionMetrics.get(execution);
        if (metric == null) {
            return;
        }
        metric.setEnd(millis(data));
        timelineMetric.setEnd(System.currentTimeMillis());
        timelineMetric.setDuration(metric.end - metric.start);
    }

    private Path path(MavenSession session, String key, String defaultValue) {
        Path path = Paths.get(session.getUserProperties().getProperty(key, defaultValue));
        if (path.isAbsolute()) {
            return path;
        }
        String buildDir = session.getExecutionRootDirectory();
        return Paths.get(buildDir).resolve(path);
    }

    private Execution key(ExecutionEvent event) {
        final MojoExecution mojo = event.getMojoExecution();
        final MavenProject project = event.getProject();
        return new Execution(
                project.getGroupId(),
                project.getArtifactId(),
                mojo.getGroupId(),
                mojo.getArtifactId(),
                mojo.getLifecyclePhase(),
                mojo.getGoal(),
                mojo.getExecutionId());
    }

    private Execution key(RepositoryEvent event) {
        if (event.getArtifact() != null) {
            return new Execution(
                    "org.apache.maven.resolver",
                    "maven-resolver-api",
                    event.getArtifact().getGroupId(),
                    event.getArtifact().getArtifactId(),
                    "resolve",
                    "resolve",
                    "none");
        } else if (event.getMetadata() != null) {
            return new Execution(
                    "org.apache.maven.resolver",
                    "maven-resolver-api",
                    event.getMetadata().getGroupId(),
                    event.getMetadata().getArtifactId(),
                    "resolve",
                    "resolve",
                    "none");
        }
        return null;
    }

    private void exportExecutionMetrics(Data data, Path metrics) throws IOException {
        Files.createDirectories(metrics.getParent());
        try (Writer writer = Files.newBufferedWriter(metrics)) {
            Metric.array(writer, data.executionMetrics.values());
        }
    }

    private void exportTimeline(Data data, Path timelinePath, String groupId, String artifactId) throws IOException {
        long endTime = nowInUtc();
        Files.createDirectories(timelinePath.getParent());
        WebUtils.copyResourcesToDirectory(
                getClass(), "timeline", timelinePath.getParent().toFile());
        try (Writer mavenTimelineWriter = Files.newBufferedWriter(timelinePath)) {
            Timeline timeline = new Timeline(
                    data.start, endTime, groupId, artifactId, new ArrayList<>(data.timelineMetrics.values()));
            mavenTimelineWriter.write("window.timelineData = ");
            TimelineSerializer.serialize(mavenTimelineWriter, timeline);
            mavenTimelineWriter.write(";");
        }
    }

    //
    //
    //

    static class Execution {
        final transient int hashCode;

        final String groupId;
        final String artifactId;
        final String mojoGroupId;
        final String mojoArtifactId;
        final String phase;
        final String goal;
        final String id;

        Execution(
                String groupId,
                String artifactId,
                String mojoGroupId,
                String mojoArtifactId,
                String phase,
                String goal,
                String id) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.mojoGroupId = mojoGroupId;
            this.mojoArtifactId = mojoArtifactId;
            this.phase = phase;
            this.goal = goal;
            this.id = id;

            this.hashCode = Objects.hash(groupId, artifactId, mojoGroupId, mojoArtifactId, phase, goal, id);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Execution execution = (Execution) o;
            if (!Objects.equals(groupId, execution.groupId)) return false;
            if (!Objects.equals(artifactId, execution.artifactId)) return false;
            if (!Objects.equals(mojoGroupId, execution.mojoGroupId)) return false;
            if (!Objects.equals(mojoArtifactId, execution.mojoArtifactId)) return false;
            if (!Objects.equals(phase, execution.phase)) return false;
            if (!Objects.equals(goal, execution.goal)) return false;
            return Objects.equals(id, execution.id);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return groupId + ":" + artifactId + ":" + mojoGroupId + ":" + mojoArtifactId + ":" + phase + ":" + goal
                    + ":" + id;
        }
    }

    static class Metric {
        final Execution execution;
        final Long threadId;
        final Long start;
        Long end;

        Metric(Execution execution, Long threadId, Long start) {
            this.execution = execution;
            this.threadId = threadId;
            this.start = start;
        }

        void setEnd(Long end) {
            this.end = end;
        }

        String toJSON() {
            return record(
                    value("groupId", execution.groupId),
                    value("artifactId", execution.artifactId),
                    value("mojoGroupId", execution.mojoGroupId),
                    value("mojoArtifactId", execution.mojoArtifactId),
                    value("phase", execution.phase),
                    value("goal", execution.goal),
                    value("id", execution.id),
                    value("threadId", threadId),
                    value("start", start),
                    value("end", end));
        }

        private String value(String key, String value) {
            return "\"" + key + "\":\"" + value + "\"";
        }

        private String value(String key, Long value) {
            return "\"" + key + "\":" + value + "";
        }

        private String record(String... values) {
            StringBuilder b = new StringBuilder();
            b.append("{");
            for (String value : values) {
                b.append(value).append(",");
            }
            return b.deleteCharAt(b.length() - 1).append("}").toString();
        }

        static void array(Appendable a, Iterable<Metric> metrics) throws IOException {
            a.append("[");
            Iterator<Metric> it = metrics.iterator();
            if (it.hasNext()) {
                a.append(it.next().toJSON());
            }
            while (it.hasNext()) {
                a.append(",").append(it.next().toJSON());
            }
            a.append("]");
        }
    }
}
