# SpyJ

A data analytics plugin for collecting, formatting and analysing telemetry data produced using the BlueJ coding editor.

---

## Introduction

### Project Purpose

SpyJ is a BlueJ IDE extension built to passively collect and report on student coding behaviour. Without interrupting the student's workflow, SpyJ silently observes each session — capturing project and class lifecycle events, editor typing patterns, compilation attempts, and program executions — then produces a structured JSON export and a human-readable PDF report the moment BlueJ closes. The goal is to give insight into *how* students write code, not just *what* they produce: how often they compile, how they recover from errors, how long they spend typing versus idling.

---

### Project Scope

SpyJ captures four categories of BlueJ events: **project/class lifecycle** actions (create, open, modify, delete, close), **editor interactions** (typing bursts, idle time, keystroke cadence), **compilation events** (success/failure, error codes and line numbers, inter-compile intervals), and **program executions** (normal exits and runtime exceptions). Student privacy is protected by deliberately excluding source code content, raw keystrokes, and any activity outside BlueJ.

At session end, SpyJ exports a **timestamped JSON file** (with full event details and session-level aggregates) and a **two-page PDF report** (session summary stats, compilation success rate, activity charts, class/method timeline, and error frequency table). All event data is held in-memory during the session; nothing is written to disk until BlueJ shuts down.

---

### Team

| Name | Student ID | Role |
|------|------------|------|
| Mykel Dedier | 816042057 | Extension Entry Point, Event Capture & Data Logging |
| Caitlyn Maharaj | 816039787 | Data Formatting & Export |
| Aidan Ramnarine | | Unit Testing & Quality Assurance |

---

### Roles & Contributions

#### Mykel Dedier — Event Capture & Data Logging

Designed and implemented the core telemetry data capture system. This includes the **Logger** and **Listener** packages using the Observer pattern. Also implemented the main `SpyJ` extension entry point and plugin lifecycle management.

#### Caitlyn Maharaj — Data Formatting & Export

Implemented the **Format** package. This covers the `LogFormatter` strategy interface, `JSONFormatter` (Gson-based event serialisation), `PDFFormatter` (Apache PDFBox two-page report with charts and tables), `SessionSummaryBuilder` (stats-to-report-fields mapping), and the Command-pattern export structure (`Command`, `ExportCommand`, `CommandInvoker`, `ReportExporter`).

#### Aidan Ramnarine — Unit Testing & Quality Assurance

Designed and implemented the automated **test suite** using JUnit 5 and Mockito. Configured JaCoCo for code coverage reporting and established the project's quality assurance target of ≥ 75% coverage. Test classes include `SpyJLoggerTest`, `EventStoreTest`, `StatsTrackerTest`, `ClassListenerTest`, `CompilationListenerTest`, and `ExecutionListenerTest`.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Entry Point — `SpyJ`](#entry-point--spyj)
- [Logger Package](#logger-package)
  - [SpyJLogger](#spyjlogger)
  - [EventSubscriber](#eventsubscriber)
  - [TelemetryEvent](#telemetryevent)
  - [EventType](#eventtype)
  - [DetailsKey](#detailskey)
  - [EventStore](#eventstore)
  - [StatsTracker](#statstracker)
- [Listener Package](#listener-package)
  -- [ProjectListener](#projectlistener)
  - [ClassListener](#classlistener)
  - [CompilationListener](#compilationlistener)
  - [ExecutionListener](#executionlistener)
  - [EditorListener](#editorlistener)
- [Formatting and Export Package](#formatting-and-export-package)
  - [LogFormatter](#logformatter)
  - [JSONFormatter](#jsonformatter)
  - [PDFFormatter](#pdfformatter)
  - [SessionSummaryBuilder](#sessionsummarybuilder)
  - [Command](#command)
  - [ExportCommand](#exportcommand)
  - [CommandInvoker](#commandinvoker)
  - [ReportExporter](#reportexporter)
  - [FormattingDemo](#formattingdemo)
- [Unit Testing & Quality Assurance](#unit-testing--quality-assurance)
  - [Testing Architecture](#testing-architecture)
  - [Test Suites](#test-suites)
    - [Logger Tests](#logger-tests)
    - [Listener Tests](#listener-tests)
  - [Code Coverage (JaCoCo)](#code-coverage-jacoco) 

---

## Overview

SpyJ is a [BlueJ](https://www.bluej.org/) Extension (Extensions API v2) that observes a student's coding session and collects structured telemetry data. When BlueJ shuts down, SpyJ exports a session report (JSON + PDF) that should describe how the student worked: what projects and classes were touched, how much they typed, how often they compiled, and what errors they encountered.

---

## Architecture

![Project Diagram](src/main/resources/ProjectStarUML.png)

<img src="src/main/resources/ProjectGeneratedDiagram.png" alt="Project Diagram" width="850" />


---

## Entry Point — `SpyJ`

**File:** `src/main/java/com/spyj/SpyJ.java`

`SpyJ` extends `bluej.extensions2.Extension` and serves as the plugin's lifecycle manager. BlueJ instantiates it automatically when the extension is loaded.

### Lifecycle Methods

| Method | Description |
|--------|-------------|
| `startup(BlueJ bluej)` | Called by BlueJ when the extension is loaded. Initialises all core components (`SpyJLogger`, `EventStore`, `StatsTracker`, `ReportExporter`) and all five listeners, then registers each listener with the BlueJ API. Records the session start time. |
| `terminate()` | Called by BlueJ on shutdown. Flushes any in-progress editor burst via `EditorListener.stop()`, then calls `ReportExporter.export(...)` if there are events to report. Guards against double-export with the `reportGenerated` flag. |
| `isCompatible()` | Returns `true` when the BlueJ Extensions API major version is ≥ 3. |
| `getVersion()` | Returns the plugin version string (`"1.0.0"`). |
| `getName()` | Returns `"SpyJ"`. |
| `getDescription()` | Returns a human-readable description of the plugin. |

### Fields

| Field | Type | Purpose |
|-------|------|---------|
| `sJLogger` | `SpyJLogger` | Central event logger; shared by all listeners. |
| `eventStore` | `EventStore` | Subscriber that persists every raw event for the report. |
| `statsTracker` | `StatsTracker` | Subscriber that maintains live aggregated statistics. |
| `reportExporter` | `ReportExporter` | Writes the final JSON/PDF report on session end. |
| `sessionStart` | `Instant` | The timestamp captured at plugin startup, used to name the session and to measure total session duration. |
| `reportGenerated` | `boolean` | Prevents `terminate()` from attempting a second export if called more than once. |

### Listener Registration

```
// For Class Events
bluej.addClassListener(classListener) 

// For Project Events               
bluej.addPackageListener(projectListener)    

// For Compile Events
bluej.addCompileListener(compilationListener) 

// For Execution Events
bluej.addInvocationFinishedListener(executionListener)
                                                          
// Note: EditorListener self-registers onto JavaFX window events.
// Therefore, it does not register with the BlueJ API.
// Also, there is no exposed BlueJ API for EditorEvent capture...
```

---

## Logger Package

**Package:** `com.spyj.logger`  

**Contributor:** Mykel Dedier - Event Capture and Data Logging

**Student ID:** 816042057

The logger package is the core of SpyJ's data model. It receives captured BlueJ event data and processes them into TelemetryEvent objects, which encapsulates the captured event data with a timestamp of when said TelemetryEvent objects were created. SpyJLogger is the core of the logger package, and serves as the Subject to which EventSubscribers are attatched to. Created TelemetryEvent objects within SpyJLogger are pushed to EventSubscribers for persistence throughout the Editor's currently running process and for the quantifying of event data into useful statistics.

---

### SpyJLogger

**File:** `src/main/java/com/spyj/logger/SpyJLogger.java`

The central logger bus. All five listeners hold a reference to the same `SpyJLogger` instance and call `recordEvent(...)` to publish captured event data to SypJLogger, that 'updates' its internal state, causing attatched subscribers to be notified (i.e. the relevant recorded event's data is pushed to them).

#### Responsibilities
- Maintains a list of `EventSubscriber` instances.
- Wraps raw event data into a timestamped `TelemetryEvent` object and pushes it to every subscriber.
- Attempts to gracefully handle individual subscriber exceptions so that one failing subscriber cannot break the others and by extension the running BlueJ proccess.

#### Key Methods

| Method | Description |
|--------|-------------|
| `addSubscribers(EventSubscriber)` | Registers a new subscriber. Called during `SpyJ.startup()` to add `EventStore` and `StatsTracker`. |
| `recordEvent(EventType, Map<String, Object>)` | Constructs a `TelemetryEvent` with the current timestamp and pushes it to all subscribers. Each attempt to push a `TelemetryEvent` object is wrapped in a try/catch; with failures being printed to stdout. The setup of BlueJ means anything printed to stdout actually goes to a log file instead as that datastream is redirected. |

---

### EventSubscriber

**File:** `src/main/java/com/spyj/logger/EventSubscriber.java`

The subscriber interface that any event subscriber must implement.

```java
public interface EventSubscriber {
    void onEvent(TelemetryEvent e);
}
```

Both `EventStore` and `StatsTracker` implement this interface. This design makes it trivial to add new subscribers without changing any existing code, and is inline with the Observer Pattern.

---

### TelemetryEvent

**File:** `src/main/java/com/spyj/logger/TelemetryEvent.java`

An record of a single captured BlueJ event whose raw data has been extracted and encapsulated within this timestamped object. Created by `SpyJLogger.recordEvent(...)` and pushed to every subscriber.

#### Fields

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | `Instant` | The UTC instant at which the event was recorded. Auto-set to `Instant.now()` by the primary constructor, or supplied explicitly by the secondary constructor. |
| `eventType` | `EventType` | Enum classifying the category a captured event falls into. |
| `eventDetails` | `Map<String, Object>` | The extracted metadata from a captured event. Keys are standardised via `DetailsKey`. |

#### Constructors

| Constructor | Description |
|-------------|-------------|
| `TelemetryEvent(EventType, Map)` | Standard constructor — timestamp is set to `Instant.now()`. |
| `TelemetryEvent(EventType, Map, Instant)` | Explicit timestamp — can be used when a custom time is needed or as a redundant form of the implicit assignment done within the constructor. |

---

### EventType

**File:** `src/main/java/com/spyj/logger/EventType.java`

An enum that enumerates every kind of event SpyJ can record. The enum values are grouped by domain:

| Group | Values | Notes |
|-------|--------|-------|
| **Project lifecycle** | `PROJECT_CREATED`, `PROJECT_DELETED`, `PROJECT_OPENED`, `PROJECT_CLOSED`, `PROJECT_MODIFIED` | __ |
| **Class lifecycle** | `CLASS_CREATED`, `CLASS_DELETED`, `CLASS_OPENED`, `CLASS_CLOSED`, `CLASS_MODIFIED` | `CLASS_CREATED` / `CLASS_DELETED` are also project modifications |
| **Method** | `METHOD_DEFINED` | Recorded when a new method signature is detected |
| **Editor** | `EDITOR_IDLE`, `EDITOR_BURST` | Keystroke timing determines whether the user is in an active typing burst or idle |
| **Compilation** | `COMPILE_SUCCEEDED`, `COMPILE_FAILED`, `COMPILE_WARNING`, `COMPILE_ERROR` | Error events include line number and error message |
| **Execution** | `EXECUTION_SUCCEEDED`, `EXECUTION_EXCEPTION` | Exception events that should include console output (console outputs are not handled within the extension) |

---

### DetailsKey

**File:** `src/main/java/com/spyj/logger/DetailsKey.java`

An enum of standardised keys used in `TelemetryEvent.eventDetails` maps. Using an enum rather than raw strings prevents typos and makes it easy to find every location where a particular detail is written or read.

| Constant | Label string | Used by |
|----------|-------------|---------|
| `PROJECT_NAME` | `"Project"` | `ProjectListener`, `StatsTracker` |
| `CLASS_NAME` | `"Class"` | `ClassListener`, `ExecutionListener`, `StatsTracker` |
| `CLASS_OLD_NAME` | `"Class Old Name"` | `ClassListener`, `StatsTracker` |
| `METHOD_NAME` | `"Method"` | `ExecutionListener`, `StatsTracker` |
| `EXIT_STATUS` | `"Exit Status"` | `ExecutionListener`, `StatsTracker` |
| `USER_GENERATED` | `"User Generated"` | `CompilationListener`, `StatsTracker` |
| `COMPILE_DURATION` | `"Compile Duration"` | `CompilationListener`, `StatsTracker` |
| `ERROR_MESSAGE` | `"Error Message"` | `CompilationListener`, `StatsTracker` |
| `ERROR_LINE` | `"Error Start Line"` | `CompilationListener`, `StatsTracker` |
| `KEYSTROKE_COUNT` | `"Keystroke Count"` | `EditorListener`, `StatsTracker` |
| `BURST_DURATION` | `"Burst Duration"` | `EditorListener`, `StatsTracker` |
| `CADENCE_KPM` | `"Cadence KPM"` | `EditorListener`, `StatsTracker` |
| `SESSION_KEYSTROKES` | `"Total Session Keystrokes"` | `EditorListener`, `StatsTracker` |
| `IDLE_DURATION` | `"Idle Duration Time"` | `EditorListener`, `StatsTracker` |

---

### EventStore

**File:** `src/main/java/com/spyj/logger/EventStore.java`

An `EventSubscriber` that ideally should act as an append-only, thread-safe log of every `TelemetryEvent` received during a session. It is the primary data source for the report exporter.

#### Key Methods

| Method | Description |
|--------|-------------|
| `onEvent(TelemetryEvent)` | Appends a non-null event to the internal list. |
| `getEvents()` | Returns an unmodifiable snapshot of all events. |
| `getEvents(EventType)` | Returns an unmodifiable list of events matching the given type. |
| `size()` | Returns the total number of stored events. |
| `clear()` | Clears all stored events. |

All public methods are `synchronized` to allow safe access from both the BlueJ event thread and the JavaFX Application Thread (used by `EditorListener`).

---

### StatsTracker

**File:** `src/main/java/com/spyj/logger/StatsTracker.java`

An `EventSubscriber` that maintains a continuously-updated set of aggregated statistics. `StatsTracker` updates its counters incrementally as each event arrives, ensuring an up-to-date set of telemetry metrics are maintained throughout the user's session.

#### Statistics Categories

`getStats()` returns a `Map<String, Object>` with the following top-level keys, each mapping to a nested stats map:

| Category | Key statistics |
|----------|---------------|
| **Session** | Total events, first/last event timestamps, session duration (ms) |
| **Project Lifecycle** | Counts of created/opened/closed/modified/deleted projects; distinct projects opened |
| **Class Lifecycle** | Counts of created/modified/renamed/deleted classes; distinct classes touched; most-modified class |
| **Editor Interactions** | Total keystrokes; KPM (session-wide and per burst); burst count/duration/length; peak cadence; idle count/duration/longest idle |
| **Compilation** | Total succeeded/failed compiles; success rate; user-initiated compiles; errors & warnings; average errors per failed compile; average compile duration; recovery metrics; top 3 error messages; most frequent error line |
| **Execution** | Total succeeded/exception runs; exception rate; exit status breakdown; distinct methods run; most-run method |
| **Event Counts** | Counter of the number of events per `EventType` that occured |

#### Internal Event Handlers (private)

| Handler | Triggered by |
|---------|-------------|
| `onProjectOpened` | `PROJECT_OPENED` — adds project name to the distinct-projects set |
| `onClassEvent` | `CLASS_CREATED`, `CLASS_DELETED`, `CLASS_MODIFIED` — tracks renames, touched-class set, and per-class modification counts |
| `onEditorBurst` | `EDITOR_BURST` — accumulates keystroke/duration/cadence statistics |
| `onEditorIdle` | `EDITOR_IDLE` — accumulates idle time and longest-idle record |
| `onCompileFinished` | `COMPILE_SUCCEEDED`, `COMPILE_FAILED` — tracks duration, user-initiated flag, and failure-recovery timing |
| `onCompileError` | `COMPILE_ERROR` — counts error message frequencies (first line only) and error line frequencies |
| `onExecution` | `EXECUTION_SUCCEEDED`, `EXECUTION_EXCEPTION` — counts exit statuses and per-method run counts |

---

## Listener Package

**Package:** `com.spyj.listener`  

**Contributor:** Mykel Dedier - Event Capture and Data Logging

**Student ID:** 816042057

Each listener connects a specific BlueJ (or JavaFX for EditorListener) API to the `SpyJLogger`. Listeners translate BlueJ events into a curated set of `Map<String, Object>` detail stores and call `sjLogger.recordEvent(...)`. The Listener classes themselves form a psudeo Observer Pattern with BlueJ's API.

---

### ProjectListener

**File:** `src/main/java/com/spyj/listener/ProjectListener.java`

Implements `bluej.extensions2.event.PackageListener`. BlueJ fires package events whenever the user opens or closes a project (a BlueJ "project" is exposed through its root package).

#### Events Recorded

| BlueJ Callback | Event Recorded | Details |
|----------------|---------------|---------|
| `packageOpened` | `PROJECT_OPENED` | Project name, directory, project structure string, opened package name. Only the **first** package-open event per project directory fires a `PROJECT_OPENED` event (subsequent package opens within the same project are ignored). |
| `packageClosing` | `PROJECT_CLOSED` | Project name, directory. The event is deferred onto the JavaFX Application Thread via `Platform.runLater(...)` so that `bluej.getOpenProjects()` reflects the closed state before the event is recorded. |

#### Class Pre-population

On `packageOpened`, `ProjectListener` calls `classListener.addExistingClasses(currPackage)` to seed `ClassListener`'s internal file set with classes that already existed when the project was opened. This prevents pre-existing classes from being reported as newly created.

---

### ClassListener

**File:** `src/main/java/com/spyj/listener/ClassListener.java`

Implements `bluej.extensions2.event.ClassListener`. BlueJ fires class events whenever a class is added, removed, or renamed.

#### Events Recorded

| BlueJ Callback | Event Recorded | Details |
|----------------|---------------|---------|
| `classStateChanged` | `CLASS_CREATED` (first time a class file is seen) or `CLASS_MODIFIED` (subsequent state changes) | Class name, package name, compilation status. Distinguishes creation from modification by tracking seen class files in a `HashSet<File>`. |
| `classRemoved` | `CLASS_DELETED` | Class name, package name. Removes the file from the internal tracking set. |
| `classNameChanged` | `CLASS_MODIFIED` | New class name, old class name (`"Class Old Name"`), package name, compilation status. Updates the internal tracking set by swapping the old file path for the new one. |

#### `addExistingClasses(BPackage)`

Called by `ProjectListener` on project open to pre-populate the internal `HashSet<File>` with files that already exist. Without this, all pre-existing classes would be incorrectly reported as `CLASS_CREATED` on first observation.

---

### CompilationListener

**File:** `src/main/java/com/spyj/listener/CompilationListener.java`

Implements `bluej.extensions2.event.CompileListener`. Tracks the full lifecycle of a compilation pass.

#### Events Recorded

| BlueJ Callback | Event Recorded | Details |
|----------------|---------------|---------|
| `compileStarted` | *(none)* | Records `startTime = Instant.now()` for duration tracking. |
| `compileSucceeded` | `COMPILE_SUCCEEDED` | Compile status, duration (ms), whether user-initiated, list of compiled file names. |
| `compileFailed` | `COMPILE_FAILED` | Same payload as `COMPILE_SUCCEEDED` but with status `"Failed"`. |
| `compileWarning` | `COMPILE_WARNING` | Warning line number, message text, character position. |
| `compileError` | `COMPILE_ERROR` | Error line number, message text, character position. |

The duration is computed as the elapsed time from `compileStarted` to the finish callback. `startTime` is set to `null` after recording to guard against stale measurements.

---

### ExecutionListener

**File:** `src/main/java/com/spyj/listener/ExecutionListener.java`

Implements `bluej.extensions2.event.InvocationFinishedListener`. Fires whenever the user invokes a method or constructor from the BlueJ object bench or object inspector.

#### Events Recorded

| BlueJ Exit Condition | Event Recorded | Details |
|----------------------|---------------|---------|
| `NORMAL_EXIT` | `EXECUTION_SUCCEEDED` | Package, class, method, parameters list, target object (or `"Static Instance"`), exit status. |
| Any other exit | `EXECUTION_EXCEPTION` | Same payload. |

---

### EditorListener

**File:** `src/main/java/com/spyj/listener/EditorListener.java`

Monitors all JavaFX keyboard events across every BlueJ editor window. Because BlueJ does not expose an editor-level keystroke API, `EditorListener` hooks into the JavaFX window system directly.

#### Design

- **Window discovery** — On construction, iterates `Window.getWindows()` to hook all currently-open stages, then installs a `ListChangeListener` to hook any stage opened later.
- **Editor detection** — `isEditorStage(Stage)` identifies editor windows by title format: not starting with `"BlueJ:"` and containing `"["` with a trailing `"]"`.
- **Typing key filter** — `isTypingKey(KeyEvent)` returns `true` for printable characters, Backspace, Delete, Enter, Tab, and paste/cut/undo/redo shortcuts (Ctrl+V/X/Z/Y). Navigation, modifier, and function keys are excluded.
- **Burst model** — Consecutive keystrokes in the same editor window form a *burst*. A burst ends when the user is idle for more than `IDLE_THRESHOLD_MS` (3 000 ms), or when focus moves to a different editor stage. A burst is only recorded if it contains at least `MIN_BURST_KEYSTROKES` (2) keystrokes and has a non-zero duration.
- **Idle timer** — A JavaFX `PauseTransition` of 3 000 ms is restarted on every keystroke. When it fires, `onUserBecameIdle()` flushes the active burst and marks the state as idle.

#### Events Recorded

| Condition | Event Recorded | Details |
|-----------|---------------|---------|
| Idle timer fires (user stops typing for ≥ 3 s) | `EDITOR_IDLE` (on next keystroke or `stop()`) | Editor title where idle started, title of window where typing resumed, idle duration (ms), running session idle total (ms). |
| Burst ends (stage change or idle timer) | `EDITOR_BURST` | Editor title, keystroke count, burst duration (ms), cadence (KPM), average inter-keystroke time (ms), running session total keystrokes. |

#### `stop()`

Called by `SpyJ.terminate()` before reading the event store. Runs on the JavaFX Application Thread (or schedules via `Platform.runLater` with a 2-second `CountDownLatch` if called off-thread) to ensure the final in-progress burst and any pending idle event are flushed before the report is generated.

---
---

## Formatting and Export Package

**Package:** `com.spyj.format`  
**Contributor:** Caitlyn Maharaj - Data Formatting and Export
**Student ID:**816039787

This package converts recorded telemetry and session statistics into
JSON and PDF reports. It separates report formatting, file saving,
and command execution into different classes.

The JSON report preserves all supplied events and their details.
The PDF provides a two-page summary with statistics, charts,
progression events, and compilation error information.

---

### LogFormatter

**File:** `src/main/java/com/spyj/format/LogFormatter.java`

The shared interface implemented by JSONFormatter and PDFFormatter.

#### Key Method

| Method | Description |
|--------|-------------|
| `format(List<TelemetryEvent>, Map<String, Object>)` | Receives recorded events and session information, and returns the formatted report as a byte array. Declares IOException. |

This interface supports the Strategy pattern. Export code can work
with either formatter through the same interface.

---

### JSONFormatter

**File:** `src/main/java/com/spyj/format/JSONFormatter.java`

Implements LogFormatter and uses Gson to produce readable JSON.

#### Formatting Process

1. Copies the supplied session summary into the report.
2. Converts each event into a map containing its timestamp, type, and details.
3. Adds the complete event list under the `events` field.
4. Serializes the report and returns UTF-8 bytes.

Timestamps are stored as text using Instant.toString().
Gson pretty printing makes the output easier to read.
Null values are included to represent unavailable information.

This class formats the report but does not save it to a file.

---

### PDFFormatter

**File:** `src/main/java/com/spyj/format/PDFFormatter.java`

Implements LogFormatter and uses Apache PDFBox to create a two-page
A4 report.

#### Report Contents

| Page | Contents |
|------|----------|
| Page 1 | Session information, recorded typing and idle durations, compilation counts, total runs, compilation success rate, and two bar charts. |
| Page 2 | Class creation and method definition timeline, compilation error frequency table, and intervals between compilation completion events. |

#### Important Methods

| Method | Description |
|--------|-------------|
| `format(...)` | Creates the PDF, adds both pages, and returns the document as bytes. |
| `addSummaryPage(...)` | Displays session statistics and the two charts. |
| `drawCompilationChart(...)` | Compares successful and failed compilation counts. |
| `drawActivityChart(...)` | Displays full-session activity values when supplied; otherwise uses recorded editor typing and idle durations. |
| `addEventsPage(...)` | Displays progression events and counts compilation error messages. |
| `drawCompilationIntervals(...)` | Calculates gaps between sorted successful or failed compilation event timestamps. |
| `getNumber(...)` | Returns a numeric summary value, or null when the value is not numeric. |
| `writeLine(...)` | Writes text and shortens lines that exceed the available width. |

#### Calculations and Display Limits

- Compilation success rate is successful compilations divided by
  total compilations, multiplied by 100.
- The progression timeline includes CLASS_CREATED and METHOD_DEFINED
  events, sorted by timestamp.
- Up to nine progression events are displayed.
- Error frequencies count COMPILE_ERROR events grouped by their
  complete error message.
- Up to eight distinct error messages are displayed in encounter order.
- The first three compilation intervals are displayed.
- The average interval uses all consecutive compilation completion
  timestamps.
- Compilation intervals represent time between completion events,
  not the duration of an individual compilation.
- Long text is shortened, and characters outside printable ASCII
  are replaced with question marks.

The JSON report retains the supplied events and full details when
the PDF cannot display everything.

---

### SessionSummaryBuilder

**File:** `src/main/java/com/spyj/format/SessionSummaryBuilder.java`

Converts StatsTracker statistics into the field names used by
the JSON and PDF reports.

#### Key Methods

| Method | Description |
|--------|-------------|
| `build(tracker, sessionId, studentId, start, end)` | Creates the session summary using identifiers, session timestamps, and tracked statistics. |
| `getSection(stats, name)` | Retrieves a required statistics map and rejects missing or incorrectly structured sections. |

#### Summary Fields

| Fields | Source or meaning |
|--------|-------------------|
| `session_id`, `student_id` | Identifiers supplied by the caller. |
| `start_time`, `end_time` | Supplied session timestamps converted to text. |
| `session_duration` | Milliseconds between the supplied start and end times. |
| `active_time`, `idle_time` | Null because full-session activity measurements are unavailable. |
| `active_typing_time` | StatsTracker's Active Typing Time (ms). |
| `recorded_editor_idle_time` | StatsTracker's Total Idle Time (ms). |
| `total_compilations` | Total Compiles from the Compilation statistics. |
| `successful_compilations`, `failed_compilations` | Succeeded and Failed compilation counts. |
| `total_runs` | Total Runs from the Execution statistics. |

The builder rejects an end time earlier than the start time.

Recorded typing and editor idle durations do not necessarily cover
the entire session.

---

### Command

**File:** `src/main/java/com/spyj/format/Command.java`

Defines the interface for an executable operation.

| Method | Description |
|--------|-------------|
| `execute()` | Performs the operation and declares IOException. |

This interface forms part of the Command pattern.

---

### ExportCommand

**File:** `src/main/java/com/spyj/format/ExportCommand.java`

Implements Command and stores the information needed to save
one report.

#### Constructor Inputs

- A LogFormatter implementation.
- The recorded events.
- The session summary.
- The destination Path.

The constructor copies the event list and summary map.
These are shallow copies: nested objects are not independently copied.

#### Execution

`execute()` formats the data, creates missing parent directories,
and writes the resulting bytes to the destination file.
An existing file at the same path is overwritten.

Formatting and file-writing errors are passed to the caller.

---

### CommandInvoker

**File:** `src/main/java/com/spyj/format/CommandInvoker.java`

Runs commands through a single-thread ExecutorService.
Commands execute one at a time in submission order.

| Method | Description |
|--------|-------------|
| `submit(Command)` | Queues a command and returns a Future<Void> for checking completion or retrieving an execution failure. |
| `close()` | Stops accepting new work and waits for submitted tasks to finish. |

The class implements AutoCloseable so it can be used in a
try-with-resources block.

Waiting through Future.get() or close() blocks the calling thread.
FormattingDemo uses these operations to wait for its reports.

---

### ReportExporter

**File:** `src/main/java/com/spyj/format/ReportExporter.java`

Coordinates the export of both JSON and PDF reports.

#### Key Method

`export(store, tracker, sessionId, studentId, start, end)`

This method:

1. Retrieves an event snapshot from EventStore.
2. Builds the session summary using SessionSummaryBuilder.
3. Creates JSON and PDF bytes through the LogFormatter interface.
4. Creates the output directory if needed.
5. Writes both reports and prints their absolute paths.

#### Output Files

- `target/reports/<sessionId>.json`
- `target/reports/<sessionId>.pdf`

These paths are relative to the application's working directory.

SpyJ.terminate() calls this exporter at shutdown when recorded events
are available. In this implementation, ReportExporter performs its
work synchronously; it does not use CommandInvoker.

---

### FormattingDemo

**File:** `src/main/java/com/spyj/format/FormattingDemo.java`

Demonstrates formatting and export using a controlled 60-second
sample session.

The sample includes:

- One class creation and one method definition.
- 30,000 milliseconds of typing.
- 10,000 milliseconds of editor idle time.
- Three compilation errors, including a repeated message.
- Two failed compilations and one successful compilation.

The events are passed directly to EventStore and StatsTracker
to preserve their supplied timestamps.

The demo builds a session summary, creates JSON and PDF ExportCommands,
submits them to CommandInvoker, and waits for completion.

#### Running the Demo

1. Compile the project using `mvn compile`.
2. Open FormattingDemo.java in VS Code.
3. Run its main method.

#### Output Files

- `target/sample-report.json`
- `target/sample-report.pdf`

The demo exercises report generation with sample data.
It is not an automated assertion-based test or a complete BlueJ
integration test.

---

### Design Patterns Used in This Package

| Pattern | Classes | Purpose |
|---------|---------|---------|
| Strategy | LogFormatter, JSONFormatter, PDFFormatter | Allows different report formats to share the same interface. |
| Command | Command, ExportCommand, CommandInvoker | Separates an export request from its execution. |

### External Libraries

- Gson: JSON serialization.
- Apache PDFBox: PDF generation.
- Maven: dependency and build management.

## Unit Testing & Quality Assurance

**Contributor:** Aidan Ramnarine - Unit Testing & Quality Assurance

**Student ID:** 

**Package:** `src/test/java/com/spyj/`

To ensure the reliability and correctness of the telemetry data collection, SpyJ includes a comprehensive suite of automated tests. The testing infrastructure is built using **JUnit 5** for test execution and assertions, **Mockito** for mocking complex BlueJ API events, and **JaCoCo** for generating code coverage reports. 

The test suites validate internal components independently of the BlueJ GUI interface, achieving the required code coverage target of at least 75%.

---

### Testing Architecture

All tests are located in `src/test/java/com/spyj/` and mirror the package structure of the main source code. The Maven `pom.xml` is configured with the `maven-surefire-plugin` to automatically execute tests during the `mvn test` lifecycle phase.

**Key Dependencies:**
- `junit-jupiter` (v5.10.2)
- `mockito-core` (v5.11.0)
- `jacoco-maven-plugin` (v0.8.11)

---
---

### Test Suites

The testing strategy is divided into two main categories: testing the core Observer/Data Aggregation logic (Logger Package) and testing the translation of BlueJ events into telemetry data (Listener Package).

| Test Class | Component Tested | Key Validations |
|------------|------------------|-----------------|
| `SpyJLoggerTest` | `SpyJLogger` | Verifies the Observer pattern (subscribers are notified). Ensures that a `RuntimeException` thrown by one subscriber does not break the logging loop for others. |
| `EventStoreTest` | `EventStore` | Verifies thread-safe event appending, retrieval of all events, filtering by `EventType`, and clearing the store. |
| `StatsTrackerTest` | `StatsTracker` | Validates the mathematical aggregations. Tests compilation success rate percentages, total/longest/average idle time calculations, and error frequency tracking. |
| `ClassListenerTest` | `ClassListener` | Mocks `ClassEvent` and `BClass` to verify that class creation, modification, and deletion events are correctly recorded with the proper `EventType`. |
| `CompilationListenerTest` | `CompilationListener` | Mocks `CompileEvent` to verify that successful, failed, and error compilation events are logged with the correct duration and status details. |
| `ExecutionListenerTest` | `ExecutionListener` | Mocks `InvocationFinishedEvent` to verify that normal exits are logged as `EXECUTION_SUCCEEDED` and abnormal exits as `EXECUTION_EXCEPTION`. |

#### Logger Tests
Because the `Logger` package handles pure data logic, these tests do not require complex mocking. They focus on state verification. For example, `StatsTrackerTest` feeds a sequence of `TelemetryEvent` objects into the tracker and asserts that the output of `getStats()` contains the exact expected calculations (e.g., feeding 2 successful and 1 failed compile must return a `Success Rate (%)` of `66.67`).

#### Listener Tests
Testing the listener classes is complex because they rely heavily on the BlueJ API (`bluej.extensions2`), which cannot be instantiated outside of a running BlueJ environment. To solve this, Mockito is used to create fake BlueJ events. 
For example, in `ClassListenerTest`, a fake `ClassEvent` is created, and Mockito is used to define what `event.getBClass().getJavaFile()` returns. The test then triggers `listener.classStateChanged(fakeEvent)` and verifies (using `Mockito.verify`) that the `SpyJLogger` received the event with the correct `EventType.CLASS_CREATED` or `CLASS_MODIFIED`.

---

### Code Coverage (JaCoCo)

To ensure the test suite is actually exercising the codebase rather than just passing trivially, SpyJ uses **JaCoCo** to generate coverage reports. 

To run the full test suite and generate the coverage report, execute:
```bash
mvn clean test
