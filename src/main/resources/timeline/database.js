/**
 * Created by Dominik on 10.12.2016.
 */
function TimeLineDb(timelineData) {
  db = openDatabase('mydb', '1.0', 'Test DB', 5 * 1024 * 1024);

  db.onError = function(tx, e) {
    alert("There has been an error: " + e.message);
  };

  db.transaction(function (tx) {
    tx.executeSql('DROP TABLE events');
    tx.executeSql('CREATE TABLE events (start LONG, end LONG, duration INTEGER, trackNum INTEGER, groupId TEXT, artifactId TEXT, phase TEXT, goal TEXT)');
  });

  db.transaction(function (tx) {
    for(var index = 0; index < timelineData.events.length; index++) {
      var event = timelineData.events[index];
      tx.executeSql(
        'INSERT INTO events (start, end, duration, trackNum, groupId, artifactId, phase, goal) VALUES (?,?,?,?,?,?,?,?)',
        [event.start,event.end,event.duration,event.trackNum,event.groupId,event.artifactId,event.phase,event.goal]
      );
    }
  });

  function errorHandler(transaction, sqlerror) {
    console.log(sqlerror);
  }

  this.getPhases = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select phase from events group by phase", [], renderFunc, errorHandler);
    });
  };
  this.getGoals = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select goal from events group by goal", [], renderFunc, errorHandler);
    });
  };
  this.getArtifactIds = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select artifactId from events group by artifactId", [], renderFunc, errorHandler);
    });
  };
  this.getTracks = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select trackNum from events group by trackNum", [], renderFunc, errorHandler);
    });
  };
  this.getTotalPhaseDuration = function(phase, renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select sum(duration) from events where phase is ?", [phase], function (tx, rs) {
        renderFunc(rs.rows.item(0)["sum(duration)"], phase);
      }, errorHandler);
    });
  };
  this.getTotalGoalDuration = function(goal, renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select sum(duration) from events where goal is ?", [goal], function (tx, rs) {
        renderFunc(rs.rows.item(0)["sum(duration)"], goal);
      }, errorHandler);
    });
  };
  this.getTotalArtifactDuration = function(artifactId, renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select sum(duration) from events where artifactId is ?", [artifactId], function (tx, rs) {
        renderFunc(rs.rows.item(0)["sum(duration)"], artifactId);
      }, errorHandler);
    });
  };
  this.getTotalTrackDuration = function(track, renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select sum(duration) from events where trackNum is ?", [track], function (tx, rs) {
        renderFunc(rs.rows.item(0)["sum(duration)"], track);
      }, errorHandler);
    });
  };
}
