package net.gageot.maven;

public class Event {
  String start;
  String end;
  long duration;
  String description;
  long trackNum;
  String color;
  
  boolean durationEvent = true;

  public Event(String description, long trackNum, String color, String start) {
    this.start = start;
    this.description = description;
    this.trackNum = trackNum;
    this.color = color;
  }

  public void setEnd(String end) {
    this.end = end;    
  }
  
  public void setDuration(long duration) {
    this.duration = duration;
    this.description = this.description + " (" + duration + " ms)"; 
  }
}
