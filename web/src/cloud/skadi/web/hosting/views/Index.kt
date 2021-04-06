package cloud.skadi.web.hosting.views

import kotlinx.html.*

fun FlowContent.indexPage() {

    div {
        id = "header-featured"
        div {
            id="feature-bg-color"
        }
        div {
            id="header-featured-name"
            img {
                id = "header-logo"
                src = "/assets/icon.png"
            }
            h1 { +"skadi cloud" }
            p { +"An experiment with Projector to put JetBrains MPS into the browser." }
        }
        div {
            id="scroll-down"
            div {
                i(classes = "fas fa-chevron-circle-down")
            }
        }

        img {
            id = "featured-bg"
            src = "/assets/featured.png"
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
        id = "faq"
        h2 {
            id = "faq-header"
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
                h3 { +"What’s that name?" }
                p {
                    +"Skadi is the "
                    a {
                        href = "https://www.gods-and-goddesses.com/norse/skadi/"
                        +"Norse"
                    }
                    +" Norse giant goddess of winter, hunting, and skiing."
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"Can I use this on my iPad/Phone/Tablet?" }
                p {
                    +"""Yes you can. There is special links in you dashboard for mobile devices with no keyboard. 
                        |If you have a keyboard attached feel free to use the desktop links. Key mappings can get weird 
                        |though and might now work as expected. """.trimMargin()
                }
            }
        }

        div(classes = "faq-item") {
            div {
                h3 { +"Are there bugs?" }
                p {
                    +"""Most probably. This is a prove of concept and implemented like that. Projector, the underlying 
                        |technology used to give you access to the IDE, is very young technology and most probably also
                        | contains bugs. Expect this service to be a playground for experimenting with none sensitive 
                        | data that you can afford to loose.""".trimMargin()
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"Is my connection to the IDE safe?" }
                p {
                    +"""While the connection is encrypted there is no additional authentication in place. If the link 
                        |to your instance gets leaked including the token anyone with the link can connect to it. The
                        | playground list gives your two links one that grants full access and one that is read only.""".trimMargin()
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"Can I access Github?" }
                p {
                    +"""Yes you can, but the IDE plugins for Github won’t work. The authentication workflow will fail 
                        |because it assumes that you are on your machine and not in your browser. You could of course 
                        |use personal access tokens. Keep in mind if somebody gains access to the IDE instance they 
                        |also gain access to your Github token. Since this is a playground I wouldn’t recommend adding 
                        |authentication information into the container.""".trimMargin()
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"My instance is stuck in “deploying”!" }
                p {
                    +"""When the underlying infrastructure gets busy it might add more computing resources on demand. 
                        |Adding new resources to instratructure requires provisioning them which can take some time. 
                        |Usually containers should be up wihtin a minute but it can sometimes take 5+ minutes when new 
                        |resources are added to the infrastructure. """.trimMargin()
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"How big can my project get?" }
                p {
                    style = "display:inline"
                    +"You get 10GB of persistent storage in you user directory "
                }
                pre {
                    style = "display:inline"
                    +"/home/projector-user"
                }
                p {
                    style = "display:inline"
                    +""" everything else is not persisted and will get deleted if the instance is shutdown or restarted.
                        | 10GB should be plenty of space for playing around with MPS. Other resources like CPU and 
                        | memory are limited as well but should be enough for even larger experiments. You won’t be 
                        | able to use the playground to work with really large project like mbeddr.""".trimMargin()
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"What is that skadi-cloud plugin in my IDE?" }
                p {
                    +"""The plugin is responsible for detecting if you are connected to the IDE or not. In case there is
                        | no connection to an instance for more than 30 minutes the IDE is shutdown. You can restart it
                        | from the instance overview and reconnect. If you disable the plugin your instance will shutdown
                        | automatically after 30 minutes.""".trimMargin()
                }
            }
        }
        div(classes = "faq-item") {
            div {
                h3 { +"How long is my data stored? " }
                p {
                    +"Your playground is kept for 30 day after its been showdown. After 30 days of inactivity the playground is delete entirely. This might change at any time."
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
