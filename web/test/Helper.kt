package ws.logv.hosting

import cloud.skadi.web.hosting.getEnvOfFail
import cloud.skadi.web.hosting.getEnvOrDefault
import java.sql.DriverManager

fun ensureDbEmpty() {
    val SQL_PASSWORD = getEnvOfFail("SQL_PASSWORD")
    val SQL_USER = getEnvOfFail("SQL_USER")
    val SQL_DB = getEnvOrDefault("SQL_DB", "kernelf")
    val SQL_HOST = getEnvOrDefault("SQL_HOST", "localhost:5432")
    val url = "jdbc:postgresql://$SQL_HOST/$SQL_DB"
    val connection = DriverManager.getConnection(url, SQL_USER, SQL_PASSWORD)
    val statement = connection.createStatement()
    val result = statement.executeQuery(
        "select 'drop table if exists \"' || tablename || '\" cascade;'\n" +
                "from pg_tables\n" +
                "where schemaname = 'public';"
    )
    while (result.next()) {
        val stmt = connection.createStatement()
        stmt.execute(result.getString(1))
    }
}