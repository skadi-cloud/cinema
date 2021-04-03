import { connectStreamSource, disconnectStreamSource } from '@hotwired/turbo';

import { Application } from "stimulus"
import { definitionsFromContext } from "stimulus/webpack-helpers"

const application = Application.start()
const context = require.context("./controllers", true, /\.js$/)
application.load(definitionsFromContext(context))

window.onload = function() {
    var es = new WebSocket("ws://localhost:8080/home/stream");
    connectStreamSource(es)
    for (let element of document.getElementsByClassName("date-relative")) {
        calcDateDiff(element)
    }
}

function calcDateDiff(element) {

}