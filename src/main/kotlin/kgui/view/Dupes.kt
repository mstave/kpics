package kgui.view

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.scene.control.ListView
import javafx.scene.layout.Priority
import kgui.app.PicCollectionsController
import kpics.LocalPicFiles
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

class Dupes : View() {
    val dupeC: DupeController by inject()
    private val logger: KLogger = KotlinLogging.logger {}
    private val status: TaskStatus by inject()
    // TODO pass status to dupeC then have dupeC handle the picCollectionsCont.allPicLibs.onChange
    override val root = scrollpane(fitToWidth = true, fitToHeight = true) {
        vbox {
            hbox {
                titleProperty.bind(status.title)
                progressindicator {
                    minWidth = 100.0
                    bind(status.progress)
                }
                vbox {
                    label(Bindings.concat("Looking for duplicates among ", dupeC.pfCount, " files."))
                    label(status.title)
                    label(status.message)
                }
                visibleWhen(status.running)
                managedWhen(status.running)
            }
            vbox {
                visibleWhen(!status.running)
                managedWhen(!status.running)
                vbox {
                    text(Bindings.concat(" Directories with all dupes (",
                                         dupeC.dupeDirCount,")"))
                    listview(dupeC.dupeDirs) {
                        vgrow = Priority.SOMETIMES
                        items.onChange {
                            updateHeight()
                        }
                        updateHeight()
                    }
                    /*
                    text(Bindings.concat(
                            " Directories with all dupes with some/all of those dupes in other directories (",
                            dupeC.dupeDirElsewhereCount, ")"))
                    listview(dupeC.dupeElsewhereDirs) {
                        vgrow = Priority.SOMETIMES
                        items.onChange {
                            updateHeight()
                        }
                        updateHeight()
                    }
                    */
                    text(Bindings.concat(" Duplicated Files (", dupeC.dupeCount,")"))
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
        }
    }

    private fun ListView<String>.updateHeight() {
        val rowHeight = 24
        prefHeight = max(0, min(15, items.size)).toDouble() * rowHeight + 2
    }

    private fun updateDiffs() = runAsync { dupeC.getDupesFromAllLocalCollections(this) }
}

class DupeController : Controller() {
    private val logger: KLogger = KotlinLogging.logger {}
    val picCollectionsCont: PicCollectionsController by inject()
    var doneSearching = SimpleBooleanProperty(false)
    var dupeStrings = ArrayList<String>().observable()
    val dupeDirs = ArrayList<String>().observable()
    val dupeElsewhereDirs = ArrayList<String>().observable()
    var pfCount = SimpleIntegerProperty()
    var dupeCount = SimpleIntegerProperty()
    var dupeDirCount = SimpleIntegerProperty()
    var dupeDirElsewhereCount = SimpleIntegerProperty()
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
        val duration = measureTimeMillis { concatedPf.dupeFileSets }
        logger.info("done looking for dupes, took ${duration / 1000} seconds")
        Platform.runLater {
            // will get from cache
            concatedPf.getDupeFileList()?.let {
                dupeStrings.addAll(it)
                dupeDirs.addAll(concatedPf.getDirsWithAllDupes().sorted())
                dupeElsewhereDirs.addAll(concatedPf.getDirsWithAllDupesElsewhere().sorted())
                dupeCount.set(dupeStrings.size)
                dupeDirCount.set(dupeDirs.size)
                dupeDirElsewhereCount.set(dupeElsewhereDirs.size)
            }
            doneSearching.set(true)
        }
    }
}
