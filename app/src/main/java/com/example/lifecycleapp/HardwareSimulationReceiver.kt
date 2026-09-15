package com.example.lifecycleapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver for adb-driven Hardware Simulation.
 *
 * Example adb commands:
 * 1. Simulating Passenger Occupancy Sensor (Item isVisible test):
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --ez passenger_present false
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --ez passenger_present true
 *
 * 2. Simulating Vehicle Driving Lockout (ValueWithState isEnabled test):
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --ei speed 60
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --ei speed 0
 *
 * 3. Simulating Option Visibility (ValueWithState isVisible test):
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --es item seat_massage --es hide_options STRETCH
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --es item seat_massage --es hide_options none
 *
 * 4. Simulating Physical Hardware Switch / HAL Value update (ValueWithState isSelected test):
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --es item driver_seat_heat --es value "LEVEL 2"
 *    adb shell am broadcast -a com.example.carsettings.SIMULATE_HARDWARE --es item auto_lock --es value false
 */
class HardwareSimulationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SIMULATE_HARDWARE = "com.example.carsettings.SIMULATE_HARDWARE"
        private const val TAG = "HardwareSimReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SIMULATE_HARDWARE) {
            Log.i(TAG, "Received hardware simulation broadcast: $intent")
            VehicleHardwareSimulator.applySimulation(intent)
        }
    }
}
