package io.takari.maven.timeline;

@SuppressWarnings({"FieldCanBeLocal", "unused"}) // needed for serialization
public class Event {

  private final long start;
  private long end;
  private long duration;
  private final long trackNum;
  private final String groupId;
  private final String artifactId;
  private final String phase;
  private final String goal;
  private final String id;

  public Event(long trackNum,
               long start,
               String groupId,
               String artifactId,
               String phase,
               String goal,
               String id) {
    this.start = start;
    this.trackNum = trackNum;
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.phase = phase;
    this.goal = goal;
    this.id = id;
  }

  public void setEnd(long end) {
    this.end = end;
  }

  public void setDuration(long duration) {
    this.duration = duration;
  }
}
