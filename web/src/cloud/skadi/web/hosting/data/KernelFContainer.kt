package cloud.skadi.web.hosting.data

import cloud.skadi.web.hosting.getName
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.*


enum class ContainerStatus {
    Created, Deploying, Running, Error, Stopped, Stopping
}

object KernelFContainers : UUIDTable() {
    val name = varchar("name", 1024)
    val version = enumeration("version", ContainerVersion::class)
    val created = datetime("created")
    val started = datetime("started")
    val lastHeartBeat = datetime("heartbeat")
    val user = reference("user", Users)
    val status = enumeration("status", ContainerStatus::class)
    val rwToken = varchar("rw-token",128)
    val roToken = varchar("ro-token",128)
}

class KernelFContainer(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<KernelFContainer>(KernelFContainers)

    var name by KernelFContainers.name
    var version by KernelFContainers.version
    var created by KernelFContainers.created
    var started by KernelFContainers.started
    var lastHeartBeat by KernelFContainers.lastHeartBeat
    var user by User referencedOn KernelFContainers.user
    var status by KernelFContainers.status
    var roToken by KernelFContainers.roToken
    var rwToken by KernelFContainers.rwToken
}

fun canCreateContainer(email: String): Boolean {
    val usersContainers =
        transaction() { User.find { Users.email eq email }.firstOrNull()?.containers?.count() } ?: return false
    return usersContainers < 10
}

fun getContainerByName(name: String, user: User): KernelFContainer? {
    return transaction {
        KernelFContainer.find { (KernelFContainers.name eq name) and (KernelFContainers.user eq user.id) }.firstOrNull()
    }
}

fun getContainerById(id: String): KernelFContainer? {
    return transaction { KernelFContainer.findById(UUID.fromString(id)) }
}

fun containerWithNameExists(name: String) =
    !transaction() { KernelFContainer.find { KernelFContainers.name eq name }.empty() }

fun createContainer(
    userEmail: String,
    kernelFVersion: ContainerVersion,
    rwToken: String,
    roToken: String
): KernelFContainer {
    return transaction {
        val user = User.find {
            Users.email eq userEmail
        }.first()

        var name = getName()

        while (!KernelFContainer.find { (KernelFContainers.user eq user.id) and (KernelFContainers.name eq name) }.empty()) {
            name = getName()
        }

        KernelFContainer.new {
            this.name = name
            this.user = user
            this.version = kernelFVersion
            created = LocalDateTime.now()
            lastHeartBeat = LocalDateTime.now()
            started = LocalDateTime.now()
            status = ContainerStatus.Created
            this.roToken = roToken
            this.rwToken = rwToken
        }
    }
}

fun deleteContainerByName(name: String) {
    transaction { KernelFContainers.deleteWhere { KernelFContainers.name eq name } }
}

fun deleteContainerById(id: UUID) {
    transaction { KernelFContainer.findById(id)?.delete() }
}

fun containers(email: String): List<KernelFContainer> {
    return transaction { User.find { Users.email eq email }.firstOrNull()?.containers?.sortedBy { it.name }?.toList() } ?: emptyList()
}
