package it.nicolasfarabegoli.pulverization

import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.NodeProperty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

data class DischargeBattery(
    override val node: Node<Any>,
    private val ec: Environment<*, *>,
) : NodeProperty<Any> {
    private val deviceEPI by GetMolecule
    private val behaviourInstructions by GetMolecule
    private val communicationInstructions by GetMolecule
    private val sensorsInstructions by GetMolecule
    private val intraCommInstructions by GetMolecule
    private val osDeviceInstructions by GetMolecule
    private val behaviourInDevice by GetMolecule
    private val batteryConsumption by GetMolecule
    private val maxBatteryCapacity by GetMolecule
    private val personalStartChargeThreshold by GetMolecule
    private val personalStopChargeThreshold by GetMolecule
    private val isCharging by GetMolecule
    private val rechargeRate by GetMolecule
    private val currentCapacity by GetMolecule
    private val batteryPercentage by GetMolecule

    private val deviceEPIValue: Double by lazy { node.getConcentration(deviceEPI) as Double * 1E-9 }
    private val behaviourInstructionsValue: Double by lazy { node.getConcentration(behaviourInstructions) as Double * 1E6 }
    private val communicationInstructionsValue: Double by lazy { node.getConcentration(communicationInstructions) as Double * 1E6 }
    private val intraCommInstructionsValue: Double by lazy { node.getConcentration(intraCommInstructions) as Double * 1E6 }
    private val osDeviceInstructionsValue: Double by lazy { node.getConcentration(osDeviceInstructions) as Double * 1E6 }
    private val sensorsInstructionsValue: Double by lazy { node.getConcentration(sensorsInstructions) as Double * 1E6 }
    private val maxBatteryCapacityValue: Double by lazy { node.getConcentration(maxBatteryCapacity) as Double }
    private val personalStartChargeThresholdValue: Double by lazy { node.getConcentration(personalStartChargeThreshold) as Double }
    private val personalStopChargeThresholdValue: Double by lazy { node.getConcentration(personalStopChargeThreshold) as Double }
    private val rechargeRateValue: Double by lazy { node.getConcentration(rechargeRate) as Double }

    private var prevTime = 0.0
    private var restoreInDevice = true
    private var restoreInCloud = false

    fun manageDeviceBattery() {
        val now = ec.simulation.time.toDouble()
        val delta = now - prevTime
        prevTime = now
        val isChargingValue = node.getConcentration(isCharging) as Boolean
        val currentCapacityValue = node.getConcentration(currentCapacity) as Double
        val newCharge = if (isChargingValue) {
            restoreInDevice = node.getConcentration(behaviourInDevice) as Boolean
            restoreInCloud = node.getConcentration(behaviourInCloud) as Boolean
            recharge(currentCapacityValue, delta)
        } else {
            if (delta > 0.0) { discharge(currentCapacityValue, delta) } else { currentCapacityValue }
        }
        node.setConcentration(currentCapacity, newCharge)
        val percentage = newCharge.toPercentage(maxBatteryCapacityValue)
        node.setConcentration(batteryPercentage, percentage)
        propagateBatteryChangeEvent()
    }

    private fun discharge(currentValue: Double, delta: Double): Double {
        val behaviourInDevice = node.getConcentration(behaviourInDevice) as Boolean
        val behaviourJoule = if (behaviourInDevice) {
            deviceEPIValue * behaviourInstructionsValue * delta
        } else { deviceEPIValue * intraCommInstructionsValue * delta }
        val behaviourWatt = behaviourJoule.toWatt(delta)

        val communicationJoule = deviceEPIValue * communicationInstructionsValue * delta
        val communicationWatt = communicationJoule.toWatt(delta)

        val sensorsJoule = deviceEPIValue * sensorsInstructionsValue * delta
        val sensorsWatt = sensorsJoule.toWatt(delta)

        val osJoule = Random.nextDouble(osDeviceInstructionsValue) * deviceEPIValue * delta
        val osWatt = osJoule.toWatt(delta)

        val totalWatt = behaviourWatt + communicationWatt + sensorsWatt + osWatt
        val totalMilliAmps = totalWatt.toMilliAmps(3.3) * 1000

        node.setConcentration(batteryConsumption, totalWatt)

        val newCharge = currentValue - (totalMilliAmps * delta / 3600.0)
        val currentPercentage = newCharge.toPercentage(maxBatteryCapacityValue)

        return if (currentPercentage <= personalStartChargeThresholdValue) {
            node.setConcentration(isCharging, true)
            personalStartChargeThresholdValue.toCharge(maxBatteryCapacityValue)
        } else { newCharge }
    }

    private fun recharge(currentCharge: Double, delta: Double): Double {
        node.setConcentration(batteryConsumption, 0.0)
        node.setConcentration(behaviourInDevice, false)
        node.setConcentration(behaviourInCloud, false)
        val addingCharge = rechargeRateValue * delta / 3600.0
        val currentPercentage = currentCharge.toPercentage(maxBatteryCapacityValue)
        return if (currentPercentage >= personalStopChargeThresholdValue) {
            node.setConcentration(isCharging, false)
            node.setConcentration(behaviourInDevice, restoreInDevice)
            node.setConcentration(behaviourInCloud, restoreInCloud)
            personalStopChargeThresholdValue.toCharge(maxBatteryCapacityValue)
        } else { currentCharge + addingCharge }
    }

    private fun propagateBatteryChangeEvent() {
        val lowBatteryReconfigurator = node.asProperty(OnLowBattery::class)
        val highBatteryReconfigurator = node.asProperty(OnHighBattery::class)
        val currentCapacityConcentration = node.getConcentration(batteryPercentage) as Double
        runBlocking {
            lowBatteryReconfigurator.updateBattery(currentCapacityConcentration)
            highBatteryReconfigurator.updateBattery(currentCapacityConcentration)

            // Needed for synchronize Alchemist with the pulverization framework
            highBatteryReconfigurator.results.first()
            lowBatteryReconfigurator.results.first()
        }
    }

    private fun Double.toWatt(delta: Double) = this / delta
    private fun Double.toMilliAmps(volts: Double) = this / volts
    private fun Double.toPercentage(max: Double) = this / max * 100.0
    private fun Double.toCharge(max: Double) = this * max / 100.0

    override fun cloneOnNewNode(node: Node<Any>): NodeProperty<Any> = TODO("Not yet implemented")
}
