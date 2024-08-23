var HIGHLIGHTED_ITEMS = 10;

window.highlightColorTheme = [
  "#ef5350",
  "#ba68c8",
  "#7986cb",
  "#4fc3f7",
  "#81c784",
  "#d4e157",
  "#fff176",
  "#f48fb1",
  "#827717",
  "#a1887f",
  "#90a4ae",
  "#ff5722",
  "#ffc107"
];

function TimeLineApp() {
  var timelineData = window.timelineData;
  if (timelineData == undefined) {
    $.ajax({
      url: "maven-timeline.json",
      dataType: "json",
      async: false
    }).done(function (data) {
      timelineData = data
    });
  }
  this.timeLineDb = new TimeLineDb(timelineData);
  this.timeLine = new TimeLine(timelineData);

  function elem(tagName, content) {
    var element = document.createElement(tagName);
    element.innerText = content;
    return element;
  }

  function addStatsCards(timeLineDb) {
    var cardTitles = ["Summary by phase", "Summary by goal", "Summary by artifact", "Summary by track"];

    for (var i = 0; i < cardTitles.length; i++) {
      var summary = document.createElement("div");
      summary.setAttribute("class", "summary card");
      var h2 = document.createElement("h2");
      h2.innerText = cardTitles[i];
      summary.appendChild(h2);
      document.getElementsByTagName("aside")[0].appendChild(summary);
    }

    timeLineDb.getTotalPhaseDuration(function (phase, duration) {
      var container = document.createElement("div");
      container.appendChild(elem("span", phase));
      container.appendChild(elem("span", formatTime(duration)));
      document.getElementsByClassName("summary")[0].appendChild(container);
    });
    timeLineDb.getTotalGoalDuration(function (goal, duration) {
      var container = document.createElement("div");
      container.appendChild(elem("span", goal));
      container.appendChild(elem("span", formatTime(duration)));
      document.getElementsByClassName("summary")[1].appendChild(container);
    });
    timeLineDb.getTotalArtifactDuration(function (artId, duration) {
      var container = document.createElement("div");
      container.appendChild(elem("span", artId));
      container.appendChild(elem("span", formatTime(duration)));
      document.getElementsByClassName("summary")[2].appendChild(container);
    });
    timeLineDb.getTotalTrackDuration(function (track, duration) {
      var container = document.createElement("div");
      container.appendChild(elem("span", track));
      container.appendChild(elem("span", formatTime(duration)));
      document.getElementsByClassName("summary")[3].appendChild(container);
    });
  }

  function addControls(zoomMin, zoomMax, zoomDefault, timeLineDb, timeLine) {
    var controlsContainer = document.createElement("div");
    controlsContainer.setAttribute("class", "controls card");
    var h2 = document.createElement("h2");
    h2.innerText = "Controls";
    controlsContainer.appendChild(h2);
    document.getElementsByTagName("aside")[0].appendChild(controlsContainer);

    var sliderContainer = document.createElement("div");
    var slider = document.createElement("div");
    slider.setAttribute("id", "zoomSlider");
    sliderContainer.appendChild(elem("span", "Zoom"));
    sliderContainer.appendChild(slider);

    var legendElement = document.createElement("legend");
    legendElement.innerText = "Show/Hide labels";
    controlsContainer.appendChild(sliderContainer);
    controlsContainer.appendChild(legendElement);
    $(function () {
      $("#zoomSlider").slider({
        min: zoomMin, max: zoomMax, step: 1, value: zoomDefault,
        change: function (ev, ui) {
          timeLine.render(ui.value);
        }
      });
    });

    appendCssClassToggle(controlsContainer, "groupId", false);
    appendCssClassToggle(controlsContainer, "artifactId", true);
    appendCssClassToggle(controlsContainer, "goal", false);
    appendCssClassToggle(controlsContainer, "phase", true);
    appendCssClassToggle(controlsContainer, "id", false);
    appendCssClassToggle(controlsContainer, "duration", true);

    legendElement = document.createElement("legend");
    legendElement.innerText = "Highlight by phase";
    controlsContainer.appendChild(legendElement);

    timeLineDb.getTopPhases(HIGHLIGHTED_ITEMS, function(rows) {
      var themeIndex = 0;
      for(var i = 0; i < rows.length; i++, themeIndex++) {
        if(themeIndex >= (window.highlightColorTheme.length)) {
          themeIndex = 0;
        }
        appendHighlightToggle(controlsContainer, "phase-", rows[i]["phase"], themeIndex);
      }

      legendElement = document.createElement("legend");
      legendElement.innerText = "Highlight by goal";
      controlsContainer.appendChild(legendElement);

      timeLineDb.getTopGoals(HIGHLIGHTED_ITEMS, function(rows) {
        var themeIndex = 0;
        for(var i = 0; i < rows.length; i++, themeIndex++) {
          if(themeIndex >= (window.highlightColorTheme.length)) {
             themeIndex = 0;
          }
          appendHighlightToggle(controlsContainer, "goal-", rows[i]["goal"], window.highlightColorTheme.length - 1 - themeIndex);
        }
      });
    });
  }

  function appendCssClassToggle(controlsContainer, className, enabled) {
    var label = document.createElement("label");
    var input = document.createElement("input");
    var name = "checkbox-nested-" + className;

    input.setAttribute("type", "checkbox");
    input.setAttribute("name", name);
    input.setAttribute("id", name);
    input.setAttribute("data-title", className);
    label.setAttribute("for", name);

    if(enabled != undefined && enabled != false) {
      input.setAttribute("checked", true);
    }
    //else {
    //  document.styleSheets[0].addRule("." + className, "display: none;");
    //}

    label.innerText = className;
    label.appendChild(input);
    controlsContainer.appendChild(label);

    $(function () {
      $("#" + name).checkboxradio({
        icon: false,
        value: enabled
      });
    });
    $("#" + name).on("change", function (e) {
      var attribute = e.target.getAttribute("data-title");

      if (e.target.checked == true) {
        $("." + attribute).show();
      }
      else {
        $("." + attribute).hide();
      }
    });
  }

  function appendHighlightToggle(controlsContainer, classPrefix, className, index) {
    var label = document.createElement("label");
    var input = document.createElement("input");
    var name = "checkbox-nested-" + classPrefix + className;

    input.setAttribute("type", "checkbox");
    input.setAttribute("name", name);
    input.setAttribute("id", name);
    input.setAttribute("data-title", classPrefix+className);
    input.setAttribute("data-index", index);
    label.setAttribute("for", name);
    label.setAttribute("class", "highlightingBox");
    label.setAttribute("style", "background-color: " + window.highlightColorTheme[index] + "; border-color: " + window.highlightColorTheme[index]);

    label.innerText = className;
    label.appendChild(input);
    controlsContainer.appendChild(label);

    //document.styleSheets[0].addRule(".event" + "." + classPrefix+className + ":hover",
    //  "background-color: " + window.highlightColorTheme[index] + "; transition: background-color .5s;"
    //);

    $(function () {
      $("#" + name).checkboxradio({
        icon: false,
        value: false
      });
    });
    $("#" + name).on("change", function (e) {
      var attribute = e.target.getAttribute("data-title");
      var index = e.target.getAttribute("data-index");

      var sheet = document.styleSheets[attribute];
      if(sheet == undefined) {
        document.styleSheets[attribute] = (function() {
          var style = document.createElement("style");
          style.appendChild(document.createTextNode(""));
          document.head.appendChild(style);
          return style.sheet;
        })();
        sheet = document.styleSheets[attribute];
      }
      if (e.target.checked == true) {
        sheet.addRule("." + attribute + ".event", "background-color: " + window.highlightColorTheme[index] + " !important");
      }
      else {
        sheet.removeRule(0);
      }
    });
  }

  function addRankings(timeLineDb) {
    var controlsContainer = document.createElement("div");
    controlsContainer.setAttribute("class", "ranking card");
    var h2 = document.createElement("h2");
    h2.innerText = "Top 10 artifacts";
    controlsContainer.appendChild(h2);
    document.getElementsByTagName("aside")[0].appendChild(controlsContainer);

    timeLineDb.getTopArtifacts(10, function (groupId, artId, phase, goal, duration) {
      var container = document.createElement("div");
      container.appendChild(elem("span", artId + ":" + phase + ":" + goal));
      container.appendChild(elem("span", formatTime(duration)));
      document.getElementsByClassName("ranking")[0].appendChild(container);
    });
  }

  this.run = function() {
    var zoomMin = 1;
    var zoomMax = Math.max(zoomMin, (timelineData.end - timelineData.start) / 1000);
    var zoomDefault = Math.max(zoomMin, zoomMax / 3);

    this.timeLine.render(zoomDefault);

    addControls(zoomMin, zoomMax, zoomDefault, this.timeLineDb, this.timeLine);
    addStatsCards(this.timeLineDb);
    addRankings(this.timeLineDb)
  }
}

function formatTime(duration) {
  if(duration < 100) {
    return duration + " ms";
  }
  if(duration < 1000 * 60) {
    return Number((duration/1000).toFixed(1)) + " s";
  }
  // if(duration < 1000 * 60 * 60) {
    return Math.floor(duration / (1000 * 60)) + " min " + Math.floor(((duration % (60 * 1000)) / 1000)) + " s";
  // }
}
