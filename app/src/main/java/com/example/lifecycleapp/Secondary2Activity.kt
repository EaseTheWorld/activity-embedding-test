package com.example.lifecycleapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.common.ui.settings.BaseUpdateStateViewModel
import com.example.common.ui.settings.SimpleMviViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

// ========================================================================
// 1. External Cold UseCase 1: Continuous Sensor Stream
// ========================================================================
class ContinuousSensorUseCase {
    private val tag = "MviLifecycleTest"

    operator fun invoke(): Flow<String> = flow {
        Log.i(tag, ">>> [SensorUseCase] ACTIVE: Flow collection started!")
        var tick = 0
        while (true) {
            emit("Vehicle Sensor tick #$tick (${System.currentTimeMillis() % 100000})")
            tick++
            delay(1000)
        }
    }.onStart {
        Log.i(tag, ">>> [SensorUseCase] onStart: Sensor stream initiated")
    }.onCompletion { cause ->
        Log.i(tag, ">>> [SensorUseCase] onCompletion: UNSUBSCRIBED! cause=$cause")
    }
}

// ========================================================================
// 2. External Cold UseCase 2: Battery Level Stream
// ========================================================================
class BatteryLevelUseCase {
    private val tag = "MviLifecycleTest"

    operator fun invoke(): Flow<Int> = flow {
        Log.i(tag, ">>> [BatteryUseCase] ACTIVE: Flow collection started!")
        var battery = 85
        while (true) {
            emit(battery)
            delay(2000)
            battery = (battery - 1).coerceAtLeast(10)
        }
    }.onStart {
        Log.i(tag, ">>> [BatteryUseCase] onStart: Battery stream initiated")
    }.onCompletion { cause ->
        Log.i(tag, ">>> [BatteryUseCase] onCompletion: UNSUBSCRIBED! cause=$cause")
    }
}

// ========================================================================
// 3. Shared Contracts
// ========================================================================
sealed interface DemoIntent {
    data class AddNote(val note: String) : DemoIntent
}

sealed interface DemoMutation {
    data class SensorUpdated(val sensorText: String) : DemoMutation
    data class BatteryUpdated(val percent: Int) : DemoMutation
    data class NoteAdded(val note: String) : DemoMutation
}

data class DemoUiState(
    val architectureName: String = "",
    val latestSensorText: String = "Connecting to sensor stream...",
    val batteryPercent: Int = 100,
    val userNotes: List<String> = emptyList()
)

// ========================================================================
// 4A. Version A: Classic MVI (BaseMviViewModel with merge + scan)
// ========================================================================
class MviDemoViewModel(
    sensorUseCase: ContinuousSensorUseCase = ContinuousSensorUseCase(),
    batteryUseCase: BatteryLevelUseCase = BatteryLevelUseCase()
) : SimpleMviViewModel<DemoIntent, DemoMutation, DemoUiState>(
    initialState = DemoUiState(architectureName = "Classic MVI (merge + scan + Reducer)")
) {
    // Merges external flows into the uiState pipeline:
    override val externalMutations: Flow<DemoMutation> = merge(
        sensorUseCase().map { DemoMutation.SensorUpdated(it) },
        batteryUseCase().map { DemoMutation.BatteryUpdated(it) }
    )

    override fun toMutation(intent: DemoIntent): Flow<DemoMutation> = when (intent) {
        is DemoIntent.AddNote -> flowOf(DemoMutation.NoteAdded(intent.note))
    }

    override fun reduce(previousState: DemoUiState, mutation: DemoMutation): DemoUiState =
        when (mutation) {
            is DemoMutation.SensorUpdated -> previousState.copy(latestSensorText = mutation.sensorText)
            is DemoMutation.BatteryUpdated -> previousState.copy(batteryPercent = mutation.percent)
            is DemoMutation.NoteAdded -> previousState.copy(userNotes = previousState.userNotes + "[Classic MVI] ${mutation.note}")
        }
}

// ========================================================================
// 4B. Version B: Pragmatic MVI (BaseUpdateStateViewModel with updateState)
// ========================================================================
class UpdateStateDemoViewModel(
    sensorUseCase: ContinuousSensorUseCase = ContinuousSensorUseCase(),
    batteryUseCase: BatteryLevelUseCase = BatteryLevelUseCase()
) : BaseUpdateStateViewModel<DemoIntent, DemoUiState, Nothing>(
    initialState = DemoUiState(architectureName = "Pragmatic MVI (updateState { copy(...) })")
) {
    init {
        // Collects external streams using launchWhileSubscribed to ensure 5-second unsubscription
        launchWhileSubscribed(sensorUseCase()) { sensorText ->
            updateState { copy(latestSensorText = sensorText) }
        }

        launchWhileSubscribed(batteryUseCase()) { percent ->
            updateState { copy(batteryPercent = percent) }
        }
    }

    override fun sendIntent(intent: DemoIntent) {
        when (intent) {
            is DemoIntent.AddNote -> {
                // Inlined mutation: no Mutation class or central reduce() needed
                updateState { copy(userNotes = userNotes + "[UpdateState] ${intent.note}") }
            }
        }
    }
}

// ========================================================================
// 5. Jetpack Compose Screen with Comparison Tabs
// ========================================================================
@Composable
fun MviComparisonScreen(
    classicViewModel: MviDemoViewModel = viewModel(),
    updateStateViewModel: UpdateStateDemoViewModel = viewModel()
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val classicState by classicViewModel.uiState.collectAsStateWithLifecycle()
    val updateStateState by updateStateViewModel.uiState.collectAsStateWithLifecycle()

    val currentUiState = if (selectedTab == 0) classicState else updateStateState
    val currentOnSendIntent: (DemoIntent) -> Unit = if (selectedTab == 0) {
        { classicViewModel.sendIntent(it) }
    } else {
        { updateStateViewModel.sendIntent(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E2E))
            .padding(24.dp)
    ) {
        Text(
            text = "MVI Comparison: Classic vs. updateState",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Compare how both architectures handle external sensor streams & state updates",
            fontSize = 13.sp,
            color = Color(0xFFA5A5BA),
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF2A2A3E),
            contentColor = Color.White
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("1. Classic MVI (merge + scan)") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("2. Pragmatic MVI (updateState)") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Active: ${currentUiState.architectureName}",
            fontSize = 14.sp,
            color = Color(0xFFFFC107),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Sensor Stream", fontSize = 13.sp, color = Color(0xFF8181A0))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentUiState.latestSensorText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Card(
                modifier = Modifier.weight(0.5f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A3E))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "EV Battery", fontSize = 13.sp, color = Color(0xFF8181A0))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${currentUiState.batteryPercent}%",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                currentOnSendIntent(DemoIntent.AddNote("Note #${System.currentTimeMillis() % 10000}"))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedTab == 0) Color(0xFF6750A4) else Color(0xFF00796B)
            )
        ) {
            Text(if (selectedTab == 0) "Dispatch Intent via Reducer (Classic)" else "Call updateState { copy(...) }")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "User Notes:",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(currentUiState.userNotes) { note ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF32324A))
                ) {
                    Text(text = note, color = Color.White, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

// ========================================================================
// 6. Host Activity
// ========================================================================
class Secondary2Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MviComparisonScreen()
            }
        }
    }
}
