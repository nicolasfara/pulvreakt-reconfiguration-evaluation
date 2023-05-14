package it.nicolasfarabegoli.pulverization

import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.NodeProperty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.random.RandomGenerator

data class DischargeBattery(
    override val node: Node<Any>,
    private val ec: Environment<*, *>,
    private val random: RandomGenerator,
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
    private val isHome by GetMolecule

    private val deviceEPIValue: Double by lazy { node.getConcentration(deviceEPI) as Double * 1E-9 }
    private val behaviourInstructionsValue by lazy { node.getConcentration(behaviourInstructions) as Double * 1E6 }
    private val communicationInstructionsValue by lazy { node.getConcentration(communicationInstructions) as Double * 1E6 }
    private val intraCommInstructionsValue by lazy { node.getConcentration(intraCommInstructions) as Double * 1E6 }
    private val osDeviceInstructionsValue by lazy { node.getConcentration(osDeviceInstructions) as Double * 1E6 }
    private val sensorsInstructionsValue by lazy { node.getConcentration(sensorsInstructions) as Double * 1E6 }
    private val maxBatteryCapacityValue by lazy { node.getConcentration(maxBatteryCapacity) as Double }
    private val personalStartChargeThresholdValue by lazy { node.getConcentration(personalStartChargeThreshold) as Double }
    private val personalStopChargeThresholdValue by lazy { node.getConcentration(personalStopChargeThreshold) as Double }
    private val rechargeRateValue: Double by lazy { node.getConcentration(rechargeRate) as Double }

    private var prevTime = 0.0

    fun manageDeviceBattery() {
        val now = ec.simulation.time.toDouble()
        val delta = now - prevTime
        prevTime = now

        val isHomeValue = node.getConcentration(isHome) as Boolean
        val batteryPercentageValue = node.getConcentration(batteryPercentage) as Double
        val isChargingValue = node.getConcentration(isCharging) as Boolean
        val isChargingCondition =
            isHomeValue &&
                ((batteryPercentageValue <= personalStartChargeThresholdValue) || isChargingValue) &&
                (batteryPercentageValue <= personalStopChargeThresholdValue)
        node.setConcentration(isCharging, isChargingCondition)

        val newCharge = if (isChargingCondition) {
            val currentCapacityValue = node.getConcentration(currentCapacity) as Double
            recharge(currentCapacityValue, delta)
        } else {
            val currentCapacityValue = node.getConcentration(currentCapacity) as Double
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

        val osJoule = random.nextDouble() * osDeviceInstructionsValue * deviceEPIValue * delta
        val osWatt = osJoule.toWatt(delta)

        val totalWatt = behaviourWatt + communicationWatt + sensorsWatt + osWatt
        val totalMilliAmps = totalWatt.toMilliAmps(3.3) * 1000

        node.setConcentration(batteryConsumption, totalWatt)

        val newCharge = currentValue - (totalMilliAmps * delta / 3600.0)
        val currentPercentage = newCharge.toPercentage(maxBatteryCapacityValue)

        return if (currentPercentage < 0.0) {
            node.setConcentration(batteryConsumption, 0.0)
            0.0
        } else { newCharge }
    }

    private fun recharge(currentCharge: Double, delta: Double): Double {
        node.setConcentration(batteryConsumption, 0.0)
        val addingCharge = rechargeRateValue * delta / 3600.0
        val currentPercentage = currentCharge.toPercentage(maxBatteryCapacityValue)
        return if (currentPercentage >= personalStopChargeThresholdValue) {
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
