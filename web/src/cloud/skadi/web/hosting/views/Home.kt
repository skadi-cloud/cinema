package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.data.containers
import cloud.skadi.web.hosting.data.remainingContainers
import kotlinx.html.*
import java.util.*

fun FlowContent.appHome(email: String, name: String, toEdit: UUID? = null) {
    div {
        id = "top"
        p {
            +"""Hello $name, welcome to Skadi cloud. Below you find a list of your playgrounds where you can create new ones or 
            |manage existing ones. You have currently 
        """.trimMargin()
            b { +"${remainingContainers(email)}" }
            +" playground slots remaining."
        }
        p {
            +"If you find bugs, have ideas to add to the playground or questions feel free to reach out to me at "
            a {
                href = "mailto:kolja@hey.com"
                +"kolja@hey.com"
            }
            +"."
        }
        createPlaygroundForm(email)
        instanceTable {
            containers(email).forEach { container ->
                containerRow(container, container.id.value == toEdit)
            }
        }
    }

    div {
        id = "getting-started"
        h2 {
            +"Getting Started"
        }
        p {
            +"""To get started you need a Github account. To sign in click the button below. Once you are signed in you
                | can create a playground by default the playground uses the latest available version of MPS with 
                | KernelF preinstalled but you can choose other versions if you like.""".trimMargin()
        }
        p {
            +"""Once you created a playground it will get deployed, you can see the status of the playground in the list. 
                |When the status changes to “running” you can connect via the links in the list. Clicking a link will open 
                | a new browser window that then connects to you playground. 
                |Alternatively you can copy the link and use the """.trimMargin()
            a {
                href = "https://github.com/JetBrains/projector-client/releases"
                target = "_blank"
                +"native client applications"
            }
            +""" for projector.
            |The native applications will give you better performance than the browser client and also allow you to
            |use all keyboard shortcuts because some aren’t available in the browser. """.trimMargin()
        }
        p {
            +"""When you connect for the first time you will have to go through the usual first start process of any 
                |JetBrains IDE. Accepting the JetBrains privacy policy and deciding if you want to sent usage statistics. """.trimMargin()
        }
    }
    div {
        id = "samples"
        h2 {
            +"Included Samples"
        }
        p {
            +""" Playgrounds come with the usual MPS samples but also package a set of additional samples. 
                |These samples include:""".trimMargin()
        }
        ul {
            li {
                +"Basic KernelF Demo"
            }
            li {
                a {
                    href = "https://heavymeta.tv"
                    target = "_blank"
                    +"Heavy Meta TV MPS tutorial"
                }
            }
            li {
                a {
                    href = "https://github.com/markusvoelter/mpsintrocourse"
                    target = "_blank"
                    +"Markus slightly advanced introduction to MPS"
                }
            }
        }
        p {
            +"""You can access these samples from the same locations as the MPS samples: the welcome screen.""".trimMargin()
        }
        div {
            id = "samples-img-container"
            img {
                src = "/assets/samples1.png"
            }
            img {
                src = "/assets/samples2.png"
            }
        }
        p {
            +"The samples are hosted on Github and if think an important example is missing feel free to create new issue"
            a {
                href = "https://github.com/coolya/skadi-community-samples/issues"
                target = "_blank"
                +" here"
            }
            +"."
        }
    }
}


