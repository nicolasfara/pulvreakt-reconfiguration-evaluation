package it.nicolasfarabegoli.pulverization.components

import it.nicolasfarabegoli.pulverization.component.Context
import it.nicolasfarabegoli.pulverization.core.Behaviour
import it.nicolasfarabegoli.pulverization.core.BehaviourOutput
import it.nicolasfarabegoli.pulverization.runtime.componentsref.ActuatorsRef
import it.nicolasfarabegoli.pulverization.runtime.componentsref.CommunicationRef
import it.nicolasfarabegoli.pulverization.runtime.componentsref.SensorsRef
import it.nicolasfarabegoli.pulverization.runtime.componentsref.StateRef
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.inject

class DeviceBehaviour : Behaviour<Unit, Int, Int, Unit, Unit> {
    override val context: Context by inject()

    override fun invoke(state: Unit, export: List<Int>, sensedValues: Int): BehaviourOutput<Unit, Int, Unit, Unit> {
        return BehaviourOutput(Unit, 0, Unit, Unit)
    }
}

@Suppress("UnusedPrivateMember", "UNUSED_PARAMETER")
suspend fun deviceSmartphoneBehaviour(
    behaviour: Behaviour<Unit, Int, Int, Unit, Unit>,
    stateRef: StateRef<Unit>,
    commRef: CommunicationRef<Int>,
    sensorsRef: SensorsRef<Int>,
    actuatorsRef: ActuatorsRef<Unit>,
) = coroutineScope {
    val neighboursMessages = mutableListOf<Int>()
    val j1 = launch {
        commRef.receiveFromComponent().collect {
            neighboursMessages += it
        }
    }
    sensorsRef.receiveFromComponent().collect {
        val (_, message, _) = behaviour(Unit, neighboursMessages, it)
        commRef.sendToComponent(message)
    }
    j1.join()
}
