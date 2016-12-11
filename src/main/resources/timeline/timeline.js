function TimeLine(timelineData) {

  function renderTimeLabel(currentTime, sessionStartTime, zoomFactor, rootContainer) {
    var timeLabel = document.createElement("div");
    timeLabel.setAttribute("class", "timeLabel");
    var date = new Date(currentTime);
    timeLabel.innerText = date.getUTCHours() + ":" + date.getUTCMinutes();
    if(zoomFactor < 10) {
      timeLabel.innerText = timeLabel.innerText + ":" + date.getUTCSeconds();
    }
    var left = normalize(sessionStartTime, currentTime, zoomFactor);
    var style = "left: " + left + "px;";
    timeLabel.setAttribute("style", style);
    rootContainer.appendChild(timeLabel);
  }

  this.render = function(zoomFactor) {
    // console.log(zoomFactor);
    var sessionStartTime = timelineData.start;
    var sessionEndTime = timelineData.end;

    var rootContainer = document.getElementById("timeLineContainer");
    rootContainer.innerHTML = "";

    for(var index = 0; index < timelineData.events.length; index++) {
      var event = timelineData.events[index];
      var startTime = event.start;
      var endTime = event.end;

      if(event.duration < 50) {
        continue;
      }

      var container = document.getElementById(event.trackNum);

      if(container == undefined) {
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
      addProperty(div, event, "duration");
      var left = normalize(sessionStartTime, startTime, zoomFactor);
      var width = normalize(endTime, startTime, zoomFactor);
      var style = "width: " + width + "px; left: " + left + "px;";
      div.setAttribute("style", style);
      div.setAttribute("class", "event " + event.color);
      div.setAttribute("title", event.description);
    }

    renderTimeLabel(sessionStartTime, sessionStartTime, zoomFactor, rootContainer);

    var stepSeconds = 60;

    if(zoomFactor < 10) {
      stepSeconds = 1;
    }

    for(var currentTime = sessionStartTime - (sessionStartTime % (1000*stepSeconds)); currentTime < sessionEndTime; currentTime += (1000*stepSeconds) ) {
      renderTimeLabel(currentTime, sessionStartTime, zoomFactor, rootContainer);
    }
  };

  function addProperty(container, event, propertyName) {
    var span = document.createElement("span");
    span.setAttribute("class", propertyName);
    span.innerText = event[propertyName];
    container.appendChild(span);
  }

  function normalize(absoluteStart, relativeStart, zoomFactor) {
    return Math.max(1, Math.abs ( (relativeStart - absoluteStart) / zoomFactor ));
  }
}