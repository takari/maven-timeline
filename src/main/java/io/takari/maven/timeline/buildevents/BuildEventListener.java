/**
 * Copyright (C) 2013 david@gageot.net Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License
 */
package io.takari.maven.timeline.buildevents;

import io.takari.maven.timeline.Event;
import io.takari.maven.timeline.Timeline;
import io.takari.maven.timeline.TimelineSerializer;
import io.takari.maven.timeline.WebUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.project.MavenProject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.google.common.collect.Lists;

// adjacent bars should be a different color
// highlight the critical path
// table with build values that are sortable

public class BuildEventListener extends AbstractExecutionListener {
  private final File mavenTimeline;
  private final File output;
  private final long start;
  private final Map<Execution, Metric> executionMetrics = new ConcurrentHashMap<Execution, Metric>();
  private final Map<Execution, Event> timelineMetrics = new ConcurrentHashMap<Execution, Event>();
  private final Map<Long, Long> threadToTrackNum = new ConcurrentHashMap<Long, Long>();
  private final Map<Long, Integer> threadNumToColour = new ConcurrentHashMap<Long, Integer>();
  private long trackNum = 1;

  String startTime;
  String endTime;

  private static String[] colours = new String[] {
      "blue", "green"
  };

  public BuildEventListener(File output, File mavenTimeline) {
    this.output = output;
    this.mavenTimeline = mavenTimeline;
    this.start = System.currentTimeMillis();
    this.startTime = nowInUtc();
  }

  long millis() {
    return System.currentTimeMillis() - start;
  }

  @Override
  public void mojoStarted(ExecutionEvent event) {
    Execution key = key(event);
    Long threadId = Thread.currentThread().getId();
    Long threadTrackNum = threadToTrackNum.get(threadId);
    if (threadTrackNum == null) {
      threadTrackNum = trackNum;
      threadToTrackNum.put(threadId, threadTrackNum);
      trackNum++;
    }
    Integer colour = threadNumToColour.get(threadId);
    if (colour == null) {
      colour = 0;
      threadNumToColour.put(threadId, colour);
    } else {
      colour = 1 - colour;
      threadNumToColour.put(threadId, colour);
    }
    executionMetrics.put(key, new Metric(key, Thread.currentThread().getId(), millis()));
    timelineMetrics.put(key, new Event(key.toString(), threadTrackNum, colours[colour], nowInUtc()));
  }

  String nowInUtc() {
    return new DateTime(DateTimeZone.UTC).toString();
  }

  @Override
  public void mojoSkipped(ExecutionEvent event) {
    mojoEnd(event);
  }

  @Override
  public void mojoSucceeded(ExecutionEvent event) {
    mojoEnd(event);
  }

  @Override
  public void mojoFailed(ExecutionEvent event) {
    mojoEnd(event);
  }

  private void mojoEnd(ExecutionEvent event) {
    final Event timelineMetric = timelineMetrics.get(key(event));
    final Metric metric = executionMetrics.get(key(event));
    if (metric == null) {
      return;
    }
    metric.setEnd(millis());
    timelineMetric.setEnd(new DateTime(DateTimeZone.UTC).toString());
    timelineMetric.setDuration(metric.end - metric.start);
  }

  @Override
  public void sessionEnded(ExecutionEvent event) {
    try {
      report();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private Execution key(ExecutionEvent event) {
    final MojoExecution mojo = event.getMojoExecution();
    final MavenProject project = event.getProject();
    return new Execution(project.getGroupId(), project.getArtifactId(), mojo.getLifecyclePhase(), mojo.getGoal(), mojo.getExecutionId());
  }

  private void report() throws IOException {
    File path = output.getParentFile();
    if (!(path.isDirectory() || path.mkdirs())) {
      throw new IOException("Unable to create " + path);
    }

    Writer writer = new BufferedWriter(new FileWriter(output));
    try {
      Metric.array(writer, executionMetrics.values());
    } finally {
      writer.close();
    }

    exportTimeline();
  }

  private void exportTimeline() throws IOException {
    endTime = nowInUtc();
    WebUtils.copyResourcesToDirectory(getClass(), "timeline", mavenTimeline.getParentFile());
    try(Writer mavenTimelineWriter = new BufferedWriter(new FileWriter(mavenTimeline))) {
      Timeline timeline = new Timeline(startTime, endTime, Lists.newArrayList(timelineMetrics.values()));
      mavenTimelineWriter.write("window.timelineData = ");
      TimelineSerializer.serialize(mavenTimelineWriter, timeline);
      mavenTimelineWriter.write(";");
    }
  }

  //
  //
  //

  static class Execution {
    final String groupId;
    final String artifactId;
    final String phase;
    final String goal;
    final String id;

    public Execution(String groupId, String artifactId, String phase, String goal, String id) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.phase = phase;
      this.goal = goal;
      this.id = id;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
      result = prime * result + ((goal == null) ? 0 : goal.hashCode());
      result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
      result = prime * result + ((id == null) ? 0 : id.hashCode());
      result = prime * result + ((phase == null) ? 0 : phase.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      Execution other = (Execution) obj;
      if (artifactId == null) {
        if (other.artifactId != null) {
          return false;
        }
      } else if (!artifactId.equals(other.artifactId)) {
        return false;
      }
      if (goal == null) {
        if (other.goal != null) {
          return false;
        }
      } else if (!goal.equals(other.goal)) {
        return false;
      }
      if (groupId == null) {
        if (other.groupId != null) {
          return false;
        }
      } else if (!groupId.equals(other.groupId)) {
        return false;
      }
      if (id == null) {
        if (other.id != null) {
          return false;
        }
      } else if (!id.equals(other.id)) {
        return false;
      }
      if (phase == null) {
        if (other.phase != null) {
          return false;
        }
      } else if (!phase.equals(other.phase)) {
        return false;
      }
      return true;
    }

    @Override
    public String toString() {
      return groupId + ":" + artifactId + ":" + phase + ":" + goal + ":" + id;
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
      return record(value("groupId", execution.groupId), value("artifactId", execution.artifactId), value("phase", execution.phase), value("goal", execution.goal), value("id", execution.id),
          value("threadId", threadId), value("start", start), value("end", end));
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
