package ws.logv.hosting.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*

object KernelFContainers : UUIDTable() {
    val name = varchar("name", 1024)
    val kernelFVersion = varchar("kernelf", 128)
    val created = datetime("created")
    val started = datetime("started")
    val lastHeartBeat = datetime("heartbeat")
    val user = reference("user", Users)
}

class KernelFContainer(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<KernelFContainer>(KernelFContainers)

    var name by KernelFContainers.name
    var kernelFVersion by KernelFContainers.kernelFVersion
    var created by KernelFContainers.created
    var started by KernelFContainers.started
    var lastHeartBeat by KernelFContainers.lastHeartBeat
    var user by User referencedOn KernelFContainers.user
}

fun canCreateContainer(email: String): Boolean {
    val usersContainers =
        transaction() { User.find { Users.email eq email }.firstOrNull()?.containers?.count() } ?: return false
    return usersContainers < 10
}

fun getContainerByName(name:String, user: User): KernelFContainer? {
    return transaction { KernelFContainer.find { (KernelFContainers.name eq name) and (KernelFContainers.user eq user.id) }.firstOrNull() }
}

fun getContainerById(id: String): KernelFContainer? {
    return transaction { KernelFContainer.findById(UUID.fromString(id)) }
}

fun containerWithNameExists(name: String) =
    !transaction() { KernelFContainer.find { KernelFContainers.name eq name }.empty() }

fun createContainer(name: String, userEmail: String, kernelFVersion: String): KernelFContainer {
    return transaction {
        val user = User.find {
            Users.email eq userEmail
        }.first()
        KernelFContainer.new {
            this.name = name
            this.user = user
            this.kernelFVersion = kernelFVersion
            created = LocalDateTime.now()
            lastHeartBeat = LocalDateTime.now()
            started = LocalDateTime.now()
        }
    }
}

fun deleteContainerByName(name: String) {
    transaction { KernelFContainers.deleteWhere { KernelFContainers.name eq name } }
}

fun deleteContainerById(id: String) {
    transaction { KernelFContainer.findById(UUID.fromString(id))?.delete() }
}

fun containers(email: String): List<KernelFContainer> {
    return transaction { User.find { Users.email eq email }.firstOrNull()?.containers?.toList() } ?: emptyList()
}


