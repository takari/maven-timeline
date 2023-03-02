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
package io.takari.maven.timeline.buildevents;

import org.apache.maven.execution.ExecutionEvent;
import org.apache.maven.execution.ExecutionListener;

public class ExecutionListenerChain implements ExecutionListener {
  private final ExecutionListener first;
  private final ExecutionListener second;

  public ExecutionListenerChain(ExecutionListener first, ExecutionListener second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public void projectDiscoveryStarted(ExecutionEvent event) {
    first.projectDiscoveryStarted(event);
    second.projectDiscoveryStarted(event);
  }

  @Override
  public void sessionStarted(ExecutionEvent event) {
    first.sessionStarted(event);
    second.sessionStarted(event);
  }

  @Override
  public void sessionEnded(ExecutionEvent event) {
    first.sessionEnded(event);
    second.sessionEnded(event);
  }

  @Override
  public void projectSkipped(ExecutionEvent event) {
    first.projectSkipped(event);
    second.projectSkipped(event);
  }

  @Override
  public void projectStarted(ExecutionEvent event) {
    first.projectStarted(event);
    second.projectStarted(event);
  }

  @Override
  public void projectSucceeded(ExecutionEvent event) {
    first.projectSucceeded(event);
    second.projectSucceeded(event);
  }

  @Override
  public void projectFailed(ExecutionEvent event) {
    first.projectFailed(event);
    second.projectFailed(event);
  }

  @Override
  public void mojoSkipped(ExecutionEvent event) {
    first.mojoSkipped(event);
    second.mojoSkipped(event);
  }

  @Override
  public void mojoStarted(ExecutionEvent event) {
    first.mojoStarted(event);
    second.mojoStarted(event);
  }

  @Override
  public void mojoSucceeded(ExecutionEvent event) {
    first.mojoSucceeded(event);
    second.mojoSucceeded(event);
  }

  @Override
  public void mojoFailed(ExecutionEvent event) {
    first.mojoFailed(event);
    second.mojoFailed(event);
  }

  @Override
  public void forkStarted(ExecutionEvent event) {
    first.forkStarted(event);
    second.forkStarted(event);
  }

  @Override
  public void forkSucceeded(ExecutionEvent event) {
    first.forkSucceeded(event);
    second.forkSucceeded(event);
  }

  @Override
  public void forkFailed(ExecutionEvent event) {
    first.forkFailed(event);
    second.forkFailed(event);
  }

  @Override
  public void forkedProjectStarted(ExecutionEvent event) {
    first.forkedProjectStarted(event);
    second.forkedProjectStarted(event);
  }

  @Override
  public void forkedProjectSucceeded(ExecutionEvent event) {
    first.forkedProjectSucceeded(event);
    second.forkedProjectSucceeded(event);
  }

  @Override
  public void forkedProjectFailed(ExecutionEvent event) {
    first.forkedProjectFailed(event);
    second.forkedProjectFailed(event);
  }
}
