package cloud.skadi.web.hosting.k8s

import com.fkorotkov.kubernetes.*
import com.fkorotkov.kubernetes.networking.v1beta1.*
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress
import cloud.skadi.web.hosting.INSTANCE_HOST
import cloud.skadi.web.hosting.getEnvOrDefault
import com.fkorotkov.kubernetes.apps.*
import java.util.*


private val containerRegistry = getEnvOrDefault( "CONTAINER_REGISTRY","rg.nl-ams.scw.cloud/cloud-skadi-mps")
private val containerImageName = ""

fun UUID.appLabel() = mapOf("app" to "mps-instance-$this")

fun pvcName(id: UUID) = "pvc-mps-instance-$id"
class MPSInstancePVC(id: UUID) : PersistentVolumeClaim() {
    init {
        metadata {
            name = pvcName(id)
            labels = id.appLabel()
        }
        spec {
            accessModes = listOf("ReadWriteOnce")
            resources {
                requests = mapOf("storage" to Quantity("10Gi"))
            }
        }
    }
}

fun serviceName(id: UUID) = "svc-mps-instance-$id"
class MPSInstanceService(id: UUID) : Service() {
    init {
        metadata {
            name = serviceName(id)
            labels = id.appLabel()
        }
        spec {
            ports = listOf(newServicePort {
                name = "http"
                port = 80
                targetPort = IntOrString(8887)
                protocol = "TCP"
            })
            selector = id.appLabel()
        }
    }
}

fun deploymentName(id: UUID) = "mps-instance-$id"
fun containerImage(kernelFVersion: String) = "$containerRegistry/$kernelFVersion:latest"
class MPSInstanceDeployment(id: UUID, kernelFVersion: String, rwToken: String, roToken: String) : Deployment() {
    init {
        metadata {
            name = deploymentName(id)
            labels = id.appLabel()
        }
        spec {
            replicas = 1
            strategy = newDeploymentStrategy {
                type = "Recreate"
            }
            selector {
                matchLabels = id.appLabel()
            }
            template {
                metadata { labels = id.appLabel() }
                spec {
                    securityContext { fsGroup = 1024 }
                    containers = listOf(newContainer {
                        name = "mps-instance-$id"
                        image = containerImage(kernelFVersion)
                        imagePullPolicy = "Always"
                        ports = listOf(newContainerPort { containerPort = 8887 })
                        env = listOf(
                            newEnvVar {
                                name = "SKADI_INSTANCE_ID"
                                value = id.toString()
                            },
                            newEnvVar {
                                name = "SKADI_BACKEND_ADDRESS"
                                value = "heartbeat"
                            },
                            newEnvVar {
                                name = "ORG_JETBRAINS_PROJECTOR_SERVER_HANDSHAKE_TOKEN"
                                value = rwToken
                            },
                            newEnvVar {
                                name = "ORG_JETBRAINS_PROJECTOR_SERVER_RO_HANDSHAKE_TOKEN"
                                value = roToken
                            }

                        )
                        resources {
                            requests = mapOf(
                                "cpu" to Quantity("1500m"),
                                "memory" to Quantity("2Gi")
                            )
                            limits = mapOf(
                                "cpu" to Quantity("3000m"),
                                "memory" to Quantity("2.2Gi")
                            )

                        }
                        volumeMounts = listOf(newVolumeMount {
                            name = "mps-instance-$id-volume"
                            mountPath = "/home/projector-user"
                        })
                        readinessProbe {
                            httpGet = newHTTPGetAction {
                                path = "/"
                                port = IntOrString(8887)
                                scheme = "HTTP"
                            }
                            initialDelaySeconds = 10
                            periodSeconds = 10
                            timeoutSeconds = 5
                        }
                        livenessProbe {
                            httpGet = newHTTPGetAction {
                                path = "/"
                                port = IntOrString(8887)
                                scheme = "HTTP"
                            }
                            initialDelaySeconds = 120
                            periodSeconds = 30
                            timeoutSeconds = 5
                        }
                    })
                    volumes = listOf(newVolume {
                        name = "mps-instance-$id-volume"
                        persistentVolumeClaim {
                            claimName = pvcName(id)
                        }
                    })
                    imagePullSecrets = listOf(newLocalObjectReference {
                        name = "registry-secret"
                    })
                }
            }
        }
    }


}

fun ingressName(id: UUID) = "ingress-mps-instance-$id"
class MPSInstanceIngress(id: UUID) : Ingress() {
    init {
        metadata { name = ingressName(id) }
        spec {
            rules = listOf(newIngressRule {
                host = "$id.$INSTANCE_HOST"
                http = newHTTPIngressRuleValue {
                    paths = listOf(newHTTPIngressPath {
                        path = "/"
                        pathType = "Prefix"
                        backend {
                            serviceName = serviceName(id)
                            servicePort = IntOrString(80)
                        }
                    })
                }
            })
        }
    }
}