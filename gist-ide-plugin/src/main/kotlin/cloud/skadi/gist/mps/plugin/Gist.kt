package cloud.skadi.gist.mps.plugin

import cloud.skadi.gist.shared.*
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.client.engine.java.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SNodeOperations
import jetbrains.mps.lang.smodel.generator.smodelAdapter.SPropertyOperations
import jetbrains.mps.smodel.SNodePointer
import jetbrains.mps.smodel.StaticReference
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.adapter.structure.language.SLanguageAdapterById
import jetbrains.mps.smodel.adapter.structure.link.SContainmentLinkAdapterById
import jetbrains.mps.smodel.adapter.structure.property.SPropertyAdapterById
import jetbrains.mps.smodel.adapter.structure.ref.SReferenceLinkAdapterById
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SRepository

val client = io.ktor.client.HttpClient(Java) {
    followRedirects = false
}
const val HOST = "http://localhost:8080/gist/create"

private val mapper = JsonMapper.builder()
    .addModule(KotlinModule(strictNullChecks = true))
    .build()

suspend fun upload(
    name: String,
    description: String?,
    visibility: GistVisibility,
    nodes: List<Pair<SNode, String?>>,
    repository: SRepository
): String? {
    val response = client.post<HttpResponse>(HOST) {
        expectSuccess = false
        body = MultiPartContent.build {
            add("name", name)
            if (description != null) {
                add("description", description)
            }
            add("visibility", visibility.toString())

            nodes.forEachIndexed { index, (node, name) ->
                val n = name ?: "node-$index"
                val serialized = serializeRootNode(node)
                val image = node.asImage(repository)
                add("image-$index", image.toByteArray(), contentType = ContentType.Image.PNG, filename = n)
                add("node-$index", mapper.writeValueAsString(serialized), contentType = ContentType.Application.Json)
                add("name-$index", n)
            }
        }
    }

    return if(response.status.value == 302) {
        response.headers[HttpHeaders.Location]
    } else {
        null
    }
}

fun serializeRootNode(node: SNode): AST {
    var serializedNode: Node? = null
    var descendants: MutableList<SNode>?
    var usedLanguages: List<SLanguage> = emptyList()
    var imports: List<SModelReference> = emptyList()

    node.model!!.repository.modelAccess.runReadAction {
        serializedNode = node.serialize()
        descendants = SNodeOperations.getNodeDescendants(node, null, true)
        usedLanguages = descendants!!.map { it.concept.language }.distinct()
        imports = descendants!!.flatMap { it.references.mapNotNull { reference -> reference.targetSModelReference } }.distinct()
    }

    return AST(
        imports.map { Import(it.modelName, it.modelId.toString()) },
        usedLanguages.map { UsedLanguage(it.qualifiedName, (it as SLanguageAdapterById).serialize()) },
        serializedNode!!
    )
}

fun SNode.serialize(): Node {
    return Node(
        id = this.nodeId.toString(),
        concept = (this.concept as SConceptAdapterById).serialize(),
        properties = this.properties.map {
            Property(
                id = (it as SPropertyAdapterById).serialize(),
                value = SPropertyOperations.getString(this, it)
            )
        },
        children = this.children.map {
            Child(
                (it.containmentLink as SContainmentLinkAdapterById).serialize(),
                it.serialize()
            )
        },
        references = this.references.filterIsInstance(StaticReference::class.java)
            .map {
                Reference(
                    (it.link as SReferenceLinkAdapterById).serialize(),
                    SNodePointer.serialize(it.targetNodeReference)
                )
            }
    )
}