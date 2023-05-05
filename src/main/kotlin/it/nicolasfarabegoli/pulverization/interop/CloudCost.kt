package it.nicolasfarabegoli.pulverization.interop

import it.nicolasfarabegoli.pulverization.GetMolecule
import it.unibo.alchemist.loader.export.extractors.AbstractDoubleExporter
import it.unibo.alchemist.model.interfaces.Actionable
import it.unibo.alchemist.model.interfaces.Environment
import it.unibo.alchemist.model.interfaces.Time
import kotlin.math.ceil

class CloudCost @JvmOverloads constructor(
    precision: Int? = null,
) : AbstractDoubleExporter(precision) {
    override val columnNames: List<String> = listOf("cloudCost", "instances")

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
        val maxInstancePowerValue = environment.nodes.first().getConcentration(maxInstancePower) as Double
        val instanceCostValue = environment.nodes.first().getConcentration(instanceCost) as Double
        val instances = ceil(cloudConsumptionValue / maxInstancePowerValue)
        val cost = instances * instanceCostValue
        return mapOf("cloudCost" to cost, "instances" to instances)
    }
}
