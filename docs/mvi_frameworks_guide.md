# Comprehensive Guide to Modern MVI & UDF Frameworks

A complete comparative analysis of Model-View-Intent (MVI) and Unidirectional Data Flow (UDF) architectures in modern Android and Kotlin Multiplatform (KMP) development.

---

## Table of Contents
1. [Foundational Theory: The Elm Architecture (TEA)](#1-foundational-theory-the-elm-architecture-tea)
2. [The 8 Architectures Compared](#2-the-8-architectures-compared)
   - [1. Classic MVI (`merge` + `scan`)](#1-classic-mvi-merge--scan)
   - [2. Modern MVVM / Pragmatic UDF (Google Recommended)](#2-modern-mvvm--pragmatic-udf-google-recommended)
   - [3. Orbit MVI](#3-orbit-mvi)
   - [4. Airbnb Mavericks](#4-airbnb-mavericks)
   - [5. Spotify Mobius](#5-spotify-mobius)
   - [6. Slack Circuit](#6-slack-circuit)
   - [7. MVIKotlin](#7-mvikotlin)
   - [8. FlowMVI](#8-flowmvi)
3. [Master Comparison Matrix](#3-master-comparison-matrix)
4. [Deep Dives & Common Pitfalls](#4-deep-dives--common-pitfalls)
   - [How External Cold Streams & Lifecycle Unsubscription Work](#how-external-cold-streams--lifecycle-unsubscription-work)
   - [Why Orbit Needs `subscriptionCount` but Mobius Does Not](#why-orbit-needs-subscriptioncount-but-mobius-does-not)
   - [The Mavericks Cold Stream Trap](#the-mavericks-cold-stream-trap)
   - [The Cartesian Explosion with `combine` & Solutions](#the-cartesian-explosion-with-combine--solutions)
   - [What is Time-Travel Debugging?](#what-is-time-travel-debugging)

---

## 1. Foundational Theory: The Elm Architecture (TEA)

Almost all modern MVI and Redux architectures descend from **The Elm Architecture (TEA)**, created in 2012 by Evan Czaplicki:

```
                 ┌──────────────┐
                 │     VIEW     │ ◄─── view(model)
                 └──────┬───────┘
                        │ User Dispatches Msg
                        ▼
                 ┌──────────────┐
                 │     MSG      │ (Message / Event / Intent)
                 └──────┬───────┘
                        │
                        ▼
┌──────────────┐ ┌──────────────┐    Cmd (Side-effect description)   ┌──────────────┐
│ SUBSCRIPTION ├─►    UPDATE    ├───────────────────────────────────►│   RUNTIME    │
│ (Sensors/GPS)│ │(Pure Reducer)│                                    │ (HTTP, Disk) │
└──────────────┘ └──────┬───────┘                                    └──────┬───────┘
                        │ newModel                                          │ Result Msg
                        ▼                                                   │
                 ┌──────────────┐                                           │
                 │    MODEL     │ ──────────────────────────────────────────┘
                 │ (Immutable)  │
                 └──────────────┘
```

### The Three Pure Pillars:
1. **`Model`**: The single, immutable state of the application.
2. **`View`**: A 100% pure projection: $\text{View}: \text{Model} \rightarrow \text{UI}$.
3. **`Update`**: A 100% pure function: $\text{Update}: (\text{Model}, \text{Msg}) \rightarrow (\text{NewModel}, \text{Cmd})$.
4. **Side Effects as Data (`Cmd` & `Sub`)**:
   - Functions never execute network calls or I/O directly.
   - `Update` returns **data descriptions** of side effects (`Cmd`). The runtime executes them and feeds results back as `Msg`s.
   - External continuous streams (Sensors, GPS) are declared as **Subscriptions (`Sub`)**.

---

## 2. The 8 Architectures Compared

---

### 1. Classic MVI (`merge` + `scan`)
* **Philosophy**: Mathematical reactive stream modeling via RxJava or Kotlin Coroutines Flow.
* **Core Flow**:
  $$\text{merge}(\text{intentFlow.flatMapMerge} \{\text{toMutation}(it)\}, \text{externalMutations}) \xrightarrow{\text{scan}} \text{State}$$

```kotlin
class ClassicMviViewModel(
    sensorUseCase: ContinuousSensorUseCase
) : ViewModel() {
    val externalMutations: Flow<Mutation> = sensorUseCase().map { Mutation.SensorUpdated(it) }

    val uiState: StateFlow<State> by lazy {
        merge(intentFlow.flatMapMerge(::toMutation), externalMutations)
            .scan(initialState, ::reduce)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialState)
    }

    private fun reduce(previous: State, mutation: Mutation): State = when (mutation) {
        is Mutation.SensorUpdated -> previous.copy(sensorText = mutation.data)
        is Mutation.NoteAdded -> previous.copy(notes = previous.notes + mutation.note)
    }
}
```
* **Pros**: 100% pure state transitions; automatic unsubscription via `WhileSubscribed`.
* **Cons**: Massive boilerplate ("explosion of mutations"); async operations require complex Flow operators.

---

### 2. Modern MVVM / Pragmatic UDF (Google Recommended)
* **Philosophy**: Unidirectional Data Flow without redundant `Mutation` classes.
* **Core Flow**: Declarative `combine` + `StateFlow.update { copy(...) }`.

```kotlin
class ModernUdfViewModel(
    sensorUseCase: ContinuousSensorUseCase,
    batteryUseCase: BatteryLevelUseCase
) : ViewModel() {
    private val _userState = MutableStateFlow(UserState())

    val uiState: StateFlow<UiState> = combine(
        _userState,
        sensorUseCase(),
        batteryUseCase()
    ) { user, sensor, battery ->
        UiState(notes = user.notes, sensor = sensor, battery = battery)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun sendIntent(intent: Intent) {
        when (intent) {
            is Intent.AddNote -> _userState.update { it.copy(notes = it.notes + intent.note) }
        }
    }
}
```
* **Pros**: Standard Google architecture; extremely readable; zero 3rd-party dependencies.
* **Cons**: Wide `combine` can create invalid state combinations if not properly grouped.

---

### 3. Orbit MVI
* **Philosophy**: "MVVM+": Coroutine-native DSL (`intent`, `reduce`, `postSideEffect`).
* **Handling Cold Streams**: **`repeatOnSubscription`** monitors container subscriber count.

```kotlin
class OrbitViewModel(
    private val sensorUseCase: ContinuousSensorUseCase
) : ContainerHost<State, SideEffect>, ViewModel() {
    override val container = container<State, SideEffect>(State())

    init {
        intent {
            repeatOnSubscription { // 👈 Automatically pauses when UI is hidden!
                sensorUseCase().collect { sensor ->
                    reduce { state.copy(sensorText = sensor) }
                }
            }
        }
    }

    fun addNote(text: String) = intent {
        reduce { state.copy(notes = state.notes + text) }
        postSideEffect(SideEffect.Toast("Added!"))
    }
}
```
* **Compose Integration**:
  ```kotlin
  val state by viewModel.collectAsState()
  viewModel.collectSideEffect { effect -> ... }
  ```
* **Pros**: Zero boilerplate; full lifecycle safety out of the box; KMP support.

---

### 4. Airbnb Mavericks (MvRx)
* **Philosophy**: React-like `setState { copy() }` with property-level `Async<T>` sealed wrappers.
* **How it handles async tasks**: `.execute { copy(data = it) }` automatically handles `Uninitialized`, `Loading`, `Success`, `Fail`.

```kotlin
data class ScreenState(
    val listing: Async<Listing> = Uninitialized,
    val batteryPercent: Int = 100
) : MavericksState

class ScreenViewModel : MavericksViewModel<ScreenState>(ScreenState()) {
    fun fetchListing() {
        repository.getListing()
            .execute { copy(listing = it) } // Transitions through Loading -> Success/Fail
    }
}
```
* **Compose Integration**:
  ```kotlin
  // Granular subscription avoids recomposing when other fields change:
  val battery by viewModel.collectAsStateWithLifecycle(ScreenState::batteryPercent)
  ```
* **The Catch**: `execute()` runs in `viewModelScope` and **does NOT cancel continuous cold streams** when backgrounded. Continuous streams must be exposed as separate `StateFlow`s.

---

### 5. Spotify Mobius
* **Philosophy**: Strict Elm Architecture (TEA) for enterprise state machines.
* **Core Flow**:
  $$\text{update}(\text{Model}, \text{Event}) \rightarrow \text{Next}<\text{Model}, \text{Set}<\text{Effect}>>$$

```kotlin
// 1. Pure Update Function (Zero Side-Effects)
fun update(model: Model, event: Event): Next<Model, Effect> = when (event) {
    is Event.SaveClicked -> next(
        model.copy(isSaving = true),
        setOf(Effect.SaveToDb(event.text)) // 👈 Instruction only!
    )
    is Event.SensorTick -> {
        if (model.isPaused) noChange() // 👈 Drops invalid event!
        else next(model.copy(sensor = event.data))
    }
}

// 2. EventSource (External streams)
val sensorEventSource = EventSource<Event> { consumer ->
    val job = scope.launch { sensorUseCase().collect { consumer.accept(Event.SensorTick(it)) } }
    Disposable { job.cancel() }
}
```
* **Lifecycle**: Controlled imperatively via `controller.start()` and `controller.stop()`.
* **Pros**: 100% pure JUnit testable in 2ms without mocks; zero race conditions.
* **Cons**: Highest boilerplate (Events, Effects, Handlers, Controllers).

---

### 6. Slack Circuit
* **Philosophy**: "Compose-Native Architecture." Completely eliminates AndroidX `ViewModel`.
* **Core Idea**: Presenter is a `@Composable` function; UI State embeds an `eventSink` callback.

```kotlin
data class CounterState(
    val count: Int,
    val sensorText: String,
    val eventSink: (CounterEvent) -> Unit // 👈 Event handler lives in State!
) : CircuitUiState

class CounterPresenter(
    private val sensorUseCase: ContinuousSensorUseCase
) : Presenter<CounterState> {
    @Composable
    override fun present(): CounterState {
        var count by rememberRetained { mutableIntStateOf(0) }
        val sensorText by produceState("...") {
            sensorUseCase().collect { value = it } // Cancelled on dispose!
        }

        return CounterState(count, sensorText) { event ->
            when (event) {
                CounterEvent.Increment -> count++
            }
        }
    }
}
```
* **Configuration Changes**: Uses `rememberRetained` backed by a root `RetainedStateRegistry` (survives rotation without extending `ViewModel`).

---

### 7. MVIKotlin (Arkadii Ivanov)
* **Philosophy**: Multiplatform MVI (paired with Decompose) with formal separation between UI `Intent` and external `Action`.
* **Core Components**:
  - `Intent`: User UI interactions
  - `Action`: External stream triggers / bootstrap
  - `Message`: Internal mutations feeding the reducer
  - `Reducer`: Pure `reduce(state, message) -> state`
* **Bootstrapper & Executor**:
  ```kotlin
  class MyBootstrapper(private val sensor: ContinuousSensorUseCase) : CoroutineBootstrapper<Action>() {
      override fun invoke() {
          scope.launch { sensor().collect { dispatch(Action.SensorUpdated(it)) } }
      }
  }

  class MyExecutor : CoroutineExecutor<Intent, Action, State, Message, Nothing>() {
      override fun executeAction(action: Action, getState: () -> State) { ... }
      override fun executeIntent(intent: Intent, getState: () -> State) { ... }
  }
  ```
* **Superpower**: Official IntelliJ IDEA plugin for visual **Time-Travel Debugging**.

---

### 8. FlowMVI (Respawn)
* **Philosophy**: Plugin-driven, Coroutine-native MVI store.
* **Handling Cold Streams**: Built-in **`whileSubscribed`** plugin.

```kotlin
val store = store<State, Intent, Nothing>(initial = State()) {
    enableLogging()
    manageJobs() // Auto-cancels coroutines on store destruction

    // Only collects while UI is actively subscribed:
    whileSubscribed {
        sensorUseCase().consume { sensorData ->
            updateState { copy(sensorText = sensorData) }
        }
    }

    reduce { intent ->
        when (intent) {
            is Intent.Click -> updateState { copy(count = count + 1) }
        }
    }
}
```

---

## 3. Master Comparison Matrix

| Framework | Core Engine | Uses `merge + scan`? | Cold Stream Lifecycle Handling | State Transition Style | Time-Travel Debugging? | Primary Target |
| :--- | :--- | :---: | :--- | :--- | :---: | :--- |
| **Classic MVI** | Kotlin Coroutines | **YES** | `stateIn(WhileSubscribed)` | Pure `reduce(state, mutation)` | Difficult | Reactive purists |
| **Modern MVVM** | Google Vanilla Flow | **NO** | `combine(...).stateIn(WhileSubscribed)` | Inlined `update { copy() }` | ❌ No | Android industry standard |
| **Orbit MVI** | Coroutines DSL | **NO** | `repeatOnSubscription` (sub count + 100ms) | Inlined `reduce { state.copy() }` | ❌ No | Modern Compose & KMP |
| **Mavericks** | Coroutines + Jetpack | **NO** | ⚠️ Runs in `viewModelScope` (must split) | Inlined `setState { copy() }` | ❌ No | Fragment + Epoxy legacy |
| **Mobius** | Pure Elm Engine | **YES (Events)** | `controller.start()` / `controller.stop()` | Pure `update(model, event) -> Next` | **YES** | Automotive, Media, Fintech |
| **Circuit** | Compose Runtime | **NO** | Compose `produceState` / `rememberRetained` | Direct Snapshot (`count++`) | ❌ No | Pure Compose apps |
| **MVIKotlin** | Multiplatform Stores | **YES** | `CoroutineBootstrapper` + Lifecycle Binder | Pure `reduce(state, message)` | **YES (IDE Plugin)** | KMP apps with Decompose |
| **FlowMVI** | Plugin Store | **NO** | `whileSubscribed` plugin | Atomic `updateState { copy() }` | Basic | Coroutine plugin fans |

---

## 4. Deep Dives & Common Pitfalls

### How External Cold Streams & Lifecycle Unsubscription Work
Cold streams (e.g. GPS, vehicle telemetry, hardware sensors) **must stop collecting** when an app is in the background to save battery:
- In **Flow pipelines (`stateIn(WhileSubscribed(5_000))` / Orbit / FlowMVI)**:  
  When Compose leaves `STARTED`, the subscriber count drops to 0. After the timeout, cancellation propagates upstream to cancel the cold stream.
- In **Imperative Loops (Mobius)**:  
  `controller.stop()` explicitly calls `.dispose()` on all `EventSource`s in `onStop()`.

---

### Why Orbit Needs `subscriptionCount` but Mobius Does Not
* **Orbit (Implicit Reactive)**: The ViewModel has **no reference** to the Android Lifecycle. The only signal it has to know whether the UI is alive is checking whether `stateFlow` has active collectors (`subscriptionCount > 0`).
* **Mobius (Explicit Imperative)**: Android lifecycle callbacks directly command `controller.start()` and `controller.stop()`. It doesn't need to count subscribers because it is explicitly ordered to start and stop.

---

### The Mavericks Cold Stream Trap
In Airbnb Mavericks, `.execute()` runs in `viewModelScope`. Because `viewModelScope` is not cancelled in `onStop()`, **infinite cold streams will run forever in the background**.  
*Solution*: Continuous cold streams in Mavericks must bypass `execute` and be exposed as a separate `StateFlow` via `WhileSubscribed(5_000)`.

---

### The Cartesian Explosion with `combine` & Solutions
Combining 5 independent flows (`combine(A, B, C, D, E)`) creates an $N$-dimensional Cartesian Product, risking invalid states (e.g. *Connected, but speed is missing and error is non-null*).

#### Solutions:
1. **Sealed Interface + `flatMapLatest` (Finite State Machine)**:  
   Model the screen as `Loading`, `Error`, and `Connected`. Only combine telemetry streams when in the `Connected` branch.
2. **Domain Aggregator UseCase**:  
   Group related streams in the domain layer (`GetVehicleTelemetryUseCase`) before they reach the ViewModel.
3. **`Async<T>` Wrapper (Mavericks)**:  
   Wrap each property in `Async<T>` (`Uninitialized`, `Loading`, `Success`, `Fail`) so loading/error states are locally scoped.

---

### What is Time-Travel Debugging?
Because pure MVI functions satisfy:
$$\text{State}_{n+1} = \text{reduce}(\text{State}_n, \text{Event})$$
A DevTools recorder can log `InitialState` and an array of all `[Events]`.

#### Capabilities:
1. **Rewind**: Click any past event to instantly reset the running UI to that point in time.
2. **Event Skipping**: Toggle off an event to see what the state machine would produce without it.
3. **1-Click Bug Reproduction**: Export the event log as a `.json` file from a tester's device, import it into the IDE, and replay the exact bug with 100% determinism.
