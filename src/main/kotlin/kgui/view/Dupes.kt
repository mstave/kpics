package kgui.view

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
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
    private val status: TaskStatus by inject()
    // TODO pass status to dupeC then have dupeC handle the picCollectionsCont.allPicLibs.onChange
    override val root = scrollpane(true, true) {
        vbox {
            titleProperty.bind(status.title)

            hbox {
                progressindicator {
                    minWidth = 100.0
                    bind(status.progress)
                }
                vbox {
                    label(Bindings.concat("Looking for duplicates among ", dupeC.pfCount, " files."))
                    label(status.title)
                    label(status.message)
                }
                managedWhen(status.running)
            }

            listview<String> {
                items = dupeC.dupeStrings
                prefWidth = 300.0
                minHeight = 600.0
                vgrow = Priority.ALWAYS
                dupeC.picCollectionsCont.allPicLibs.onChange {
                    updateDiffs()
                }
                updateDiffs()
            }
        }
    }

    private fun updateDiffs() = runAsync { dupeC.getDupesFromAllLocalCollections(this) }
}

class DupeController : Controller() {
    private val logger: KLogger = KotlinLogging.logger {}
    val picCollectionsCont: PicCollectionsController by inject()
    var doneSearching = SimpleBooleanProperty(false)
    var dupeStrings = ArrayList<String>().observable()
    var pfCount = SimpleIntegerProperty()
    fun getDupesFromAllLocalCollections(fxTask: FXTask<*>) {
        val concatedPf = LocalPicFiles("/dev/null")
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
        val justFiles = picCollectionsCont.allPicLibs.filter { it is LocalPicFiles }.map { it as LocalPicFiles }
        justFiles.forEach {
            concatedPf.filePaths.addAll(it.filePaths)
        }
        Platform.runLater {
            pfCount.set(concatedPf.filePaths.count())
        }
        concatedPf.updateFunc = ::updateStatus
        // prime cache
        val duration = measureTimeMillis { concatedPf.dupeFiles }
        logger.info("done looking for dupes, took ${duration / 1000} seconds")
        Platform.runLater {
            // will get from cache
            concatedPf.getDupeFileList()?.let { dupeStrings.addAll(it) }
            doneSearching.set(true)
        }
    }
}