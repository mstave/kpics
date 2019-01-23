package kgui.view

import javafx.scene.control.Alert
import javafx.stage.FileChooser
import kgui.app.PicCollectionsController
import kpics.AbstractPicCollection
import kpics.LightroomDB
import kpics.LocalPicFiles
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*

class SetupForm : View() {
    private val pCont: PicCollectionsController by inject()
    private val logger: KLogger = KotlinLogging.logger {}
    private var dbs: Fieldset by singleAssign()
    private var files: Fieldset by singleAssign()
    override val root = form {
        dbs = fieldset("Lightroom database files") {
            button("Add a db file") {
                action { addDBClicked() }
            }
        }
        files = fieldset("Directories with image files") {
            button("Add a directory") {
                action { addDirClicked() }
            }
        }
        this += dbs
        this += files
        addPicLibsToForm()
    }

    private fun addDirClicked() {
        val picDir = chooseDirectory("Select Target Directory")
        picDir?.let {
            val newPicCollection = LocalPicFiles(picDir.toString())
            addPicLibToForm(newPicCollection)
            pCont.allPicLibs.add(LocalPicFiles(picDir.toString()))
        }
    }

    private fun addDBClicked() {
        val picDBs = chooseFile("Select Lightroom Database file", arrayOf(
                FileChooser.ExtensionFilter("Lightroom default extension", "*.lrcat"),
                FileChooser.ExtensionFilter("All files", "*.*"),
                FileChooser.ExtensionFilter("db files", "*.db")), FileChooserMode.Multi)
        picDBs.let {
            picDBs.forEach { picDB ->
                val newPicCollection = LocalPicFiles(picDB.toString())
                addPicLibToForm(newPicCollection)
                pCont.allPicLibs.add(LightroomDB(picDB.toString()))
            }
        }
    }

    private fun addPicLibsToForm() {
        pCont.allPicLibs.forEach { picL ->
            addPicLibToForm(picL)
        }
    }

    private fun addPicLibToForm(picL: AbstractPicCollection) {
        logger.info("Adding ${picL.baseStr}")
        val picField: Field = field {
            textfield { bind(picL.baseStr.toProperty()) }
            button("remove") {
                action {
                    //                        setOnAction {
                    val dlg = Alert(Alert.AlertType.CONFIRMATION, "${picL.baseStr}")
                    dlg.headerText = "Click OK to remove, or Cancel to abort"
                    dlg.title = "Removing entry"
                    dlg.showAndWait().ifPresent { result ->
                        println("Result is $result")
                        if (result.text == "OK") {
                            pCont.allPicLibs.remove(picL)
                            this@field.removeFromParent()
                        }
                    }
                }
            }
        }
        when (picL) {
            is LightroomDB   -> {
                dbs.add(picField)
            }
            is LocalPicFiles -> files.add(picField)
            else             -> {
                log.warning("broken config data")
            }
        }
    }
}