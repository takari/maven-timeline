function TimeLine(timelineData) {
  this.render = function(zoomFactor) {
    var sessionStartTime = timelineData.start;
    var sessionEndTime = timelineData.end;

    for(var index = 0; index < timelineData.events.length; index++) {
      var event = timelineData.events[index];
      var startTime = event.start;
      var endTime = event.end;

      var container = document.getElementById(event.trackNum);

      if(container == undefined) {
        container = document.createElement("div");
        container.setAttribute("id", event.trackNum);
        container.setAttribute("class", "track");
        document.getElementsByTagName("main")[0].appendChild(container);
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
