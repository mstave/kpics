package kgui.view

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
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
    private val logger: KLogger = KotlinLogging.logger {}
    val dupeC: DupeController by inject()
    private val status: TaskStatus by inject()
    var itemsP = mutableListOf<String>().observable()
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
                items = this@Dupes.itemsP
                prefWidth = 300.0
                minHeight = 600.0
                vgrow = Priority.ALWAYS
                dupeC.picCollectionsCont.allPicLibs.onChange {
                    logger.info("got change message")
                    runAsync {
                        dupeC.updateDupes(this)
                    } ui {
                        this@Dupes.itemsP.addAll(dupeC.dupeStrings)
                    }
                }
                updateDiffs()
            }
        }
    }

    private fun updateDiffs() {
        logger.info("updateDiffs")
        runAsync {
            dupeC.updateDupes(this)
        } ui {
            this@Dupes.itemsP.addAll(dupeC.dupeStrings)
        }
    }
}

class DupeController : Controller() {
    private val logger: KLogger = KotlinLogging.logger {}
    val picCollectionsCont: PicCollectionsController by inject()
    private val dupesProperty =
            SimpleObjectProperty<HashSet<HashSet<String>>>()
    private var dupes by dupesProperty
    var doneSearching = SimpleBooleanProperty(false)
    var dupeStrings = ArrayList<String>().observable()
    var concatedPf = LocalPicFiles("")
    var pfCount = SimpleIntegerProperty()
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
        var justFiles = picCollectionsCont.allPicLibs.filter { it is LocalPicFiles }.map { it as LocalPicFiles }
        justFiles.forEach {
            concatedPf.filePaths.addAll(it.filePaths)
        }
        Platform.runLater {
            pfCount.set(concatedPf.filePaths.count())
        }
        concatedPf.updateFunc = ::updateStatus
        concatedPf.dupesChecked = false
        val duration = measureTimeMillis { dupes = concatedPf.getDupes() }
        logger.info("done looking for dupes, took ${duration / 1000} seconds")
        logger.info("checked == " + concatedPf.dupesChecked)
        concatedPf.dupesChecked = true
        concatedPf.getDupeFileList()?.let { dupeStrings.addAll(it) }
        doneSearching.set(true)
    }
}