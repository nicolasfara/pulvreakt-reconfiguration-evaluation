package it.nicolasfarabegoli.pulverization

import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.NodeProperty
import kotlin.random.Random

data class DischargeBattery(override val node: Node<Any>) : NodeProperty<Any> {
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

    private val deviceEPIValue: Double by lazy { node.getConcentration(deviceEPI) as Double / 1000000000.0 }
    private val behaviourInstructionsValue: Int by lazy { node.getConcentration(behaviourInstructions) as Int * 1000000 }
    private val communicationInstructionsValue: Int by lazy { node.getConcentration(communicationInstructions) as Int * 1000000 }
    private val intraCommInstructionsValue: Int by lazy { node.getConcentration(intraCommInstructions) as Int * 1000000 }
    private val osDeviceInstructionsValue: Int by lazy { node.getConcentration(osDeviceInstructions) as Int * 1000000 }
    private val sensorsInstructionsValue: Int by lazy { node.getConcentration(sensorsInstructions) as Int * 1000000 }
    private val maxBatteryCapacityValue: Double by lazy { node.getConcentration(maxBatteryCapacity) as Double }
    private val personalStartChargeThresholdValue: Double by lazy { node.getConcentration(personalStartChargeThreshold) as Double }
    private val personalStopChargeThresholdValue: Double by lazy { node.getConcentration(personalStopChargeThreshold) as Double }
    private val rechargeRateValue: Double by lazy { node.getConcentration(rechargeRate) as Double }

    private var lastTime = 0.0

    fun manageDeviceBattery() {
        val now = this.
    }

    private fun discharge(currentValue: Double, delta: Double): Double {
        val behaviourInDevice = node.getConcentration(behaviourInDevice) as Boolean
        val behaviourJoule = if (behaviourInDevice) {
            deviceEPIValue * behaviourInstructionsValue
        } else { deviceEPIValue * intraCommInstructionsValue }
        val behaviourWatt = behaviourJoule.toWatt(delta)

        val communicationJoule = deviceEPIValue * communicationInstructionsValue
        val communicationWatt = communicationJoule.toWatt(delta)

        val sensorsJoule = deviceEPIValue * sensorsInstructionsValue
        val sensorsWatt = sensorsJoule.toWatt(delta)

        val osJoule = Random.nextInt(osDeviceInstructionsValue) * deviceEPIValue
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
        val addingCharge = rechargeRateValue * delta / 3600.0
        val currentPercentage = currentCharge.toPercentage(maxBatteryCapacityValue)
        return if (currentPercentage >= personalStopChargeThresholdValue) {
            node.setConcentration(isCharging, false)
            personalStopChargeThresholdValue.toCharge(maxBatteryCapacityValue)
        } else { currentCharge + addingCharge }
    }

    private fun Double.toWatt(delta: Double) = this / delta
    private fun Double.toMilliAmps(volts: Double) = this / volts
    private fun Double.toPercentage(max: Double) = this / max * 100.0
    private fun Double.toCharge(max: Double) = this * max / 100.0

    override fun cloneOnNewNode(node: Node<Any>): NodeProperty<Any> {
        TODO("Not yet implemented")
    }
}
