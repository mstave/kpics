package kgui.view

import mu.KLogger
import mu.KotlinLogging
import tornadofx.*

class MainView : View("Pics") {
    private val logger: KLogger = KotlinLogging.logger {}
    override val root = tabpane()
    private val dupes: Dupes by inject()

    init {
        logger.debug("MainView init")
//        root.setPrefSize()
        with(root) {
            tab("Setup") {
                this += find<SetupForm>()
            }
            tab("All"){
                this += find<All>()
            }
            tab("differences") {
                this += find<Diffs>()
            }
            tab("EXIF details") {
                //                this += exif.root
            }
            tab("Dupes") {
                this += dupes.root  // Arbitrarily inconsisent use of injected view rather than find<>
            }
        }
        logger.info("with root complete")
    }
}



