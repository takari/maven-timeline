package io.takari.maven.timeline.buildevents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.execution.ProjectDependencyGraph;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryEvent;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TimelineListenerTest {

    @TempDir
    Path tmp;

    @Test
    void recordsRepeatedAndCollidingExecutions() throws Exception {
        DefaultRepositorySystemSession rss = new DefaultRepositorySystemSession();
        MavenProject project = new MavenProject();
        project.setGroupId("g");
        project.setArtifactId("a");
        project.setExecutionRoot(true);
        project.getModel().setBuild(new Build());
        project.getBuild().setDirectory(tmp.resolve("target").toString());
        @SuppressWarnings("deprecation")
        MavenSession session =
                new MavenSession(null, rss, new DefaultMavenExecutionRequest(), new DefaultMavenExecutionResult());
        session.setProjects(Collections.singletonList(project));
        session.setProjectDependencyGraph(new ProjectDependencyGraph() {
            @Override
            public List<MavenProject> getAllProjects() {
                return Collections.singletonList(project);
            }

            @Override
            public List<MavenProject> getSortedProjects() {
                return Collections.singletonList(project);
            }

            @Override
            public List<MavenProject> getDownstreamProjects(MavenProject p, boolean transitive) {
                return Collections.emptyList();
            }

            @Override
            public List<MavenProject> getUpstreamProjects(MavenProject p, boolean transitive) {
                return Collections.emptyList();
            }
        });

        Plugin plugin = new Plugin();
        plugin.setGroupId("org.apache.maven.plugins");
        plugin.setArtifactId("maven-install-plugin");
        MojoExecution mojo = new MojoExecution(plugin, "install", "default-install");
        mojo.setLifecyclePhase("install");

        TimelineListener listener = new TimelineListener();
        listener.onEvent(event(ExecutionEvent.Type.SessionStarted, session, null, null));
        // same mojo execution twice (e.g. "mvn install install"): both must be recorded
        for (int i = 0; i < 2; i++) {
            listener.onEvent(event(ExecutionEvent.Type.MojoStarted, session, project, mojo));
            listener.onEvent(event(ExecutionEvent.Type.MojoSucceeded, session, project, mojo));
        }
        // failed mojo must be flagged
        MojoExecution deploy = new MojoExecution(plugin, "deploy", "default-deploy");
        listener.onEvent(event(ExecutionEvent.Type.MojoStarted, session, project, deploy));
        listener.onEvent(event(ExecutionEvent.Type.MojoFailed, session, project, deploy));
        // pom and jar of the same GA: must not overwrite each other
        for (String ext : new String[] {"pom", "jar"}) {
            DefaultArtifact artifact = new DefaultArtifact("x", "y", ext, "1.0");
            listener.onEvent(new RepositoryEvent.Builder(rss, RepositoryEvent.EventType.ARTIFACT_RESOLVING)
                    .setArtifact(artifact)
                    .build());
            listener.onEvent(new RepositoryEvent.Builder(rss, RepositoryEvent.EventType.ARTIFACT_RESOLVED)
                    .setArtifact(artifact)
                    .build());
        }
        listener.onEvent(event(ExecutionEvent.Type.SessionEnded, session, null, null));

        Path target = tmp.resolve("target");
        JsonArray metrics = new Gson()
                .fromJson(
                        new String(
                                Files.readAllBytes(target.resolve("execution-metrics.json")), StandardCharsets.UTF_8),
                        JsonArray.class);
        assertEquals(5, metrics.size());
        int failed = 0;
        int critical = 0;
        for (int i = 0; i < metrics.size(); i++) {
            JsonObject m = metrics.get(i).getAsJsonObject();
            assertEquals(Thread.currentThread().getName(), m.get("threadName").getAsString());
            if (m.has("critical")) {
                assertEquals("g", m.get("groupId").getAsString()); // project mojos only, never resolver events
                critical++;
            }
            if (m.has("failed")) {
                assertEquals("deploy", m.get("goal").getAsString());
                failed++;
            }
        }
        assertEquals(1, failed);
        assertEquals(3, critical);

        String html = new String(Files.readAllBytes(target.resolve("timeline/timeline.html")), StandardCharsets.UTF_8);
        assertTrue(html.contains("function TimeLineApp()"));
        int dataStart = html.indexOf("window.timelineData = ") + "window.timelineData = ".length();
        JsonObject timeline = new Gson()
                .fromJson(html.substring(dataStart, html.indexOf(";\n</script>", dataStart)), JsonObject.class);
        assertEquals("a", timeline.get("artifactId").getAsString());
        assertEquals(5, timeline.getAsJsonArray("events").size());
        assertEquals(Arrays.asList("timeline.html"), listFileNames(target.resolve("timeline")));
        assertTrue(!html.contains(" src=") && !html.contains(" href="), "timeline.html must be self-contained");
    }

    @Test
    void criticalPathFollowsLastFinishedUpstream() {
        // c depends on a and b; b finishes after a, so b (not a) gated c
        MavenProject a = project("a"), b = project("b"), c = project("c"), unrelated = project("d");
        Map<MavenProject, List<MavenProject>> upstream = new HashMap<>();
        upstream.put(a, Collections.emptyList());
        upstream.put(b, Collections.emptyList());
        upstream.put(c, Arrays.asList(a, b));
        upstream.put(unrelated, Collections.emptyList());
        List<TimelineListener.Event> events = Arrays.asList(
                mojoEvent("a", 0, 10), mojoEvent("b", 0, 20), mojoEvent("c", 20, 30), mojoEvent("d", 0, 25));
        assertEquals(
                Arrays.asList("g:b", "g:c"),
                TimelineListener.criticalPath(upstream::get, Arrays.asList(a, b, c, unrelated), events));
    }

    private static List<String> listFileNames(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).collect(Collectors.toList());
        }
    }

    private static MavenProject project(String artifactId) {
        MavenProject p = new MavenProject();
        p.setGroupId("g");
        p.setArtifactId(artifactId);
        return p;
    }

    private static TimelineListener.Event mojoEvent(String artifactId, long start, long end) {
        TimelineListener.Event e =
                new TimelineListener.Event(1, 0, start, "g", artifactId, "p", "p", "compile", "compile", "x");
        e.end = end;
        return e;
    }

    private static ExecutionEvent event(
            ExecutionEvent.Type type, MavenSession session, MavenProject project, MojoExecution mojo) {
        return new ExecutionEvent() {
            @Override
            public Type getType() {
                return type;
            }

            @Override
            public MavenSession getSession() {
                return session;
            }

            @Override
            public MavenProject getProject() {
                return project;
            }

            @Override
            public MojoExecution getMojoExecution() {
                return mojo;
            }

            @Override
            public Exception getException() {
                return null;
            }
        };
    }
}
