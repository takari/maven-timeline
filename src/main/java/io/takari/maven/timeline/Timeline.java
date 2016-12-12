package io.takari.maven.timeline;

import java.util.List;

/**
 * Data structure that can be used by the Simile Timline component. For each track in the timeline we place all the builds performed on a single thread.
 *
 * @author Jason van Zyl
 *
 */
@SuppressWarnings({"FieldCanBeLocal", "unused"}) // needed for serialization
public class Timeline {

  private final long start;
  private final long end;
  private final String groupId;
  private final String artifactId;
  private final List<Event> events;

  public Timeline(long start, long end, String groupId, String artifactId, List<Event> events) {
    this.start = start;
    this.end = end;
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.events = events;
  }
}
