package kgui.view

import javafx.geometry.Orientation
import javafx.scene.control.SplitPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import kgui.app.PicCollectionsController
import kpics.AbstractPicCollection
import tornadofx.*

class All : View("All") {
    override val root = splitpane(Orientation.HORIZONTAL)
    private val pCont: PicCollectionsController by inject()

    init {
        with(root) {
            prefWidth = 1200.0
            minHeight = 500.0
            pCont.allPicLibs.onChange {
                this.replaceChildren {
                    it.list.forEach { picCol ->
                        this += find<PicsFragment>(mapOf(PicsFragment::picObj to picCol))
                    }
                }
                balance()
            }
            for (v in pCont.allPicLibs) {
                this += find<PicsFragment>(mapOf(PicsFragment::picObj to v))
            }
            // even out dividers, weird that this seems to be needed
            balance()
        }
    }

    private fun SplitPane.balance() {
        for (i in 0 until dividers.size) {
            dividers[i].position = (1.0 + i) / (dividers.size + 1)
        }
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