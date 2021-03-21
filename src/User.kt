package ws.logv.hosting

import org.jetbrains.exposed.dao.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.transactions.nullableTransactionScope
import org.jetbrains.exposed.sql.`java-time`.*
import java.time.*


object Users : IntIdTable() {
    val email = varchar("email", 128).uniqueIndex()
    val regDate = datetime("reg-date")
    val lastLogin = datetime("last-login-date")
}

class User(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<User>(Users)
    var email by Users.email
    var regDate by Users.regDate
    var lastLogin by Users.lastLogin
    val containers by User referrersOn KernelFContainers.user
}

fun userExists(email: String): Boolean {
    val user = transaction() { 
        User.find {
            Users.email eq email
        } 
     }
     return !user.empty()
}

fun createUser(emailAddress: String){
    val now = LocalDateTime.now()
    transaction() { 
        User.new { 
            email = emailAddress
            regDate = now
            lastLogin = now
         }
     }
}

fun login(email: String) {
    val user = transaction() { User.find{
        Users.email eq email
    } }

    transaction() { user.first().lastLogin = LocalDateTime.now() }
}
