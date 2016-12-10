package io.takari.maven.timeline;

import java.util.List;

/**
 * Data structure that can be used by the Simile Timline component. For each track in the timeline we place all the builds performed on a single thread.
 *
 * @author Jason van Zyl
 *
 */
public class Timeline {
  // Custom
  long start;
  long end;
  List<Event> events;
  public Timeline(long start, long end, List<Event> events) {
    this.start = start;
    this.end = end;
    this.events = events;
  }
}
