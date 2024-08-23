# Takari Maven Timeline

## How to generate a timeline for your project

To generate a timeline for your project add this section to your `pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>io.takari.maven</groupId>
        <artifactId>maven-timeline</artifactId>
        <version>2.0.0</version>
  </extension>
</extensions>
```

Once your project has run you will have a `target/timeline/timeline.html` in the top-level of your project. The output will look something like the following:

![Maven Timeline](maven-timeline.png)

## Building

Build time requirement is Java 11+ and recent Maven (3.9+ recommended).
Runtime Java requirement is Java 8+.
