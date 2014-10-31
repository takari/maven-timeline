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
  String start;
  String end;
  List<Event> events;
  public Timeline(String start, String end, List<Event> events) {
    this.start = start;
    this.end = end;
    this.events = events;   
  }
}
