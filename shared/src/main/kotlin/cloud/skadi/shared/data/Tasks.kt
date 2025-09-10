package cloud.skadi.shared.data


import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.util.*

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed class Task(var id: UUID) {
    class CloneRepo(val url: String, id: UUID) : Task(id)
    class UploadData(val maxChunkSize: Int, id: UUID) : Task(id)
}

class TaskContainer(val payload: String, val signature: String)

fun getTaskFromJson(json: String): Task {
    val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()
    return mapper.readValue(json)
}

fun getContainer(json: String): TaskContainer {
    val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()
    return mapper.readValue(json)
}