package it.nicolasfarabegoli.pulverization

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import it.nicolasfarabegoli.pulverization.components.DeviceBehaviour
import it.nicolasfarabegoli.pulverization.components.DeviceCommunication
import it.nicolasfarabegoli.pulverization.components.DeviceSensorsContainer
import it.nicolasfarabegoli.pulverization.components.deviceCommunicationLogic
import it.nicolasfarabegoli.pulverization.components.deviceSensorsLogic
import it.nicolasfarabegoli.pulverization.components.deviceSmartphoneBehaviour
import it.nicolasfarabegoli.pulverization.dsl.model.Behaviour
import it.nicolasfarabegoli.pulverization.dsl.model.Capability
import it.nicolasfarabegoli.pulverization.dsl.model.Communication
import it.nicolasfarabegoli.pulverization.dsl.model.ComponentType
import it.nicolasfarabegoli.pulverization.dsl.model.Sensors
import it.nicolasfarabegoli.pulverization.runtime.communication.Binding
import it.nicolasfarabegoli.pulverization.runtime.communication.Communicator
import it.nicolasfarabegoli.pulverization.runtime.communication.RemotePlace
import it.nicolasfarabegoli.pulverization.runtime.communication.RemotePlaceProvider
import it.nicolasfarabegoli.pulverization.runtime.context.ExecutionContext
import it.nicolasfarabegoli.pulverization.runtime.dsl.model.DeploymentUnitRuntimeConfiguration
import it.nicolasfarabegoli.pulverization.runtime.dsl.model.Host
import it.nicolasfarabegoli.pulverization.runtime.dsl.pulverizationRuntime
import it.nicolasfarabegoli.pulverization.runtime.reconfiguration.NewConfiguration
import it.nicolasfarabegoli.pulverization.runtime.reconfiguration.Reconfigurator
import it.unibo.alchemist.model.implementations.molecules.SimpleMolecule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.reflect.KProperty

object Server : Host {
    override val hostname: String = "cloud"
    override val capabilities: Set<Capability> = setOf(HighCPU)
}

object Smartphone : Host {
    override val hostname: String = "smartphone"
    override val capabilities: Set<Capability> = setOf(SmartphoneDevice)
}

object GetMolecule {
    operator fun getValue(thisRef: Any?, property: KProperty<*>) = SimpleMolecule(property.name)
}

val hosts = setOf(Server, Smartphone)
val behaviourInDevice by GetMolecule
val communicationInDevice by GetMolecule
val sensorsInDevice by GetMolecule

suspend fun configureRuntime(
    reconfigurationEvent: OnLowBattery,
): DeploymentUnitRuntimeConfiguration<Unit, Int, Int, Unit, Unit> {
    Logger.setMinSeverity(Severity.Error)
    return pulverizationRuntime(systemConfiguration, "smartphone", hosts) {
        DeviceBehaviour() withLogic ::deviceSmartphoneBehaviour startsOn Smartphone
        DeviceCommunication() withLogic ::deviceCommunicationLogic startsOn Server
        DeviceSensorsContainer() withLogic ::deviceSensorsLogic startsOn Smartphone

        withCommunicator {
            object : Communicator {
                override suspend fun finalize() { }
                override suspend fun fireMessage(message: ByteArray) { }
                override fun receiveMessage(): Flow<ByteArray> = emptyFlow()
                override suspend fun setup(binding: Binding, remotePlace: RemotePlace?) { }
            }
        }

        withReconfigurator {
            object : Reconfigurator {
                override fun receiveReconfiguration(): Flow<NewConfiguration> = emptyFlow()
                override suspend fun reconfigure(newConfiguration: NewConfiguration) {
                    val componentMolecule = when (newConfiguration.first) {
                        Behaviour -> behaviourInDevice
                        Communication -> communicationInDevice
                        Sensors -> sensorsInDevice
                        else -> error("Invalid component type")
                    }
                    reconfigurationEvent.node.setConcentration(componentMolecule, false)
                }
            }
        }
        withRemotePlaceProvider {
            object : RemotePlaceProvider {
                override val context: ExecutionContext
                    get() = TODO("Not yet implemented")
                override fun get(type: ComponentType): RemotePlace? = null
            }
        }

        reconfigurationRules {
            onDevice {
                reconfigurationEvent reconfigures { Behaviour movesTo Server }
            }
        }
    }
}
