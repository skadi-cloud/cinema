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

    div {
        id = "login"
        a(classes = "loginbox") {
            href = "/login/github"
            i(classes = "fab fa-github-square") {}
            +"Login with Github"
        }
        p(classes = "smaller") {
            +"By logging in you agree with this website using cookies and storing personal information see "
            a {
                href = "#legal"
                +"here"
            }
            +" for more details."
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
                | KernelF preinstalled but you can choose other versions if you like:""".trimMargin()
        }
        p {
            +"""Once you created a playground it will get deployed, you can see the status of the playground in the list. 
                |When the status changes to “running” you can connect via the link in the list. It will open a connection 
                |to the IDE in a new window. Alternatively you can also use the native client applications for projector. 
                |The native applications will give you better performance than the browser client and also allow you to 
                |use all keyboard shortcuts because some aren’t available in the browser. """.trimMargin()
        }
        p {
            +"""Once you connect for the first time you will have to go through the usual first start process of any 
                |JetBrains IDE. After you accepted the privacy policy you get to the new project dialog from where you 
                |can also access the samples. The “samples” include the regular MPS samples while the “community samples” 
                |are samples from various community tutorials including KernelF examples. """.trimMargin()
        }
    }
    div {
        id = "faq"
        h2 {
            id="faq-header"
            +"faq"
        }
        div(classes = "faq-item") {
            div {
                h3 { +"Why did you build this?" }
                p {
                    +"""As a demo to the community as an impulse to get new ideas and discussions started. 
                    |A while back I wrote down my ideas how Projector and MPS could be used together and this is 
                    |essentially the implementation of one of those ideas.""".trimMargin()
                }
            }

        }
        div(classes = "faq-item") {
            div {
                h3 { +"Why is this free?" }
                p {
                    +"""As mentioned this is a demo to the community and not commercial service. I’m paying for the cost of 
                    |running this out of my own pocket. That said the demo might get shutdown at some point in the 
                    |future.""".trimMargin()
                }
            }

        }
        div(classes = "faq-item") {
            div {
                h3 { +"Should I use this for real projects?" }
                p {
                    +"Definitely "
                    b { +"NOT" }
                    +"."
                    +"""This playground is more of a prove of concept to show what is possible with Projector, 
                    |Kubernetes and MPS. Your data might get deleted and any time and the playground might be shutdown. 
                    |Do not store anything important in it.""".trimMargin()
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"Should I use this for real projects?" }
                p {
                    +"Definitely "
                    b { +"NOT" }
                    +"."
                    +"""This playground is more of a prove of concept to show what is possible with Projector, 
                    |Kubernetes and MPS. Your data might get deleted and any time and the playground might be shutdown. 
                    |Do not store anything important in it.""".trimMargin()
                }
            }
        }

    }

    div {
        id = "legal"
        h3 {
            +"Cookies and Personal Data"
        }
        p {
            +"""Once you login via Github this service uses cookies to store your login information. The service does 
                |not use cookies for tracking but given the current law I have to tell you that it uses cookies. 🤷‍♂️""".trimMargin()
        }
        p {
            +"This service stores personal information after you logged in:"
        }
        ul {
            li {
                +"Name provided by Github"
            }
            li {
                +"Email address provided by Github"
            }
        }
        p {
            +"""These information is not shared with any third party. The email address is only used for contacting you
                | for information regarding the service or for investigation of technical issues with your instances. 
                | The email is not used to marketing purposes.""".trimMargin()
        }
    }


}
