package it.nicolasfarabegoli.pulverization.components

import it.nicolasfarabegoli.pulverization.component.Context
import it.nicolasfarabegoli.pulverization.core.Communication
import it.nicolasfarabegoli.pulverization.runtime.componentsref.BehaviourRef
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.koin.core.component.inject

class DeviceCommunication : Communication<Int> {
    override val context: Context by inject()

    override fun receive(): Flow<Int> = emptyFlow()

    override suspend fun send(payload: Int) { }
}

suspend fun deviceCommunicationLogic(
    communication: Communication<Int>,
    behaviourRef: BehaviourRef<Int>,
) = coroutineScope {
    val j1 = launch {
        communication.receive().collect {
            behaviourRef.sendToComponent(it)
        }
    }
    val j2 = launch {
        behaviourRef.receiveFromComponent().collect {
            communication.send(it)
        }
    }
    j1.join()
    j2.join()
}