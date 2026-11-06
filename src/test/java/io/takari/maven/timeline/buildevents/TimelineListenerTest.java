package io.takari.maven.timeline.buildevents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.MavenSession;
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
        for (int i = 0; i < metrics.size(); i++) {
            JsonObject m = metrics.get(i).getAsJsonObject();
            if (m.has("failed")) {
                assertEquals("deploy", m.get("goal").getAsString());
                failed++;
            }
        }
        assertEquals(1, failed);

        String js =
                new String(Files.readAllBytes(target.resolve("timeline/maven-timeline.js")), StandardCharsets.UTF_8);
        assertTrue(js.startsWith("window.timelineData = ") && js.endsWith(";"));
        JsonObject timeline =
                new Gson().fromJson(js.substring("window.timelineData = ".length(), js.length() - 1), JsonObject.class);
        assertEquals("a", timeline.get("artifactId").getAsString());
        assertEquals(5, timeline.getAsJsonArray("events").size());
        String html = new String(Files.readAllBytes(target.resolve("timeline/timeline.html")), StandardCharsets.UTF_8);
        assertTrue(html.contains("window.timelineData = ") && html.contains("function TimeLineApp()"));
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
