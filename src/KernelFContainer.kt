package ws.logv.hosting

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object KernelFContainers : IntIdTable() {
    val name = varchar("name", 1024).uniqueIndex()
    val kernelFVersion = varchar("kernelf", 128)
    val status = varchar("status", 50)
    val created = datetime("created")
    val started = datetime("started")
    val lastHeartBeat = datetime("heartbeat")
    val user = reference("user", Users)
}

class KernelFContainer(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<KernelFContainer>(KernelFContainers)

    var name by KernelFContainers.name
    var kernelFVersion by KernelFContainers.kernelFVersion
    var status by KernelFContainers.status
    var created by KernelFContainers.created
    var started by KernelFContainers.started
    var lastHeartBeat by KernelFContainers.lastHeartBeat
    var user by User referencedOn KernelFContainers.user
}

fun canCreateContainer(email: String): Boolean {
    val usersContainers =
        transaction() { User.find { Users.email eq email }.firstOrNull()?.containers?.count() } ?: return false
    return usersContainers < 1
}

fun getContainerByName(name:String): KernelFContainer? {
    return transaction { KernelFContainer.find { KernelFContainers.name eq name }.firstOrNull() }
}
fun containerWithNameExists(name: String) =
    !transaction() { KernelFContainer.find { KernelFContainers.name eq name }.empty() }

fun createContainer(name: String, userEmail: String, kernelFVersion: String) {
    transaction {
        val user = User.find {
            Users.email eq userEmail
        }.first()
        KernelFContainer.new {
            this.name = name
            this.user = user
            this.kernelFVersion = kernelFVersion
            created = LocalDateTime.now()
            status = "Created"
            lastHeartBeat = LocalDateTime.now()
            started = LocalDateTime.now()
        }
    }
}

fun deleteContainer(name: String) {
    transaction { KernelFContainers.deleteWhere { KernelFContainers.name eq name } }
}

fun containers(email: String): List<KernelFContainer> {
    return transaction { User.find { Users.email eq email }.firstOrNull()?.containers?.toList() } ?: emptyList()
}


