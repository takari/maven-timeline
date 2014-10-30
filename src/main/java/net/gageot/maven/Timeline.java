package net.gageot.maven;

import java.util.List;

/**
 * Represents a timeline data structure that can be used by the Simile Timline component. For each
 * track in the timeline we place all the builds performed on a single thread.
 * 
 * @author Jason van Zyl
 *
 */
public class Timeline {
  String dateTimeFormat = "iso8601";
  List<Event> events;
  public Timeline(List<Event> events) {
    this.events = events;
  }
}
