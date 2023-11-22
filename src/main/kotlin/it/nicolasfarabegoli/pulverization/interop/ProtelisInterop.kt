@file:Suppress("UNCHECKED_CAST")

package it.nicolasfarabegoli.pulverization.interop

import it.nicolasfarabegoli.pulverization.*
import it.nicolasfarabegoli.pulverization.runtime.PulverizationRuntime
import it.unibo.alchemist.boundary.OutputMonitor
import it.unibo.alchemist.core.Simulation
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.GeoPosition
import it.unibo.alchemist.model.Position
import it.unibo.alchemist.model.Time
import it.unibo.alchemist.model.protelis.AlchemistExecutionContext
import it.unibo.alchemist.protelis.properties.ProtelisDevice
import it.unibo.alchemist.model.Node.Companion.asProperty

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.max

object ProtelisInterop {
    private val initialized = ConcurrentHashMap.newKeySet<AlchemistExecutionContext<*>>()
    private var maxSize: Int = 0

    @JvmStatic
    fun AlchemistExecutionContext<*>.moveOnMap() {
        val device = (deviceUID as ProtelisDevice<*>)
        val move = device.node.asProperty<Any, MoveOnMap>()
        move.moveOnMapRound()
    }

    @JvmStatic
    fun AlchemistExecutionContext<*>.manageBattery() {
        val device = (deviceUID as ProtelisDevice<*>)
        val batteryManager = device.node.asProperty<Any, DischargeBattery>()
        batteryManager.manageDeviceBattery()
    }

    @JvmStatic
    fun AlchemistExecutionContext<*>.updateCloudCosts() {
        val device = (deviceUID as ProtelisDevice<*>)
        val cloudConsumption by GetMolecule
        val maxInstancePower by GetMolecule
        val instanceCost by GetMolecule
        val cloudCost by GetMolecule
        val cloudConsumptionValue = device.node.getConcentration(cloudConsumption) as Double
        val maxInstancePowerValue = device.node.getConcentration(maxInstancePower) as Double
        val instanceCostValue = device.node.getConcentration(instanceCost) as Double

        val cost = ceil(cloudConsumptionValue / maxInstancePowerValue) * instanceCostValue

        device.node.setConcentration(cloudCost, cost)
    }

    @JvmStatic
    fun AlchemistExecutionContext<*>.onBatteryChangeEvent() {
        val device = (deviceUID as ProtelisDevice<*>)
        val lowBatteryReconfigurator = device.node.asProperty<Any, OnLowBattery>()
        val highBatteryReconfigurator = device.node.asProperty<Any, OnHighBattery>()
        val batteryPercentage by GetMolecule
        val currentCapacityConcentration = device.node.getConcentration(batteryPercentage) as Double
        runBlocking {
            lowBatteryReconfigurator.updateBattery(currentCapacityConcentration)
            highBatteryReconfigurator.updateBattery(currentCapacityConcentration)

            // Needed for synchronize Alchemist with the pulverization framework
            highBatteryReconfigurator.results.first()
            lowBatteryReconfigurator.results.first()
        }
    }

    @JvmStatic
    fun <P : Position<P>> AlchemistExecutionContext<P>.startPulverization() {
        if (this !in initialized) {
            initialized.add(this)
            val device = (deviceUID as ProtelisDevice<*>)
            val lowBatteryReconfiguration = device.node.asProperty<Any, OnLowBattery>()
            val highBatteryReconfiguration = device.node.asProperty<Any, OnHighBattery>()
            runBlocking {
                val config = configureRuntime(lowBatteryReconfiguration, highBatteryReconfiguration)
                val runtime = PulverizationRuntime(device.id.toString(), "smartphone", config)
                runtime.start()
                val sim: Simulation<Any, GeoPosition> = environmentAccess.simulation as Simulation<Any, GeoPosition>
                sim.addOutputMonitor(object : OutputMonitor<Any, GeoPosition> {
                    override fun finished(environment: Environment<Any, GeoPosition>, time: Time, step: Long) {
                        runBlocking {
                            runtime.stop()
                            lowBatteryReconfiguration.close()
                            highBatteryReconfiguration.close()
                        }
                        check(initialized.remove(this@startPulverization)) {
                            "Cleanup failure! for $this"
                        }
                    }
                })
            }
            val entries = initialized.size
            maxSize = max(maxSize, entries)
            if ((deviceUID as ProtelisDevice<P>).id == 10) {
                println("Cache size: $entries, max cache size ever reached: $maxSize")
            }
        }
    }
}
