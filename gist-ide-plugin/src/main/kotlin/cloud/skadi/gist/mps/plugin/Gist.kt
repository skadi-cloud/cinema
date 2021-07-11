package cloud.skadi.gist.mps.plugin

import jetbrains.mps.smodel.SNode

enum class Visibility {
    Public, Internal, Private
}

fun upload(name: String, description: String?, visibility: Visibility, nodes: List<Pair<SNode, String?>>) {

}