package io.takari.maven.timeline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class WebUtils {

  public static void openUrl(String url) {
    String osName = System.getProperty("os.name");
    try {
      if (osName.startsWith("Mac OS")) {
        Class<?> fileMgr = Class.forName("com.apple.eio.FileManager");
        Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[] {
          String.class
        });
        openURL.invoke(null, new Object[] {
          url
        });
      } else if (osName.startsWith("Windows")) {
        Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
      } else { // assume Unix or Linux
        String[] browsers = {
            "firefox", "opera", "konqueror", "epiphany", "mozilla", "netscape"
        };
        String browser = null;
        for (int count = 0; count < browsers.length && browser == null; count++) {
          if (Runtime.getRuntime().exec(new String[] {
              "which", browsers[count]
          }).waitFor() == 0) {
            browser = browsers[count];
          }
        }
        if (browser == null) {
          throw new Exception("Could not find web browser");
        } else {
          Runtime.getRuntime().exec(new String[] {
              browser, url
          });
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static void copyResourcesToDirectory(File jar, String jarDirectory, File outputDirectory) throws IOException {
    try (JarFile fromJar = new JarFile(jar)) {
      for (Enumeration<JarEntry> entries = fromJar.entries(); entries.hasMoreElements();) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().startsWith(jarDirectory + "/") && !entry.isDirectory()) {
          File dest = new File(outputDirectory + "/" + entry.getName().substring(jarDirectory.length() + 1));
          File parent = dest.getParentFile();
          if (parent != null) {
            parent.mkdirs();
          }
          try (FileOutputStream out = new FileOutputStream(dest); InputStream in = fromJar.getInputStream(entry)) {
            copy(in, out);
          }
        }
      }
    }
  }

  private static void copy(InputStream from, OutputStream to) throws IOException {
    byte[] buf = new byte[8192];
    while (true) {
      int r = from.read(buf);
      if (r == -1) {
        break;
      }
      to.write(buf, 0, r);
    }
  }

  //
  // Copy all resource in jarDirectory to outputDirectory
  //
  public static void copyResourcesToDirectory(Class<?> clazz, String jarDirectory, File outputDirectory) throws IOException {
    CodeSource src = clazz.getProtectionDomain().getCodeSource();
    if (src != null) {
      copyResourcesToDirectory(urlToFile(src.getLocation()), jarDirectory, outputDirectory);
    }
  }

  private static File urlToFile(URL url) {
    File f;
    try {
      f = new File(url.toURI());
    } catch (URISyntaxException e) {
      f = new File(url.getPath());
    }
    return f;
  }

  public void launch(String path, File jar, File outputDirectory) throws IOException {
    copyResourcesToDirectory(jar, "timeline", outputDirectory);
    openUrl(new File("/tmp/timeline/timeline.html").toURI().toURL().toExternalForm());
  }

  public static void main(String[] args) throws Exception {
    String path = "timeline/timeline.html";
    File jar = new File("/Users/jvanzyl/js/takari/maven-timeline/target/maven-timeline-1.4.jar");
    File outputDirectory = new File("/tmp/timeline");
    WebUtils w = new WebUtils();
    w.launch(path, jar, outputDirectory);
  }
}
