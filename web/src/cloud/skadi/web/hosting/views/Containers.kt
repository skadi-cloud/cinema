package cloud.skadi.web.hosting.views

import cloud.skadi.web.hosting.HOME_PATH
import cloud.skadi.web.hosting.data.KernelFContainer
import cloud.skadi.web.hosting.data.User
import kotlinx.html.*

fun FlowContent.confirmDelete(user: User, containerToDelete: KernelFContainer) {
    h1 {
        +"Confirm Delete ${containerToDelete.name}"
    }
    instanceTable {
        user.containers.forEach { container ->
            if(container.id.value == containerToDelete.id.value) {
                confirmDeleteRow(container)
            } else {
                containerRow(container)
            }
        }
    }
}

private fun TBODY.confirmDeleteRow(container: KernelFContainer) {
    tr {
        id = instanceRowId(container)
        td {
            colSpan = "5"
            div(classes = "confirm-delete") {
                div {
                    p {
                        +"Confirm to delete "
                        b { +container.name }
                        br
                        +"All data will be lost!"
                    }
                    form {
                        action =  "/container/${container.id._value}/delete"
                        method = FormMethod.post
                        a(classes = "cancel") {
                            href = HOME_PATH
                            i(classes = "far fa-times-circle")
                        }
                        button(classes = "confirm") {
                            type = ButtonType.submit
                            id = "delete-${container.name}"
                            i(classes = "fas fa-check-circle")
                        }
                    }
                }

            }
        }
    }
}