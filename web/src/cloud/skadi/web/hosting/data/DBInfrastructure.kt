package cloud.skadi.web.hosting.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SchemaUtils.withDataBaseLock
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dbInfrastructure")


fun initDb(jdbc: String, user: String, password: String): Boolean {
    Database.connect(
        jdbc, driver = "org.postgresql.Driver",
        user = user, password = password
    )

    return transaction {
        try {
            withDataBaseLock {
                SchemaUtils.createMissingTablesAndColumns(Users, KernelFContainers, PlaygroundLogTable)
            }
        } catch (e: Throwable) {
            logger.error("error updating schema")
            return@transaction false
        }
        return@transaction true
    }
}