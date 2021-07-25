package cloud.skadi.gist.data

import cloud.skadi.gist.shared.GistVisibility
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.or

fun allPublicGists(page: Int = 0) =
    Gist.find { GistTable.visibility eq GistVisibility.Public }
        .orderBy(GistTable.created to SortOrder.DESC)
        .limit(50, (50 * page).toLong())

fun allGistsIncludingUser(user: User, page: Int = 0) =
    Gist.find { (GistTable.visibility eq GistVisibility.Public) or (GistTable.user eq user.id) }
        .orderBy(GistTable.created to SortOrder.DESC)
        .limit(50, (50 * page).toLong())