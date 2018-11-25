package kgui.app

import kgui.view.MainView
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*
import java.net.InetAddress
import java.nio.file.Paths

class KGuiPics : App(MainView::class, Styles::class) {
    private val logger: KLogger = KotlinLogging.logger {}
    private val hostName: String = InetAddress.getLocalHost().hostName
    override val configBasePath = Paths.get("conf/$hostName")!!
    init {
        logger.info("Using config from $configBasePath")
    }

}