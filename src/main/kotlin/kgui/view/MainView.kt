package kgui.view

import javafx.geometry.Orientation
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kgui.app.PicCollectionsController
import kpics.AbstractPicCollection
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
                splitpane(Orientation.HORIZONTAL) {
                    prefWidth = 1200.0
                    minHeight = 500.0
                    for (v in controller.allPicLibs) {
                        this += find<PicsFragment>(mapOf(PicsFragment::picObj to v))
                    }
                    // even out dividers, weird that this seems to be needed
                    for (i in 0 until dividers.size) {
                        dividers[i].position = (1.0 + i) / (dividers.size + 1)
                    }
                }
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

class PicsFragment : Fragment() {
    val picObj: AbstractPicCollection by param()
    private val lview = listview<String> {
        prefWidth = 300.0
        minHeight = 600.0
        items = picObj.relativePaths.toList().observable()
        selectionModel.select(0)
        vgrow = Priority.ALWAYS
    }
    override val root = VBox(picObj.baseStr?.let { label(it) }, lview,
                             label("Count: " + picObj.count + "\tName: " + picObj.toString()))
}

