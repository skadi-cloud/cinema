package test.cloud.skadi.web.hosting

import cloud.skadi.web.hosting.data.ContainerVersion
import cloud.skadi.web.hosting.getEnvOfFail
import cloud.skadi.web.hosting.getEnvOrDefault
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.server.testing.*
import io.ktor.util.*
import kotlinx.coroutines.ObsoleteCoroutinesApi
import org.jsoup.Jsoup
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

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

data class ContainerData(val id: String, val rwToken: String, val roToken: String)

@KtorExperimentalLocationsAPI
@ObsoleteCoroutinesApi
@KtorExperimentalAPI
@ExperimentalTime
fun CookieTrackerTestApplicationEngine.withContainer(setup: (ContainerData) -> Unit = {}) {
    handleRequest(HttpMethod.Post, "/testlogin").apply {
        assertEquals(HttpStatusCode.OK, response.status())
    }
    handleRequest(HttpMethod.Get, "/home").apply {
        assertEquals(HttpStatusCode.OK, response.status())
    }
    handleRequest(HttpMethod.Post, "/new-container") {
        addHeader(HttpHeaders.ContentType, ContentType.Application.FormUrlEncoded.toString())
        setBody(listOf("version" to ContainerVersion.V2020_3_4731_f5286c0.name).formUrlEncode())
    }.apply {
        assertEquals(HttpStatusCode.SeeOther, response.status())
        assertEquals("/home", response.headers[HttpHeaders.Location])
    }
    handleRequest(HttpMethod.Get, "/home").apply {
        assertEquals(HttpStatusCode.OK, response.status())
        val body = Jsoup.parse(response.content)
        val rows = body.select("#instances > table > tbody > tr")
        val row = rows.first()
        val links = row.select("td:nth-child(4)")
        val rwToken = Url(links.select("a").first().attr("href")).parameters["token"]!!
        val roToken = Url(links.select("a")[1].attr("href")).parameters["token"]!!

        val rowId = row.children().first().id()
        val containerId = rowId.substring(7)
        setup(ContainerData(containerId, rwToken, roToken))
    }
}