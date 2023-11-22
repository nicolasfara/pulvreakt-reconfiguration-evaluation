package it.nicolasfarabegoli.pulverization.interop

import it.nicolasfarabegoli.pulverization.GetMolecule
import it.unibo.alchemist.boundary.extractors.AbstractDoubleExporter
import it.unibo.alchemist.model.Actionable
import it.unibo.alchemist.model.Environment
import it.unibo.alchemist.model.Time

import kotlin.math.ceil

class CloudCost @JvmOverloads constructor(
    precision: Int? = null,
) : AbstractDoubleExporter(precision) {
    override val columnNames: List<String> = listOf("cloudCost", "instances")
    private var maxInstancePowerValue: Double? = null
    private var instanceCostValue: Double? = null

    override fun <T> extractData(
        environment: Environment<T, *>,
        reaction: Actionable<T>?,
        time: Time,
        step: Long,
    ): Map<String, Double> {
        val cloudConsumption by GetMolecule
        val maxInstancePower by GetMolecule
        val instanceCost by GetMolecule
        val cloudConsumptionValue = environment.nodes.sumOf { it.getConcentration(cloudConsumption) as Double }
        maxInstancePowerValue =
            maxInstancePowerValue ?: environment.nodes.first().getConcentration(maxInstancePower) as Double
        instanceCostValue = instanceCostValue ?: environment.nodes.first().getConcentration(instanceCost) as Double
        val instances = ceil(cloudConsumptionValue / maxInstancePowerValue as Double)
        val cost = instances * instanceCostValue as Double
        return mapOf("cloudCost" to cost, "instances" to instances)
    }
}
