package it.nicolasfarabegoli.pulverization

import it.nicolasfarabegoli.pulverization.runtime.dsl.model.ReconfigurationEvent
import it.unibo.alchemist.model.interfaces.Node
import it.unibo.alchemist.model.interfaces.NodeProperty
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*

data class OnLowBattery(override val node: Node<Any>) : ReconfigurationEvent<Double>(), NodeProperty<Any> {
    private val flow = MutableSharedFlow<Double>(1)
    private val lowBatteryReconfiguration by GetMolecule

    override val events: Flow<Double> = flow.asSharedFlow()
    override val predicate: (Double) -> Boolean = { it < node.getConcentration(lowBatteryReconfiguration) as Double }
    suspend fun updateBattery(newValue: Double) = flow.emit(newValue)
    suspend fun close() = flow.emit(Double.NaN)

    override fun cloneOnNewNode(node: Node<Any>): NodeProperty<Any> = TODO("Not yet implemented")
}
