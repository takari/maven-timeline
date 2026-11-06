/**
 * Created by Dominik on 10.12.2016.
 */
function TimeLineDb(timelineData) {

  // [[value, totalDuration], ...] grouped by event[key], longest first
  function sumBy(key) {
    var totals = new Map();
    timelineData.events.forEach(function (e) {
      totals.set(e[key], (totals.get(e[key]) || 0) + e.duration);
    });
    return Array.from(totals.entries()).sort(function (a, b) { return b[1] - a[1]; });
  }

  this.getTopPhases = function(limit, renderFunc) {
    renderFunc(sumBy("phase").slice(0, limit).map(function (r) { return {phase: r[0]}; }));
  };
  this.getTopGoals = function(limit, renderFunc) {
    renderFunc(sumBy("goal").slice(0, limit).map(function (r) { return {goal: r[0]}; }));
  };
  this.getTopArtifacts = function(limit, renderFunc) {
    timelineData.events.slice() // do not reorder the shared events array
      .sort(function (a, b) { return b.duration - a.duration; })
      .slice(0, limit)
      .forEach(function (e) { renderFunc(e.groupId, e.artifactId, e.phase, e.goal, e.duration); });
  };
  this.getTotalPhaseDuration = function(renderFunc) {
    sumBy("phase").forEach(function (r) { renderFunc(r[0], r[1]); });
  };
  this.getTotalGoalDuration = function(renderFunc) {
    sumBy("goal").forEach(function (r) { renderFunc(r[0], r[1]); });
  };
  this.getTotalArtifactDuration = function(renderFunc) {
    sumBy("artifactId").forEach(function (r) { renderFunc(r[0], r[1]); });
  };
  this.getTotalTrackDuration = function(renderFunc) {
    sumBy("trackNum").forEach(function (r) { renderFunc(r[0], r[1]); });
  };
}
