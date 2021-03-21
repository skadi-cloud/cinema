package ws.logv.hosting

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.nullableTransactionScope
import org.jetbrains.exposed.sql.`java-time`.*
import java.time.*

object KernelFContainers: IntIdTable(){
    val name = varchar("name", 1024).uniqueIndex()
    val kernelFVersion = varchar("kernelf", 128)
    val status = varchar("status", 50)
    val created = datetime("created")
    val started = datetime("started")
    val lastHeartBeat = datetime("heartbeat")
    val user = reference("user", Users)
}

class KernelFContainer(id: EntityID<Int>): IntEntity(id) {
    companion object: IntEntityClass<KernelFContainer>(KernelFContainers)

    var name by KernelFContainers.name
    var kernelFVersion by KernelFContainers.kernelFVersion
    var status by KernelFContainers.status
    var created by KernelFContainers.created
    var started by KernelFContainers.started
    var lastHeartBeat by KernelFContainers.lastHeartBeat
    var user by User referencedOn KernelFContainers.user
}

fun canCreateContainer(email: String): Boolean{
    val usersContainers = transaction() { User.find {Users.email eq email}.firstOrNull()?.containers?.count()} ?: return false
    return usersContainers <= 1
}

fun containerWithNameExists(name: String) = !transaction() { KernelFContainer.find {KernelFContainers.name eq name} }.empty()

fun createContainer(name: String) {
    
}


