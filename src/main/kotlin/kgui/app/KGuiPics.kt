package kgui.app

import kgui.view.MainView
import tornadofx.*
import java.net.InetAddress
import java.nio.file.Paths

class KGuiPics : App(MainView::class, Styles::class) {
    override val configBasePath = Paths.get("conf/${InetAddress.getLocalHost().hostName}")!!
}