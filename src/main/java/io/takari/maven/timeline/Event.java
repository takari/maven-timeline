package io.takari.maven.timeline;

@SuppressWarnings("FieldCanBeLocal") // needed for serialization
public class Event {
  private final String start;
  private String end;
  private long duration;
  private String description;
  private final long trackNum;
  private final String color;
  private final String groupId;
  private final String artifactId;
  private final String phase;
  private final String goal;
  boolean durationEvent = true;

  public Event(String description,
               long trackNum,
               String color,
               String start,
               String groupId,
               String artifactId,
               String phase,
               String goal) {
    this.start = start;
    this.description = description;
    this.trackNum = trackNum;
    this.color = color;
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.phase = phase;
    this.goal = goal;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  public void setDuration(long duration) {
    this.duration = duration;
    this.description = this.description + " (" + duration + " ms)";
  }
}
