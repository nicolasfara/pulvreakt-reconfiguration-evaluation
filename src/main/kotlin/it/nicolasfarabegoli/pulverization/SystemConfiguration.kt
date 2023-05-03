package it.nicolasfarabegoli.pulverization

import it.nicolasfarabegoli.pulverization.dsl.model.Behaviour
import it.nicolasfarabegoli.pulverization.dsl.model.Capability
import it.nicolasfarabegoli.pulverization.dsl.model.Communication
import it.nicolasfarabegoli.pulverization.dsl.model.Sensors
import it.nicolasfarabegoli.pulverization.dsl.pulverizationSystem

object SmartphoneDevice : Capability
object HighCPU : Capability

val systemConfiguration = pulverizationSystem {
    device("smartphone") {
        Behaviour and Communication deployableOn setOf(SmartphoneDevice, HighCPU)
        Sensors deployableOn SmartphoneDevice
    }
}
