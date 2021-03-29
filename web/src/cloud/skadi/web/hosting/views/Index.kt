package cloud.skadi.web.hosting.views

import kotlinx.html.*

fun FlowContent.indexPage() {
        div {
            id = "about"
            p {
                +"""This is a MPS playground in the browser. It’s build by Kolja as a demo of what is possible with 
                    |projector and MPS. This isn’t a real service like codespaces by Github or similar services it’s a 
                    |tech demo and should be treated like that. You can use it to play around with KernelF inside of MPS 
                    |without installing anything or exploring MPS with the other included samples. KernelF is a 
                    |functional base language for building domain specific languages on top of it. The playground is 
                    |build using JetBrains Projector, a framework for remote access to Java applications, Kubernetes and 
                    |JetBrains Meta Programming System. For more information on how this thing was build see take a 
                    |look this blogpost. """.trimMargin()
            }
            p {
                +"If you find bugs, have ideas to add to the playground or questions feel free to reach out to me at "
                a {
                    href = "mailto:kolja@hey.com"
                    +"kolja@hey.com"
                }
                +"."
            }
        }

    div(classes = "login") {
        i(classes = "fab fa-github-square") {}
        a {
            href = "/login/github"

            +"Log in with Github"
        }
    }

}