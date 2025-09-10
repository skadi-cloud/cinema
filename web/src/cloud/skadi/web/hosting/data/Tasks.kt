package cloud.skadi.web.hosting.data

import cloud.skadi.shared.data.Task
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.`java-time`.datetime
import org.jetbrains.exposed.sql.and
import java.time.LocalDateTime
import java.util.*

// Order matters, only add new entry at the end because of SQL serialization
enum class TaskState {
    New, InProgress, Succeeded, Failed, Timeout
}

object TaskTable : UUIDTable() {
    val instance = reference("instance", KernelFContainers, onDelete = ReferenceOption.CASCADE)
    val data = varchar("data",4096)
    val state = enumeration("state", TaskState::class)
    val created = datetime("created")
    val lastChange = datetime("last-change")
}

class TaskEntry(id: EntityID<UUID>): UUIDEntity(id) {
    companion object : UUIDEntityClass<TaskEntry>(TaskTable)
    var instance by KernelFContainer referencedOn TaskTable.instance
    var data by TaskTable.data
    var state by TaskTable.state
    var created by TaskTable.created
    var lastChange by TaskTable.lastChange
}
private val mapper = JsonMapper.builder()
    .addModule(KotlinModule.Builder().build())
    .build()

fun createTask(instance: KernelFContainer, task: Task): TaskEntry {
    return TaskEntry.new {
        this.instance = instance
        state = TaskState.New
        created = LocalDateTime.now()
        lastChange = LocalDateTime.now()
        data = mapper.writeValueAsString(task)
    }
}

fun peekTask(instance: KernelFContainer): TaskEntry? {
    val entry = TaskEntry.find { (TaskTable.state eq TaskState.New) and (TaskTable.instance eq instance.id)}.forUpdate().sortedBy { it.created }.firstOrNull()
    if (entry != null) {
        entry.state = TaskState.InProgress
        entry.lastChange = LocalDateTime.now()
    }
    return entry
}

fun getTask(id: UUID): TaskEntry? {
    return TaskEntry.findById(id)
}