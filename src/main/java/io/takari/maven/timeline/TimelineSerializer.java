package io.takari.maven.timeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Writer;

public class TimelineSerializer {

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void serialize(Writer writer, Timeline timeline) {
        gson.toJson(timeline, writer);
    }
}
