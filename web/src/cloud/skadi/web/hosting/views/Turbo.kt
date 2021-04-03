package cloud.skadi.web.hosting.views

import kotlinx.html.*
import kotlinx.html.attributes.Attribute
import kotlinx.html.attributes.StringAttribute

class TurboFrame(consumer: TagConsumer<*>) :
    HTMLTag(
        "turbo-frame", consumer, emptyMap(),
        inlineTag = false,
        emptyTag = false
    ), HtmlBlockTag

fun HTMLTag.turboFrame(block: TurboFrame.() -> Unit = {}) {
    TurboFrame(consumer).visit(block)
}

val attributeStringString : Attribute<String> = StringAttribute()

class TurboStream(consumer: TagConsumer<*>) :
    HTMLTag(
        "turbo-stream", consumer, emptyMap(),
        inlineTag = false,
        emptyTag = false
    ), HtmlBlockTag {
    var action: String
        get() = attributeStringString[this, "action"]
        set(newValue) {
            attributeStringString[this, "action"] = newValue
        }
    var target: String
        get() = attributeStringString[this, "target"]
        set(newValue) {
            attributeStringString[this, "target"] = newValue
        }
}

fun <T> TagConsumer<T>.turboStream(block: TurboStream.() -> Unit = {}): T {
    return TurboStream(this).visitAndFinalize(this, block)
}

class TurboTemplate(consumer: TagConsumer<*>) :
    HTMLTag(
        "template", consumer, emptyMap(),
        inlineTag = false,
        emptyTag = false
    ), HtmlBlockTag

fun HTMLTag.template(block: TurboTemplate.() -> Unit = {}) {
    TurboTemplate(consumer).visit(block)
}
