package io.takari.maven.timeline;

import java.io.Writer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TimelineSerializer {

  private static Gson gson = new GsonBuilder().setPrettyPrinting().create();
  
  public static void serialize(Writer writer, Timeline timeline) {
    gson.toJson(timeline, writer);
  }
}
