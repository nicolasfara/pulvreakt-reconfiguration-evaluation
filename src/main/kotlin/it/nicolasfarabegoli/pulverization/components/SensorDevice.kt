package it.nicolasfarabegoli.pulverization.components

import it.nicolasfarabegoli.pulverization.component.Context
import it.nicolasfarabegoli.pulverization.core.Sensor
import it.nicolasfarabegoli.pulverization.core.SensorsContainer
import it.nicolasfarabegoli.pulverization.runtime.componentsref.BehaviourRef
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.inject

class SensorDevice : Sensor<Int> {
    override suspend fun sense(): Int = 0
}

class DeviceSensorsContainer : SensorsContainer() {
    override val context: Context by inject()

    override suspend fun initialize() {
        this += SensorDevice()
    }
}

@Suppress("UNUSED_PARAMETER")
suspend fun deviceSensorsLogic(
    sensors: SensorsContainer,
    behaviourRef: BehaviourRef<Int>
) = coroutineScope {
    sensors.get<SensorDevice> {
//        while (true) {
//            behaviourRef.sendToComponent(sense())
//            delay(100.milliseconds)
//        }
    }
}
