/**
 * Created by Dominik on 10.12.2016.
 */
function TimeLineDb(timelineData) {

  this.getTopPhases = function(limit, renderFunc) {
    // "select phase from events group by phase order by sum(duration) desc LIMIT ?", [limit]
    renderFunc(Object.entries(timelineData.events
      .map(({phase, duration}) => ({phase, duration}))
      .reduce((acc, {phase, duration}) => ({...acc, [phase]: (acc[phase] || 0) + duration}), {}))
      .sort((a, b) => b[1] - a[1])
      .map(([phase]) => ({phase}))
      .slice(0, limit));
  };
  this.getTopGoals = function(limit, renderFunc) {
    // "select goal from events group by goal order by sum(duration) desc LIMIT ?", [limit]
    renderFunc(Object.entries(timelineData.events
      .map(({goal, duration}) => ({goal, duration}))
      .reduce((acc, {goal, duration}) => ({...acc, [goal]: (acc[goal] || 0) + duration}), {}))
      .sort((a, b) => b[1] - a[1])
      .map(([goal]) => ({goal}))
      .slice(0, limit));
  };
  this.getTopArtifacts = function(limit, renderFunc) {
    // "select groupId, artifactId, phase, goal, duration from events order by duration desc limit ?", [limit]
    timelineData.events
      .sort((a, b) => b.duration - a.duration)
      .slice(0, limit)
      .forEach(e => renderFunc(e.groupId, e.artifactId, e.phase, e.goal, e.duration));
  };
  this.getTotalPhaseDuration = function(renderFunc) {
    // "select phase, sum(duration) from events group by phase order by sum(duration) desc"
    Object.entries(timelineData.events
      .map(({phase, duration}) => ({phase, duration}))
      .reduce((acc, {phase, duration}) => ({...acc, [phase]: (acc[phase] || 0) + duration}), {}))
      .sort((a, b) => b[1] - a[1])
      .forEach(([phase, duration]) => renderFunc(phase, duration));
  };
  this.getTotalGoalDuration = function(renderFunc) {
    // "select goal, sum(duration) from events group by goal order by sum(duration) desc"
    Object.entries(timelineData.events
      .map(({goal, duration}) => ({goal, duration}))
      .reduce((acc, {goal, duration}) => ({...acc, [goal]: (acc[goal] || 0) + duration}), {}))
      .sort((a, b) => b[1] - a[1])
      .forEach(([goal, duration]) => renderFunc(goal, duration));
  };
  this.getTotalArtifactDuration = function(renderFunc) {
    // "select artifactId, sum(duration) from events group by artifactId order by sum(duration) desc"
    Object.entries(timelineData.events
      .map(({artifactId, duration}) => ({artifactId, duration}))
      .reduce((acc, {artifactId, duration}) => ({...acc, [artifactId]: (acc[artifactId] || 0) + duration}), {}))
      .sort((a, b) => b[1] - a[1])
      .forEach(([artifactId, duration]) => renderFunc(artifactId, duration));
  };
  this.getTotalTrackDuration = function(renderFunc) {
    // "select trackNum, sum(duration) from events group by trackNum order by sum(trackNum) desc"
    Object.entries(timelineData.events
      .map(({trackNum, duration}) => ({trackNum, duration}))
      .reduce((acc, {trackNum, duration}) => ({...acc, [trackNum]: (acc[trackNum] || 0) + duration}), {}))
      .sort((a, b) => b[1] - a[1])
      .forEach(([trackNum, duration]) => renderFunc(trackNum, duration));
  };
}
