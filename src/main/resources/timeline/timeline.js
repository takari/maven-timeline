/**
 * Minimum size of the cards in the UI
 */
var SIZE_LIMIT = 20;

/**
 * Minimum event duration to be displayed
 */
var MIN_DURATION_MS = 50;

function TimeLine(timelineData) {

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
      if(date.getUTCMinutes() % 5 != 0) return;
    }
    else if(zoomFactor > 1250) {
      if(date.getUTCMinutes() % 4 != 0) return;
    }
    else if(zoomFactor > 750) {
      if(date.getUTCMinutes() % 2 != 0) return;
    }

    timeLabel.innerText = twoDigits(date.getUTCHours()) + ":" + twoDigits(date.getUTCMinutes());
    durationLabel.innerText = twoDigits(durationTime.getUTCHours()) + ":" + twoDigits(durationTime.getUTCMinutes());
    if(zoomFactor < 10) {
      timeLabel.innerText = timeLabel.innerText + ":" + twoDigits(date.getUTCSeconds());
      durationLabel.innerText = durationTime.innerText + ":" + twoDigits(durationTime.getUTCSeconds());
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
      event.artifactId + ":" +
      event.artifactId + ":" +
      event.phase + ":" +
      event.goal + ":" +
      event.id + "(" +
      formatTime(event.duration) + ")";
  }

  this.render = function(zoomFactor) {
    console.log(zoomFactor);
    var sessionStartTime = timelineData.start;
    var sessionEndTime = timelineData.end;

    var rootContainer = document.getElementById("timeLineContainer");
    rootContainer.innerHTML = "";

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
        rootContainer.appendChild(container);
      }

      var div = document.createElement("div");
      container.appendChild(div);
      addProperty(div, event, "groupId");
      addProperty(div, event, "artifactId");
      addProperty(div, event, "phase");
      addProperty(div, event, "goal");
      if (event.id.indexOf("default-" != 0)) {
        addProperty(div, event, "id");
      }
      addProperty(div, event, "duration");
      var left = normalize(sessionStartTime, startTime, zoomFactor);

      var style = "width: " + width + "px; left: " + left + "px;";
      div.setAttribute("style", style);
      div.setAttribute("class",
        "event " +
        event.color + " " +
        "phase-" + event.phase + " " +
        "goal-" + event.goal + " " +
        "artifactId-" + event.artifactId + " " +
        "id-" + event.id)
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
