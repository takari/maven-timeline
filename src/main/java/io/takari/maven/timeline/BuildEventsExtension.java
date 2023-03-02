/**
 * Copyright (C) 2013 david@gageot.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package io.takari.maven.timeline;

import javax.inject.Named;
import javax.inject.Singleton;

import io.takari.maven.timeline.buildevents.BuildEventListener;
import io.takari.maven.timeline.buildevents.ExecutionListenerChain;

import java.io.File;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.execution.ExecutionListener;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

@Singleton
@Named("buildevents")
public class BuildEventsExtension extends AbstractMavenLifecycleParticipant {
  private static final String OUTPUT_FILE = "execution.metrics.output.file";
  private static final String DEFAULT_FILE_DESTINATION = "target/execution-metrics.json";

  @Override
  public void afterProjectsRead(MavenSession session) {
    MavenExecutionRequest request = session.getRequest();
    ExecutionListener original = request.getExecutionListener();
    BuildEventListener listener = new BuildEventListener(logFile(session), mavenTimelineFile(session), session.getCurrentProject().getArtifactId(), session.getCurrentProject().getGroupId());
    ExecutionListener chain = new ExecutionListenerChain(original, listener);
    request.setExecutionListener(chain);
  }

  private File mavenTimelineFile(MavenSession session) {
    return new File(session.getExecutionRootDirectory(), "target/timeline/maven-timeline.js");
  }

  private File logFile(MavenSession session) {
    String path = session.getUserProperties().getProperty(OUTPUT_FILE, DEFAULT_FILE_DESTINATION);
    if (new File(path).isAbsolute()) {
      return new File(path);
    }
    String buildDir = session.getExecutionRootDirectory();
    return new File(buildDir, path);
  }

  public static void main(String[] args) {
    String output = new DateTime(DateTimeZone.UTC).toString();
    System.out.println(output);
  }
}
