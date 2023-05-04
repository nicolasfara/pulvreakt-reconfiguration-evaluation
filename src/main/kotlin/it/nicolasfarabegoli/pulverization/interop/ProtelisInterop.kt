package it.nicolasfarabegoli.pulverization.interop

import it.nicolasfarabegoli.pulverization.GetMolecule
import it.nicolasfarabegoli.pulverization.OnHighBattery
import it.nicolasfarabegoli.pulverization.OnLowBattery
import it.nicolasfarabegoli.pulverization.configureRuntime
import it.nicolasfarabegoli.pulverization.runtime.PulverizationRuntime
import it.unibo.alchemist.model.implementations.properties.ProtelisDevice
import it.unibo.alchemist.model.interfaces.Node.Companion.asProperty
import it.unibo.alchemist.protelis.AlchemistExecutionContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.WeakHashMap

object ProtelisInterop {
    private val initialized: WeakHashMap<AlchemistExecutionContext<*>, Any> = WeakHashMap()

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
    fun AlchemistExecutionContext<*>.startPulverization() {
        if (this !in initialized) {
            initialized[this] = true
            val device = (deviceUID as ProtelisDevice<*>)
            val lowBatteryReconfiguration = device.node.asProperty<Any, OnLowBattery>()
            val highBatteryReconfiguration = device.node.asProperty<Any, OnHighBattery>()
            runBlocking {
                val config = configureRuntime(lowBatteryReconfiguration, highBatteryReconfiguration)
                val runtime = PulverizationRuntime(device.id.toString(), "smartphone", config)
                runtime.start()
            }
        }
    }
}
