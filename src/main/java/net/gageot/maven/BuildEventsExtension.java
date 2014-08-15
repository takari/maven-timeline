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
package net.gageot.maven;

import java.io.*;

import net.gageot.maven.buildevents.*;

import org.apache.maven.*;
import org.apache.maven.execution.*;
import org.codehaus.plexus.component.annotations.*;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "buildevents")
public class BuildEventsExtension extends AbstractMavenLifecycleParticipant {
  private static final String OUTPUT_FILE = "execution.metrics.output.file";
  private static final String DEFAULT_FILE_DESTINATION = "target/execution-metrics.json";

  @Override
  public void afterProjectsRead(MavenSession session) {
    MavenExecutionRequest request = session.getRequest();

    ExecutionListener original = request.getExecutionListener();
    BuildEventListener listener = new BuildEventListener(logFile(session));
    ExecutionListener chain = new ExecutionListenerChain(original, listener);

    request.setExecutionListener(chain);
  }

  private File logFile(MavenSession session) {
    String path = session.getUserProperties().getProperty(OUTPUT_FILE, DEFAULT_FILE_DESTINATION);
    if (new File(path).isAbsolute()) {
      return new File(path);
    }

    String buildDir = session.getExecutionRootDirectory();
    return new File(buildDir, path);
  }
}
