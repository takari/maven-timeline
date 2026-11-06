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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.util.ConfigUtils;
import org.slf4j.LoggerFactory;

@Singleton
@Named
public final class TimelineListener extends AbstractEventSpy {
    static final String METRICS_OUTPUT_FILE = "maven-timeline.metrics.output.file";
    static final String METRICS_OUTPUT_FILE_LEGACY = "maven-timeline.metrics.output"; // used by 2.1.0 and earlier
    static final String DEFAULT_METRICS_OUTPUT_FILE = "execution-metrics.json";
    static final String TIMELINE_OUTPUT_FILE = "maven-timeline.timeline.output.file";
    static final String TIMELINE_OUTPUT_FILE_LEGACY = "maven-timeline.timeline.output"; // used by 2.1.0 and earlier
    static final String DEFAULT_TIMELINE_OUTPUT_FILE = "timeline/maven-timeline.js";
    static final String DISCRIMINATOR = "maven-timeline.discriminator";
    static final String ENABLED = "maven-timeline.enabled";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** One mojo execution or one artifact/metadata resolution; serialized as-is into the timeline. */
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // needed for serialization
    static final class Event {
        final transient long threadId;
        transient boolean resolver;
        final long trackNum;
        final long start;
        long end;
        long duration;
        Boolean failed; // null (omitted from JSON) unless the mojo failed
        final String groupId;
        final String artifactId;
        final String mojoGroupId;
        final String mojoArtifactId;
        final String phase;
        final String goal;
        final String id;

        Event(
                long threadId,
                long trackNum,
                long start,
                String groupId,
                String artifactId,
                String mojoGroupId,
                String mojoArtifactId,
                String phase,
                String goal,
                String id) {
            this.threadId = threadId;
            this.trackNum = trackNum;
            this.start = start;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.mojoGroupId = mojoGroupId;
            this.mojoArtifactId = mojoArtifactId;
            this.phase = phase;
            this.goal = goal;
            this.id = id;
        }

        String key() {
            // start and end of one execution always happen on the same thread
            return threadId + "|" + groupId + ":" + artifactId + ":" + mojoGroupId + ":" + mojoArtifactId + ":" + phase
                    + ":" + goal + ":" + id;
        }
    }

    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // needed for serialization
    private static final class Timeline {
        private final long start;
        private final long end;
        private final String groupId;
        private final String artifactId;
        private final Map<Integer, String> tracks;
        private final List<String> criticalPath;
        private final List<Event> events;

        Timeline(
                long start,
                long end,
                String groupId,
                String artifactId,
                Map<Integer, String> tracks,
                List<String> criticalPath,
                List<Event> events) {
            this.start = start;
            this.end = end;
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.tracks = tracks;
            this.criticalPath = criticalPath;
            this.events = events;
        }
    }

    private static final class Data {
        private final long start = System.currentTimeMillis();
        private final Map<String, Event> running = new ConcurrentHashMap<>();
        private final Queue<Event> finished = new ConcurrentLinkedQueue<>();
        private final Map<Long, Integer> threadToTrackNum = new ConcurrentHashMap<>();
        private final Map<Integer, String> trackNames = new ConcurrentHashMap<>();
        private final AtomicInteger trackNum = new AtomicInteger(0);
    }

    private final boolean enabled;

    public TimelineListener() {
        this.enabled = Boolean.parseBoolean(System.getProperty(ENABLED, "true"));
    }

    @Override
    public void onEvent(Object event) throws Exception {
        if (!enabled) {
            return;
        }
        if (event instanceof ExecutionEvent) {
            ExecutionEvent executionEvent = (ExecutionEvent) event;
            RepositorySystemSession rss = executionEvent.getSession().getRepositorySession();
            switch (executionEvent.getType()) {
                case SessionStarted:
                    getData(rss);
                    break;
                case SessionEnded:
                    sessionEnded(executionEvent.getSession());
                    break;
                case MojoStarted:
                    start(getData(rss), executionEvent);
                    break;
                case MojoSkipped:
                case MojoSucceeded:
                case MojoFailed:
                    end(getData(rss), executionEvent);
                    break;
                default:
            }
        } else if (event instanceof RepositoryEvent) {
            RepositoryEvent repositoryEvent = (RepositoryEvent) event;
            switch (repositoryEvent.getType()) {
                case ARTIFACT_RESOLVING:
                case METADATA_RESOLVING:
                    start(getData(repositoryEvent.getSession()), repositoryEvent);
                    break;
                case ARTIFACT_RESOLVED:
                case METADATA_RESOLVED:
                    end(getData(repositoryEvent.getSession()), repositoryEvent);
                    break;
                default:
            }
        }
    }

    private Data getData(RepositorySystemSession session) {
        return (Data) session.getData().computeIfAbsent(getClass().getSimpleName() + ".data", Data::new);
    }

    private void start(Data data, Object event) {
        Event e = newEvent(data, event);
        if (e != null) {
            data.running.put(e.key(), e);
        }
    }

    private void end(Data data, Object event) {
        Event e = newEvent(data, event);
        if (e == null) {
            return;
        }
        Event started = data.running.remove(e.key());
        if (started != null) {
            started.end = e.start;
            started.duration = started.end - started.start;
            if (event instanceof ExecutionEvent
                    && ((ExecutionEvent) event).getType() == ExecutionEvent.Type.MojoFailed) {
                started.failed = Boolean.TRUE;
            }
            data.finished.add(started);
        }
    }

    private Event newEvent(Data data, Object event) {
        long threadId = Thread.currentThread().getId();
        int track = data.threadToTrackNum.computeIfAbsent(threadId, k -> data.trackNum.getAndIncrement());
        data.trackNames.putIfAbsent(track, Thread.currentThread().getName());
        long now = System.currentTimeMillis();
        if (event instanceof ExecutionEvent) {
            MojoExecution mojo = ((ExecutionEvent) event).getMojoExecution();
            MavenProject project = ((ExecutionEvent) event).getProject();
            return new Event(
                    threadId,
                    track,
                    now,
                    project.getGroupId(),
                    project.getArtifactId(),
                    mojo.getGroupId(),
                    mojo.getArtifactId(),
                    mojo.getLifecyclePhase(),
                    mojo.getGoal(),
                    mojo.getExecutionId());
        }
        RepositoryEvent re = (RepositoryEvent) event;
        String groupId, artifactId, id;
        if (re.getArtifact() != null) {
            Artifact a = re.getArtifact();
            groupId = a.getGroupId();
            artifactId = a.getArtifactId();
            id = a.getVersion() + ":" + a.getExtension() + (a.getClassifier().isEmpty() ? "" : ":" + a.getClassifier());
        } else if (re.getMetadata() != null) {
            Metadata m = re.getMetadata();
            groupId = m.getGroupId();
            artifactId = m.getArtifactId();
            id = m.getVersion() + ":" + m.getType();
        } else {
            return null;
        }
        Event e = new Event(
                threadId,
                track,
                now,
                "org.apache.maven.resolver",
                "maven-resolver-api",
                groupId,
                artifactId,
                "resolve",
                "resolve",
                id);
        e.resolver = true;
        return e;
    }

    private void sessionEnded(MavenSession session) throws IOException {
        Data data = getData(session.getRepositorySession());
        MavenProject top = session.getTopLevelProject();
        Path buildDirectory = null;
        if (top != null && top.getBuild() != null && top.getBuild().getDirectory() != null) {
            buildDirectory = Paths.get(top.getBuild().getDirectory()); // target/
        } else if (session.getExecutionRootDirectory() != null) {
            buildDirectory = Paths.get(session.getExecutionRootDirectory()); // cwd
        }
        if (buildDirectory == null) {
            LoggerFactory.getLogger(getClass())
                    .warn("Maven Timeline could not determine output directory; no output written");
            return;
        }

        RepositorySystemSession rss = session.getRepositorySession();
        String discriminator = ConfigUtils.getString(rss, null, DISCRIMINATOR);
        if (discriminator != null) {
            buildDirectory = buildDirectory.resolve(discriminator);
        }
        List<Event> events = new ArrayList<>(data.finished);
        ProjectDependencyGraph graph = session.getProjectDependencyGraph();
        List<String> criticalPath = graph == null || session.getProjects() == null
                ? Collections.emptyList()
                : criticalPath(p -> graph.getUpstreamProjects(p, false), session.getProjects(), events);
        exportExecutionMetrics(
                data,
                events,
                buildDirectory.resolve(ConfigUtils.getString(
                        rss, DEFAULT_METRICS_OUTPUT_FILE, METRICS_OUTPUT_FILE, METRICS_OUTPUT_FILE_LEGACY)));
        exportTimeline(
                buildDirectory.resolve(ConfigUtils.getString(
                        rss, DEFAULT_TIMELINE_OUTPUT_FILE, TIMELINE_OUTPUT_FILE, TIMELINE_OUTPUT_FILE_LEGACY)),
                new Timeline(
                        data.start,
                        System.currentTimeMillis(),
                        top != null ? top.getGroupId() : null,
                        top != null ? top.getArtifactId() : null,
                        new TreeMap<>(data.trackNames),
                        criticalPath,
                        events));
    }

    /**
     * A module starts only once all its upstream modules have finished, so walking back from the module that finished
     * last, always via the upstream module that finished last, yields the chain that determined the build's length.
     * Returns "groupId:artifactId" entries, first module first.
     */
    static List<String> criticalPath(
            Function<MavenProject, List<MavenProject>> upstream, List<MavenProject> projects, List<Event> events) {
        Map<String, Long> moduleEnd = new HashMap<>();
        for (Event e : events) {
            if (!e.resolver) {
                moduleEnd.merge(e.groupId + ":" + e.artifactId, e.end, Math::max);
            }
        }
        MavenProject current = lastFinished(projects, moduleEnd);
        LinkedList<String> path = new LinkedList<>();
        while (current != null) {
            path.addFirst(current.getGroupId() + ":" + current.getArtifactId());
            current = lastFinished(upstream.apply(current), moduleEnd);
        }
        return path;
    }

    private static MavenProject lastFinished(List<MavenProject> projects, Map<String, Long> moduleEnd) {
        MavenProject last = null;
        long lastEnd = Long.MIN_VALUE;
        for (MavenProject p : projects) {
            Long end = moduleEnd.get(p.getGroupId() + ":" + p.getArtifactId());
            if (end != null && end > lastEnd) {
                last = p;
                lastEnd = end;
            }
        }
        return last;
    }

    private void exportExecutionMetrics(Data data, List<Event> events, Path metrics) throws IOException {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Event e : events) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("groupId", e.groupId);
            r.put("artifactId", e.artifactId);
            r.put("mojoGroupId", e.mojoGroupId);
            r.put("mojoArtifactId", e.mojoArtifactId);
            r.put("phase", e.phase);
            r.put("goal", e.goal);
            r.put("id", e.id);
            r.put("threadId", e.threadId);
            r.put("start", e.start - data.start);
            r.put("end", e.end - data.start);
            r.put("failed", e.failed);
            records.add(r);
        }
        Files.createDirectories(metrics.getParent());
        try (Writer writer = Files.newBufferedWriter(metrics)) {
            GSON.toJson(records, writer);
        }
    }

    /** Writes the data file, plus timeline.html next to it with all scripts, styles and data inlined. */
    private void exportTimeline(Path timelinePath, Timeline timeline) throws IOException {
        Files.createDirectories(timelinePath.getParent());
        String data = "window.timelineData = " + GSON.toJson(timeline) + ";"; // Gson escapes '<', so no "</script>"
        try (Writer writer = Files.newBufferedWriter(timelinePath)) {
            writer.write(data);
        }
        String html = resource("timeline.html")
                .replace("<script src=\"maven-timeline.js\"></script>", "<script>\n" + data + "\n</script>")
                .replace(
                        "<link rel=\"stylesheet\" href=\"style.css\">",
                        "<style>\n" + resource("style.css") + "</style>");
        for (String script : new String[] {"app.js", "database.js", "timeline.js"}) {
            html = html.replace(
                    "<script src=\"" + script + "\"></script>", "<script>\n" + resource(script) + "</script>");
        }
        try (Writer writer = Files.newBufferedWriter(timelinePath.resolveSibling("timeline.html"))) {
            writer.write(html);
        }
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = TimelineListener.class.getResourceAsStream("/timeline/" + name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            for (int n; (n = in.read(buf)) > 0; ) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
