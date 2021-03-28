package cloud.skadi.web.hosting.k8s

import com.fkorotkov.kubernetes.*
import com.fkorotkov.kubernetes.apps.metadata
import com.fkorotkov.kubernetes.apps.selector
import com.fkorotkov.kubernetes.apps.spec
import com.fkorotkov.kubernetes.apps.template
import com.fkorotkov.kubernetes.networking.v1beta1.*
import io.fabric8.kubernetes.api.model.IntOrString
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress
import cloud.skadi.web.hosting.HOST_URL
import java.util.*


fun UUID.appLabel() = mapOf("app" to "kernelf-instance-$this")

fun pvcName(id: UUID) = "pvc-kernelf-instance-$id"
class KernelFInstancePVC(id: UUID) : PersistentVolumeClaim() {
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

fun serviceName(id: UUID) = "svc-kernef-instance-$id"
class KernelFInstanceService(id: UUID) : Service() {
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

fun deploymentName(id:UUID) = "kernelf-instance-$id"
class KernelFInstanceDeployment(id: UUID, kernelFVersion: String) : Deployment() {
    init {
        metadata {
            name = deploymentName(id)
            labels = id.appLabel()
        }
        spec {
            replicas = 1
            selector {
                matchLabels = id.appLabel()
            }
            template {
                metadata { labels = id.appLabel() }
                spec {
                    securityContext { fsGroup = 1024 }
                    containers = listOf(newContainer {
                        name = "kernelf-instance-$id"
                        image = "rg.nl-ams.scw.cloud/kernelf-logv-ws/kernelf:$kernelFVersion"
                        imagePullPolicy = "Always"
                        ports = listOf(newContainerPort { containerPort = 8887 })
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
                            name = "kernelf-instance-$id-volume"
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
                        name = "kernelf-instance-$id-volume"
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

fun ingressName(id: UUID) = "ingress-kernelf-instance-$id"
class KernelFInstanceIngress(id: UUID) : Ingress() {
    init {
        metadata { name = ingressName(id) }
        spec {
            rules = listOf(newIngressRule {
                host = "$id.$HOST_URL"
                http = newHTTPIngressRuleValue {
                    paths = listOf(newHTTPIngressPath {
                        path = "/"
                        pathType = "Prefix"
                        backend {
                            serviceName = "svc-kernef-instance-$id"
                            servicePort = IntOrString(80)
                        }
                    })
                }
            })
        }
    }
}