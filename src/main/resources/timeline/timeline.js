/**
 * Minimum size of the cards in the UI
 */
var SIZE_LIMIT = 20;

/**
 * Minimum event duration to be displayed
 */
var MIN_DURATION_MS = 50;

var EVENT_STYLES = ["odd", "even"];

function TimeLine(timelineData) {
  var critical = new Set(timelineData.criticalPath || []);
  var tracks = timelineData.tracks || {};

  addProperty(document.getElementsByTagName("header")[0], timelineData, "groupId");
  addProperty(document.getElementsByTagName("header")[0], timelineData, "artifactId");

  function twoDigits(num) {
    if(num == undefined || Number.isNaN(num)) return "00";
    if(num < 10) return "0" + num;
    else return num;
  }

  function renderTimeLabel(currentTime, sessionStartTime, zoomFactor, rootContainer) {
    var timeLabel = document.createElement("div");
    var durationLabel = document.createElement("div");

    timeLabel.setAttribute("class", "timeLabel");
    durationLabel.setAttribute("class", "durationLabel");

    var date = new Date(currentTime);
    var durationTime = new Date(currentTime - sessionStartTime);

    if(zoomFactor > 2000) {
      if(date.getMinutes() % 5 != 0) return;
    }
    else if(zoomFactor > 1250) {
      if(date.getMinutes() % 4 != 0) return;
    }
    else if(zoomFactor > 750) {
      if(date.getMinutes() % 2 != 0) return;
    }

    timeLabel.innerText = twoDigits(date.getHours()) + ":" + twoDigits(date.getMinutes());
    durationLabel.innerText = twoDigits(durationTime.getUTCHours()) + ":" + twoDigits(durationTime.getUTCMinutes());
    if(zoomFactor < 10) {
      timeLabel.innerText = timeLabel.innerText + ":" + twoDigits(date.getSeconds());
      durationLabel.innerText = durationLabel.innerText + ":" + twoDigits(durationTime.getUTCSeconds());
    }
    var left = normalize(sessionStartTime, currentTime, zoomFactor);

    var style = "left: " + left + "px;";
    timeLabel.setAttribute("style", style);
    durationLabel.setAttribute("style", style);

    rootContainer.appendChild(timeLabel);
    rootContainer.appendChild(durationLabel);
  }

  function description(event) {
    return event.groupId + ":" +
      event.artifactId + " | " +
      event.mojoGroupId + ":" +
      event.mojoArtifactId + " | " +
      event.phase + ":" +
      event.goal + ":" +
      event.id + "(" +
      formatTime(event.duration) + ")" +
      (event.failed ? " FAILED" : "");
  }

  this.render = function(zoomFactor) {
    var sessionStartTime = timelineData.start;
    var sessionEndTime = timelineData.end;

    var rootContainer = document.getElementById("timeLineContainer");
    rootContainer.innerHTML = "";

    var styleIndexPerTrack = [];

    for(var index = 0; index < timelineData.events.length; index++) {
      var event = timelineData.events[index];
      var startTime = event.start;
      var endTime = event.end;

      var width = normalize(endTime, startTime, zoomFactor);

      if (event.duration < MIN_DURATION_MS || width < SIZE_LIMIT) {
        continue;
      }

      var container = document.getElementById(event.trackNum);

      if (container == undefined) {
        container = document.createElement("div");
        container.setAttribute("id", event.trackNum);
        container.setAttribute("class", "track");
        container.setAttribute("title", tracks[event.trackNum] || "");
        rootContainer.appendChild(container);
      }

      var div = document.createElement("div");
      container.appendChild(div);
      addProperty(div, event, "groupId");
      addProperty(div, event, "artifactId");
      addProperty(div, event, "mojoGroupId");
      addProperty(div, event, "mojoArtifactId");
      addProperty(div, event, "phase");
      addProperty(div, event, "goal");
      if (event.id && event.id.indexOf("default-") != 0) {
        addProperty(div, event, "id");
      }
      addProperty(div, event, "duration");
      var left = normalize(sessionStartTime, startTime, zoomFactor);

      var styleIndex = styleIndexPerTrack[event.trackNum];
      if(styleIndex == undefined) {
        styleIndex = 0;
      }
      var eventColor = EVENT_STYLES[styleIndex];
      styleIndex = (styleIndex + 1) % EVENT_STYLES.length;
      styleIndexPerTrack[event.trackNum] = styleIndex;

      var style = "width: " + width + "px; left: " + left + "px;";
      div.setAttribute("style", style);
      div.setAttribute("class",
        "event " +
        eventColor + " " +
        "phase-" + event.phase + " " +
        "goal-" + event.goal + " " +
        "artifactId-" + event.artifactId + " " +
        "id-" + event.id +
        (critical.has(event.groupId + ":" + event.artifactId) ? " critical" : "") +
        (event.failed ? " failed" : ""))
      ;
      div.setAttribute("title", description(event));
    }

    renderTimeLabel(sessionStartTime, sessionStartTime, zoomFactor, rootContainer);

    var stepSeconds = 60;

    if(zoomFactor < 10) {
      stepSeconds = 1;
    }

    for(var currentTime = sessionStartTime - (sessionStartTime % (1000*stepSeconds)) + (1000*stepSeconds); currentTime < sessionEndTime; currentTime += (1000*stepSeconds) ) {
      renderTimeLabel(currentTime, sessionStartTime, zoomFactor, rootContainer);
    }
  };

  function addProperty(container, event, propertyName) {
    var span = document.createElement("span");
    span.setAttribute("class", propertyName);
    if(propertyName == "duration") {
      span.innerText = formatTime(event[propertyName]);
    }
    else {
      span.innerText = event[propertyName];
    }
    container.appendChild(span);
  }

  function normalize(absoluteStart, relativeStart, zoomFactor) {
    return Math.max(1, Math.abs ( (relativeStart - absoluteStart) / zoomFactor ));
  }
}
