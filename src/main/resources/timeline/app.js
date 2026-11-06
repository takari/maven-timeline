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
  var tracks = timelineData.tracks || {};
  this.timeLineDb = new TimeLineDb(timelineData);
  this.timeLine = new TimeLine(timelineData);

  function elem(tagName, content) {
    var element = document.createElement(tagName);
    element.innerText = content;
    return element;
  }

  function addCard(title, cssClass) {
    var card = document.createElement("div");
    card.setAttribute("class", cssClass + " card");
    card.appendChild(elem("h2", title));
    document.getElementsByTagName("aside")[0].appendChild(card);
    return card;
  }

  function addRow(card, name, duration) {
    var row = document.createElement("div");
    row.setAttribute("data-name", name);
    row.setAttribute("data-ms", duration);
    row.appendChild(elem("span", name));
    row.appendChild(elem("span", formatTime(duration)));
    card.appendChild(row);
  }

  // Clicking a card's title toggles its rows between "by name" and "by duration" order.
  function makeSortable(card) {
    var h2 = card.querySelector("h2");
    var byName = false;
    h2.className = "sortable";
    h2.title = "Click to sort by name / duration";
    h2.addEventListener("click", function () {
      byName = !byName;
      Array.from(card.querySelectorAll("div[data-ms]"))
        .sort(function (a, b) {
          return byName
            ? a.getAttribute("data-name").localeCompare(b.getAttribute("data-name"))
            : b.getAttribute("data-ms") - a.getAttribute("data-ms");
        })
        .forEach(function (row) { card.appendChild(row); });
    });
  }

  function addStatsCards(timeLineDb) {
    var phases = addCard("Summary by phase", "summary");
    timeLineDb.getTotalPhaseDuration(function (phase, duration) { addRow(phases, phase, duration); });
    var goals = addCard("Summary by goal", "summary");
    timeLineDb.getTotalGoalDuration(function (goal, duration) { addRow(goals, goal, duration); });
    var artifacts = addCard("Summary by artifact", "summary");
    timeLineDb.getTotalArtifactDuration(function (artId, duration) { addRow(artifacts, artId, duration); });
    var trackCard = addCard("Summary by track", "summary");
    timeLineDb.getTotalTrackDuration(function (track, duration) { addRow(trackCard, tracks[track] || String(track), duration); });
    [phases, goals, artifacts, trackCard].forEach(makeSortable);
  }

  function addCriticalPath() {
    var path = timelineData.criticalPath || [];
    if (path.length < 2) {
      return; // a single module is trivially its own critical path
    }
    var card = addCard("Critical path", "summary");
    card.appendChild(elem("p", "Modules (in build order) that determined the total build time."));
    path.forEach(function (module) {
      var start = Infinity, end = 0;
      timelineData.events.forEach(function (e) {
        if (e.groupId + ":" + e.artifactId == module) {
          start = Math.min(start, e.start);
          end = Math.max(end, e.end);
        }
      });
      addRow(card, module.substring(module.indexOf(":") + 1), end - start);
    });
  }

  function addControls(zoomMin, zoomMax, zoomDefault, timeLineDb, timeLine) {
    var controlsContainer = addCard("Controls", "controls");

    var sliderContainer = document.createElement("div");
    var slider = document.createElement("input");
    slider.type = "range";
    slider.id = "zoomSlider";
    slider.min = zoomMin;
    slider.max = zoomMax;
    slider.step = 1;
    slider.value = zoomDefault;
    slider.addEventListener("change", function () {
      timeLine.render(Number(slider.value));
    });
    sliderContainer.appendChild(elem("span", "Zoom"));
    sliderContainer.appendChild(slider);
    controlsContainer.appendChild(sliderContainer);

    var minDurationLabel = elem("label", "Hide events shorter than (ms) ");
    var minDuration = document.createElement("input");
    minDuration.type = "number";
    minDuration.min = 0;
    minDuration.value = MIN_DURATION_MS;
    minDuration.addEventListener("change", function () {
      MIN_DURATION_MS = Math.max(0, Number(minDuration.value) || 0);
      timeLine.render(Number(slider.value));
    });
    minDurationLabel.appendChild(minDuration);
    controlsContainer.appendChild(minDurationLabel);

    var legendElement = document.createElement("legend");
    legendElement.innerText = "Show/Hide labels";
    controlsContainer.appendChild(legendElement);

    appendCssClassToggle(controlsContainer, "groupId", false);
    appendCssClassToggle(controlsContainer, "artifactId", true);
    appendCssClassToggle(controlsContainer, "mojoGroupId", false);
    appendCssClassToggle(controlsContainer, "mojoArtifactId", false);
    appendCssClassToggle(controlsContainer, "goal", false);
    appendCssClassToggle(controlsContainer, "phase", true);
    appendCssClassToggle(controlsContainer, "id", false);
    appendCssClassToggle(controlsContainer, "duration", true);

    if ((timelineData.criticalPath || []).length > 1) {
      legendElement = document.createElement("legend");
      legendElement.innerText = "Highlight";
      controlsContainer.appendChild(legendElement);
      appendToggle(controlsContainer, "critical path",
        "#timeLineContainer .event.critical { border-bottom: 4px solid #1565c0; }");
    }

    legendElement = document.createElement("legend");
    legendElement.innerText = "Highlight by phase";
    controlsContainer.appendChild(legendElement);

    timeLineDb.getTopPhases(HIGHLIGHTED_ITEMS, function(rows) {
      for (var i = 0; i < rows.length; i++) {
        appendHighlightToggle(controlsContainer, "phase-", rows[i]["phase"], i % window.highlightColorTheme.length);
      }

      legendElement = document.createElement("legend");
      legendElement.innerText = "Highlight by goal";
      controlsContainer.appendChild(legendElement);

      timeLineDb.getTopGoals(HIGHLIGHTED_ITEMS, function(rows) {
        for (var i = 0; i < rows.length; i++) {
          var themeIndex = i % window.highlightColorTheme.length;
          appendHighlightToggle(controlsContainer, "goal-", rows[i]["goal"], window.highlightColorTheme.length - 1 - themeIndex);
        }
      });
    });
  }

  // All toggles feed one generated stylesheet, so their state survives re-rendering on zoom.
  // A checkbox with data-css adds that CSS while checked; one without hides its label class while unchecked.
  function updateToggleStyles() {
    var style = document.getElementById("toggleStyles");
    if (style == null) {
      style = document.createElement("style");
      style.id = "toggleStyles";
      document.head.appendChild(style);
    }
    var css = "";
    document.querySelectorAll(".controls input[type=checkbox]").forEach(function (input) {
      if (input.hasAttribute("data-css")) {
        if (input.checked) {
          css += input.getAttribute("data-css") + "\n";
        }
      } else if (!input.checked) {
        css += "#timeLineContainer ." + CSS.escape(input.getAttribute("data-title")) + " { display: none; }\n";
      }
    });
    style.textContent = css;
  }

  function appendToggle(controlsContainer, title, css, checked) {
    var label = document.createElement("label");
    var input = document.createElement("input");
    input.type = "checkbox";
    input.checked = !!checked;
    input.setAttribute("data-title", title);
    if (css) {
      input.setAttribute("data-css", css);
    }
    input.addEventListener("change", updateToggleStyles);
    label.appendChild(input);
    label.appendChild(document.createTextNode(title.replace(/^(phase|goal)-/, "")));
    controlsContainer.appendChild(label);
    updateToggleStyles();
    return label;
  }

  function appendCssClassToggle(controlsContainer, className, enabled) {
    appendToggle(controlsContainer, className, null, enabled);
  }

  function appendHighlightToggle(controlsContainer, classPrefix, className, index) {
    var color = window.highlightColorTheme[index];
    var label = appendToggle(controlsContainer, classPrefix + className,
      "#timeLineContainer ." + CSS.escape(classPrefix + className) + ".event { background-color: " + color + " !important; }");
    label.setAttribute("class", "highlightingBox");
    label.setAttribute("style", "background-color: " + color + "; border-color: " + color);
  }

  function addRankings(timeLineDb) {
    var card = addCard("Top 10 artifacts", "ranking");
    timeLineDb.getTopArtifacts(10, function (groupId, artId, phase, goal, duration) {
      addRow(card, artId + ":" + phase + ":" + goal, duration);
    });
    makeSortable(card);
  }

  this.run = function() {
    var zoomMin = 1;
    var zoomMax = Math.max(zoomMin, (timelineData.end - timelineData.start) / 1000);
    var zoomDefault = Math.max(zoomMin, zoomMax / 3);

    this.timeLine.render(zoomDefault);

    addControls(zoomMin, zoomMax, zoomDefault, this.timeLineDb, this.timeLine);
    addCriticalPath();
    addStatsCards(this.timeLineDb);
    addRankings(this.timeLineDb);
  };
}

function formatTime(duration) {
  if(duration < 100) {
    return duration + " ms";
  }
  if(duration < 1000 * 60) {
    return Number((duration/1000).toFixed(1)) + " s";
  }
  return Math.floor(duration / (1000 * 60)) + " min " + Math.floor(((duration % (60 * 1000)) / 1000)) + " s";
}
