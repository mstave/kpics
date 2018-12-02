package kgui.view

import kgui.app.PicCollectionsController
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*

class MainView : View("Pics") {
    private val logger: KLogger = KotlinLogging.logger {}
    private val controller: PicCollectionsController by inject()
    override val root = tabpane()
    private val dupes: Dupes by inject()

    init {
        logger.debug("MainView init")
        with(root) {
            tab("Setup") {
                this += find<DBForm>(DBForm::pCont to controller)
            }
            tab("All") {
                this += find<All>(DBForm::pCont to controller)
            }
            tab("differences") {
                this += find<Diffs>(Diffs::pCont to controller)
            }
            tab("EXIF details") {
                //                this += exif.root
            }
            tab("Dupes") {
                this += dupes.root
                this.select()
            }
        }
        logger.info("with root complete")
    }
}

class AddDirDialogView : UIComponent() {
    override val root = form {
    }
}

