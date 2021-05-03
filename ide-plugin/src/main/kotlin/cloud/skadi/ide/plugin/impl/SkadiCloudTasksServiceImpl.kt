package cloud.skadi.ide.plugin.impl

import cloud.skadi.ide.plugin.SkadiCloudTasksService
import cloud.skadi.ide.plugin.SkadiHeartbeatService
import cloud.skadi.ide.plugin.ofFormData
import cloud.skadi.shared.data.Task
import cloud.skadi.shared.data.getContainer
import cloud.skadi.shared.data.getTaskFromJson
import cloud.skadi.shared.hmac.signNonce
import com.intellij.dvcs.ui.CloneDvcsValidationUtils
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.PathUtil
import com.intellij.util.io.exists
import git4idea.GitUtil
import git4idea.checkout.GitCheckoutProvider
import git4idea.commands.Git
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.apache.commons.httpclient.HttpStatus
import org.apache.http.HttpHeaders
import org.apache.http.entity.ContentType
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.*

class SkadiCloudTasksServiceImpl : SkadiCloudTasksService {
    private val logger = Logger.getInstance(this::class.java)
    private val timer = Timer(true)

    init {
        logger.info("setting up heartbeat")

        val id = System.getenv("SKADI_INSTANCE_ID")
        if (id == null) {
            logger.error("can't get instance id")
        }

        val address = System.getenv("SKADI_BACKEND_ADDRESS")
        if (address == null) {
            logger.error("can't get backend address")
        }

        val token = System.getenv("ORG_JETBRAINS_PROJECTOR_SERVER_HANDSHAKE_TOKEN")
        if (token == null) {
            logger.error("can't get token")
        }
        timer.scheduleAtFixedRate(CheckTask(address, id, token), 0, 10_000)
    }

    private class CheckTask(
        val backendAddress: String,
        val skadiInstance: String,
        val token: String
    ) : TimerTask() {
        private val logger = Logger.getInstance(this::class.java)
        private val heartbeatService = SkadiHeartbeatService.getInstance()
        fun checkForTasks() {
            heartbeatService?.acquireActivityLock()
            try {
                val signature = signNonce(token)

                val body = mapOf(Pair("nonce", signature.second), Pair("signature", signature.first))

                val client = HttpClient.newHttpClient()
                val request = HttpRequest.newBuilder()
                    .POST(ofFormData(body))
                    .timeout(Duration.ofSeconds(30))
                    .uri(URI.create("http://$backendAddress/tasks/$skadiInstance/dequeue"))
                    .header(
                        HttpHeaders.CONTENT_TYPE,
                        ContentType.APPLICATION_FORM_URLENCODED.mimeType
                    )
                    .build()

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept {

                    if (it.statusCode() == HttpStatus.SC_NOT_FOUND) {
                        logger.info("no tasks in queue")
                        return@thenAccept
                    }

                    if (it.statusCode() != HttpStatus.SC_OK) {
                        logger.error("got status code ${it.statusCode()} when requesting tasks.")
                        return@thenAccept
                    }

                    val container = getContainer(it.body())
                    val validSignature = cloud.skadi.shared.hmac.check(container.signature, container.payload, token)
                    if (!validSignature) {
                        logger.error("Invalid signature for task!")
                        return@thenAccept
                    }

                    when (val task = getTaskFromJson(container.payload)) {
                        is Task.CloneRepo -> checkoutAndOpen(task)
                        is Task.UploadData -> TODO()
                    }

                    logger.info("heartbeat send")
                }.join()
            } catch (e: Exception) {
                logger.error(e)
            } finally {
                heartbeatService?.releaseActivityLock()
            }
        }

        override fun run() {
            checkForTasks()
        }

        fun checkoutAndOpen(task: Task.CloneRepo) {
            GlobalScope.launch {
                heartbeatService?.acquireActivityLock()
                try {

                    val prj = ProjectManager.getInstance().defaultProject
                    val git = Git.getInstance()
                    val prjRoot = ProjectUtil.getBaseDir()
                    val name = PathUtil.getFileName(task.url)
                    val directoryName = name.trimEnd { it in GitUtil.DOT_GIT }

                    val path = Paths.get(prjRoot, directoryName)
                    if (!path.exists()) {
                        CloneDvcsValidationUtils.createDestination(prjRoot)
                        var result = false
                        object : com.intellij.openapi.progress.Task.Modal(prj, "Cloning", false) {
                            override fun run(indicator: ProgressIndicator) {
                                result = GitCheckoutProvider.doClone(prj, git, directoryName, prjRoot, task.url)
                            }
                        }.queue()

                        if (!result) {
                            logger.error("failed to clone repository!")
                            reportError(task)
                        } else {
                            reportSuccess(task)
                        }

                    } else {
                        reportSuccess(task)
                    }

                    val directory = Path.of(prjRoot, directoryName)
                    val dirs = directory.toFile().walkBottomUp().filter { it.isDirectory && it.name == ".mps" }.toList()
                    if (dirs.isNotEmpty()) {
                        ProjectUtil.openProject(dirs.first().parent, prj, false)
                    } else {
                        logger.error("no project found in cloned repository.")
                    }
                } catch (e:Exception) {
                    reportError(task)
                } finally {
                    heartbeatService?.releaseActivityLock()
                }
            }
        }

        suspend fun reportError(task: Task) {
            makePostWithNonce("http://$backendAddress/tasks/${task.id}/error")
        }

        suspend fun reportSuccess(task: Task) {
            makePostWithNonce("http://$backendAddress/tasks/${task.id}/success")
        }

        suspend fun makePostWithNonce(url: String): HttpResponse<String>? {
            val signature = signNonce(token)
            val body = mapOf(Pair("nonce", signature.second), Pair("signature", signature.first))

            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .POST(ofFormData(body))
                .timeout(Duration.ofSeconds(30))
                .uri(URI.create(url))
                .header(
                    HttpHeaders.CONTENT_TYPE,
                    ContentType.APPLICATION_FORM_URLENCODED.mimeType
                )
                .build()
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        }
    }


}