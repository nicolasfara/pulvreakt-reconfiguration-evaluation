package it.nicolasfarabegoli.pulverization.interop

import it.nicolasfarabegoli.pulverization.DischargeBattery
import it.nicolasfarabegoli.pulverization.GetMolecule
import it.nicolasfarabegoli.pulverization.OnHighBattery
import it.nicolasfarabegoli.pulverization.OnLowBattery
import it.nicolasfarabegoli.pulverization.configureRuntime
import it.nicolasfarabegoli.pulverization.runtime.PulverizationRuntime
import it.unibo.alchemist.boundary.interfaces.OutputMonitor
import it.unibo.alchemist.core.interfaces.Simulation
import it.unibo.alchemist.model.implementations.properties.ProtelisDevice
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Node.Companion.asProperty
import it.unibo.alchemist.model.interfaces.Position
import it.unibo.alchemist.model.interfaces.Time
import it.unibo.alchemist.protelis.AlchemistExecutionContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

object ProtelisInterop {
    private val initialized: WeakHashMap<AlchemistExecutionContext<*>, Any> = WeakHashMap()

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

            withTimeoutOrNull(100.milliseconds) {
                // Needed for synchronize Alchemist with the pulverization framework
                highBatteryReconfigurator.results.first()
                lowBatteryReconfigurator.results.first()
            }
        }
    }

    @JvmStatic
    fun <P : Position<P>> AlchemistExecutionContext<P>.startPulverization() {
        if (this !in initialized) {
            initialized[this] = true
            val device = (deviceUID as ProtelisDevice<*>)
            val lowBatteryReconfiguration = device.node.asProperty<Any, OnLowBattery>()
            val highBatteryReconfiguration = device.node.asProperty<Any, OnHighBattery>()
            runBlocking {
                val config = configureRuntime(lowBatteryReconfiguration, highBatteryReconfiguration)
                val runtime = PulverizationRuntime(device.id.toString(), "smartphone", config)
                runtime.start()
                val sim: Simulation<Any, P> = environmentAccess.simulation
                sim.addOutputMonitor(object : OutputMonitor<Any, P> {
                    override fun finished(environment: Environment<Any, P>, time: Time, step: Long) {
                        runBlocking {
                            runtime.stop()
                            lowBatteryReconfiguration.close()
                            highBatteryReconfiguration.close()
                        }
                    }
                })
            }
        }
    }
}
