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
    tx.executeSql('CREATE TABLE events (start LONG, end LONG, duration INTEGER, trackNum INTEGER, groupId TEXT, artifactId TEXT, phase TEXT, goal TEXT, phaseId TEXT)');
  });

  db.transaction(function (tx) {
    for(var index = 0; index < timelineData.events.length; index++) {
      var event = timelineData.events[index];
      tx.executeSql(
        'INSERT INTO events (start, end, duration, trackNum, groupId, artifactId, phase, goal, phaseId) VALUES (?,?,?,?,?,?,?,?,?)',
        [event.start,event.end,event.duration,event.trackNum,event.groupId,event.artifactId,event.phase,event.goal,event.id]
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
  this.getTrackCount = function(renderFunc) {
    this.getTracks(function(tx, rs) {
      renderFunc(rs.rows.length);
    });
  };
  this.getTotalPhaseDuration = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select phase, sum(duration) from events group by phase order by sum(duration) desc", [], function(tx, rs) {
        for(var i = 0; i < rs.rows.length; i++) {
          renderFunc(rs.rows[i]["phase"], rs.rows[i]["sum(duration)"]);
        }
      }, errorHandler
      );
    });
  };
  this.getTotalGoalDuration = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select goal, sum(duration) from events group by goal order by sum(duration) desc", [], function(tx, rs) {
        for(var i = 0; i < rs.rows.length; i++) {
          renderFunc(rs.rows[i]["goal"], rs.rows[i]["sum(duration)"]);
        }
      }, errorHandler);
    });
  };
  this.getTotalArtifactDuration = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select artifactId, sum(duration) from events group by artifactId order by sum(duration) desc", [], function(tx, rs) {
        for(var i = 0; i < rs.rows.length; i++) {
          renderFunc(rs.rows[i]["artifactId"], rs.rows[i]["sum(duration)"]);
        }
      }, errorHandler);
    });
  };
  this.getTotalTrackDuration = function(renderFunc) {
    db.transaction(function(tx) {
      tx.executeSql("select trackNum, sum(duration) from events group by trackNum order by sum(trackNum) desc", [], function(tx, rs) {
        for(var i = 0; i < rs.rows.length; i++) {
          renderFunc(rs.rows[i]["trackNum"], rs.rows[i]["sum(duration)"]);
        }
      }, errorHandler);
    });
  };
}
