package kgui.view

import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.layout.Priority
import kgui.app.PicCollectionsController
import kpics.LocalPicFiles
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*
import java.util.*
import kotlin.system.measureTimeMillis

class Dupes : View() {
    val dupeC: DupeController by inject()
    val status: TaskStatus by inject()
    override val root = scrollpane(true, true) {
        vbox {
            titleProperty.bind(status.title)
            hbox {
                progressindicator {
                    minWidth=100.0
                    bind(status.progress)
                }
                vbox {
                    label("Looking for duplicates among ${dupeC.pf?.count} files.")
                    label(status.title)
                    label(status.message)
                }
                managedWhen(status.running)
            }

            listview<String> {
                prefWidth = 300.0
                minHeight = 600.0
                vgrow = Priority.ALWAYS
                runAsync {
                    dupeC.updateDupes(this)
                } ui {
                    items = dupeC.dupeStrings
                }
            }
        }
    }
}

class DupeController : Controller() {
    private val logger: KLogger = KotlinLogging.logger {}
    private val picCollectionsCont: PicCollectionsController by inject()
    private val dupesProperty =
            SimpleObjectProperty<HashSet<HashSet<String>>>()
    private var dupes by dupesProperty
    var doneSearching = SimpleBooleanProperty(false)
    var dupeStrings = ArrayList<String>().observable()
    private var justFiles = picCollectionsCont.allPicLibs.filter { it is LocalPicFiles }.map { it as LocalPicFiles }
    var pf: LocalPicFiles? = justFiles.first()
    fun updateDupes(fxTask: FXTask<*>) {
        fun updateStatus(completed: Long, total: Long, msg: String, title: String) {
            Platform.runLater {
                if (msg != "")
                    fxTask.updateMessage(msg)
                fxTask.updateProgress(completed, total)
                if (title != "")
                    fxTask.updateTitle(title)
            }
        }
        logger.info("Looking for dupes")
        pf?.updateFunc = ::updateStatus
        val duration = measureTimeMillis { dupes = pf?.getDupes() }
        logger.info("done looking for dupes, took ${duration / 1000} seconds")
        pf?.getDupeFileList()?.let { dupeStrings.addAll(it) }
        doneSearching.set(true)
    }
}