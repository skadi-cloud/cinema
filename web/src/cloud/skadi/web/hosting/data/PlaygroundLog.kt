package cloud.skadi.web.hosting.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.LocalDateTime

// Only add new values at the end since the ordinal value is used to serialize it in the data base
enum class EventType {
    Created, Paused, Deleted, Started, Updated
}

object PlaygroundLogTable: IntIdTable() {
    val eventTime = datetime("event-time")
    val systemEvent = bool("system-event")
    val user = integer("user").nullable()
    val eventType = enumeration("event",EventType::class)
    val data = varchar("data", 1024).nullable()
    val instance = uuid("instance-id")
}

class PlaygroundLog(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<PlaygroundLog>(PlaygroundLogTable)

    var eventTime by PlaygroundLogTable.eventTime
    var isSystemEvent by PlaygroundLogTable.systemEvent
    var user by PlaygroundLogTable.user
    var type by PlaygroundLogTable.eventType
    var instance by PlaygroundLogTable.instance
    var data by PlaygroundLogTable.data
}

suspend fun logEvent(type:EventType, instance: KernelFContainer, user: User? = null, data: String? = null) {
    newSuspendedTransaction {
        PlaygroundLog.new {
            eventTime = LocalDateTime.now()
            isSystemEvent = user != null
            this.user = user?.id?.value
            this.type = type
            this.instance = instance.id.value
            this.data = data
        }
    }
}