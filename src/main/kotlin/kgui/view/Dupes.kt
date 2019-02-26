package kgui.view

import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.ListView
import javafx.scene.layout.Priority
import kgui.app.PicCollectionsController
import kpics.LocalPicFiles
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*
import java.nio.file.Paths
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.system.measureTimeMillis

class Dupes : View() {
    val dupeC: DupeController by inject()
    //                            minHeight = 600.0
    private val logger: KLogger = KotlinLogging.logger {}
    private val status: TaskStatus by inject()
    private val dupeDirView = listview(dupeC.dupeDirs) {
        bindSelected(dupeC.selectedDir)
//        updateHeight()
    }
    private val allDupeFileView = listview(dupeC.dupeStrings) {
        multiSelect(true)
        dupeC.selectedFiles.setAll(selectionModel.selectedItems)
//                        vgrow = Priority.ALWAYS
//                        selectionModel.selectedItems.onChange {
////                            selectedFiles
//                        }
//                        this.selectionModel.
//                            selectedFiles = selectionModel.selectedItems
        dupeC.selectedDir.onChange { _ ->
            selectWhere {
                it.startsWith(dupeC.selectedDir.get())
            }
//                            selectedFiles.setAll(selectionModel.selectedItems)
            //                           items.forEach {
            //                               if (it.startsWith(selectedDir.get())) {
            //                                   selectionModel.selectedItems.add(it)
            //                               }
            //                           }
        }
        dupeC.picCollectionsCont.allPicLibs.onChange {
            updateDiffs()
        }
        updateDiffs()
    }
    private val defaultWidth = 600.0
    //    val selectedFiles = SimpleListProperty<String>()
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
                titledpane("Select duplicated files:") {
//                    hgrow = Priority.
                    hbox {
                        titledpane(Bindings.concat(" Directories with all dupes (",
                                                   dupeC.dupeDirCount, ")")) {
                            prefWidth=defaultWidth
                            vbox {
                                hbox {
                                    button("Select") {
                                        tooltip("Add all files in the chosen directories to selected list")
                                        action {
                                            dupeC.selectedFiles.addAll(dupeC.concatedPf.getDupesInDir(
                                                    Paths.get(dupeC.selectedDir.value)).map { it.toString() })
                                            dupeC.selectedFiles.sort()
                                        }
                                    }
                                    button("Deselect files from this directory") {
                                        action {
                                            dupeC.selectedFiles.removeAll(dupeC.concatedPf.getDupesInDir(
                                                    Paths.get(dupeC.selectedDir.value)).map { it.toString() })
                                            dupeC.selectedFiles.sort()
                                        }
                                    }
                                    button("Clear selection") {
                                        action {
                                            dupeDirView.selectionModel.clearSelection()
                                        }
                                    }
                                }
                                this += dupeDirView
                            }
                        }
                        titledpane(Bindings.concat(" All Duplicated Files (", dupeC.dupeCount, ")")) {
//                            hgrow = Priority.SOMETIMES
                            prefWidth = defaultWidth
                            vbox {
                                hbox {
                                    button("Add") {
                                        tooltip("Add these files to selected list")
                                        action {
                                            dupeC.selectedFiles.addAll(dupeC.selectedFromAllFiles)
                                        }
                                    }
                                    button("clear") {
                                        action {
                                            allDupeFileView.selectionModel.clearSelection()
                                        }
                                    }
                                }
                                this += allDupeFileView
                            }
                        }
                    }
                }
                titledpane("Act upon selected duplicated files:") {
                    hbox {
                        titledpane(Bindings.concat(" Selected Duplicated Files (", dupeC.dupeCount, ")")) {
                            prefWidth= defaultWidth
                            listview(dupeC.selectedFiles) {
                                vgrow = Priority.SOMETIMES
                            }
                        }
                        titledpane("Duplicates of selected files") {
                            prefWidth= defaultWidth
                            listview<String> {
                                fitToParentSize()
                                dupeC.selectedFiles.onChange { sFiles ->
                                    items.setAll(sFiles.list.flatMap {
                                        dupeC.concatedPf.getDupesForFile(it)
                                    }.sorted())
                                }
                            }
                        }
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
    val selectedDir = SimpleStringProperty("")
    var selectedFiles = observableList<String>()
    var selectedFromAllFiles = observableList<String>()
    val concatedPf = LocalPicFiles("/dev/null")
    fun getDupesFromAllLocalCollections(fxTask: FXTask<*>) {
        //        val concatedPf = LocalPicFiles("/dev/null")
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
