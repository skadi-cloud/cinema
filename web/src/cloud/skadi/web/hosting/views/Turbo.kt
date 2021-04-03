package cloud.skadi.web.hosting.views

import kotlinx.html.*

class TurboFrame(consumer: TagConsumer<*>) :
    HTMLTag("turbo-frame", consumer, emptyMap(),
        inlineTag = false,
        emptyTag = false), HtmlBlockTag {
}
fun HTMLTag.turboFrame(block: TurboFrame.() -> Unit = {}) {
    TurboFrame(consumer).visit(block)
}