package it.nicolasfarabegoli.pulverization

import it.unibo.alchemist.model.implementations.environments.OSMEnvironment
import it.unibo.alchemist.model.interfaces.GeoPosition
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.NodeProperty
import it.unibo.alchemist.model.interfaces.Time
import org.protelis.lang.datatype.impl.ArrayTupleImpl
import kotlin.random.Random

class MoveOnMap(override val node: Node<Any>, private val environment: OSMEnvironment<Any>) : NodeProperty<Any> {
    private val target by GetMolecule
    private val home by GetMolecule
    private val batteryPercentage by GetMolecule
    private val personalStartChargeThreshold by GetMolecule
    private val isCharging by GetMolecule
    private val distance by GetMolecule
    private val isHome by GetMolecule
    private val minLat by GetMolecule
    private val minLatValue by lazy { node.getConcentration(minLat) as Double }
    private val maxLat by GetMolecule
    private val maxLatValue by lazy { node.getConcentration(maxLat) as Double }
    private val minLon by GetMolecule
    private val minLonValue by lazy { node.getConcentration(minLon) as Double }
    private val maxLon by GetMolecule
    private val maxLonValue by lazy { node.getConcentration(maxLon) as Double }
    private val cityPOIs by GetMolecule
    private val cityPOIsValue by lazy { node.getConcentration(cityPOIs) as ArrayTupleImpl }

    private val routingService = environment.routingService
    private var prevTime = 0.0
    private var isFirstRun = true
    private var stillTime = Time.ZERO
    private var nexPoiTimeValue = nextPoiTime()

    fun moveOnMapRound() {
        val now = environment.simulation.time.toDouble()
        val delta = now - prevTime
        prevTime = now

        if (isFirstRun) {
            isFirstRun = false
            setupHome()
            getNextRandomPOI()
        }

        val batteryPercentageValue = node.getConcentration(batteryPercentage) as Double
        val personalStartChargeThresholdValue = node.getConcentration(personalStartChargeThreshold) as Double
        val homeValue = node.getConcentration(home) as GeoPosition
        val isChargingValue = node.getConcentration(isCharging) as Boolean
        val isHomeValue = node.getConcentration(isHome) as Boolean

        // Determine if I reached the home
        node.setConcentration(isHome, getDevicePosition() == homeValue)

        // Determine the travelled distance
        val actualDistance = node.getConcentration(distance) as Double
        val travelledDistance =
            if (!isHomeValue && !isChargingValue && batteryPercentageValue > 0.0) { 1.4 * delta / 1000.0 } else { 0.0 }
        val newTotalDistance = actualDistance + travelledDistance
        node.setConcentration(distance, newTotalDistance)

        val time = (environment.simulation.time - stillTime).toDouble()
        if (time > nexPoiTimeValue) {
            val poiReached = getDevicePosition() == node.getConcentration(target) as GeoPosition
            if (poiReached && !isChargingValue) { getNextRandomPOI(); nexPoiTimeValue = nextPoiTime() } else {
                if (batteryPercentageValue <= personalStartChargeThresholdValue) {
                    node.setConcentration(target, homeValue)
                }
            }
            stillTime = environment.simulation.time
        }
    }

    private fun nextPoiTime() = Random.nextDouble(MIN_POI_TIME, MAX_POI_TIME)

    private fun getNextRandomPOI() {
        val selectedPOI = cityPOIsValue.toList().random() as ArrayTupleImpl
        val newTarget = routingService.allowedPointClosestTo(
            environment.makePosition(selectedPOI[0] as Double, selectedPOI[1] as Double),
        ) ?: getDevicePosition()
        node.setConcentration(target, newTarget)
    }

    private fun setupHome() {
        val lat = Random.nextDouble(minLatValue, maxLatValue)
        val lon = Random.nextDouble(minLonValue, maxLonValue)
        val homeCoordinates = routingService.allowedPointClosestTo(environment.makePosition(lat, lon))
            ?: getDevicePosition()
        node.setConcentration(home, homeCoordinates)
    }

    private fun getDevicePosition() = environment.getPosition(node)

    override fun cloneOnNewNode(node: Node<Any>): NodeProperty<Any> = TODO("Not yet implemented")

    companion object {
        private const val MIN_POI_TIME = 5 * 60.0
        private const val MAX_POI_TIME = 30 * 60.0
    }
}
