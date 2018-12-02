package kgui.view

import javafx.stage.FileChooser
import kgui.app.PicCollectionsController
import kpics.AbstractPicCollection
import kpics.LightroomDB
import kpics.LocalPicFiles
import mu.KLogger
import mu.KotlinLogging
import org.controlsfx.control.Notifications
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
        var picDir = chooseDirectory("Select Target Directory")
        val newPicCollection = LocalPicFiles(picDir.toString())
        addPicLibToForm(newPicCollection)
        pCont.allPicLibs.add(LocalPicFiles(picDir.toString()))
    }

    private fun addDBClicked() {
        var picDB = chooseFile("Select Lightroom Database file", arrayOf(
                FileChooser.ExtensionFilter("Lightroom default extension", "*.lrcat"),
                FileChooser.ExtensionFilter("All files", "*.*"),
                FileChooser.ExtensionFilter("db files", "*.db")))
        val newPicCollection = LocalPicFiles(picDB.toString())
        addPicLibToForm(newPicCollection)
        pCont.allPicLibs.add(LightroomDB(picDB.toString()))
    }

    private fun addPicLibsToForm() {
        pCont.allPicLibs.forEach { picL ->
            addPicLibToForm(picL)
        }
    }

    private fun addPicLibToForm(picL: AbstractPicCollection) {
        logger.info("Adding ${picL.baseStr}")
        when (picL) {
            is LightroomDB   -> dbs.add(field {
                textfield { bind(picL.baseStr.toProperty()) }
                button("remove") {
                    action {
                        //                        setOnAction {
                        Notifications.create()
                                .title("Removing!")
                                .text(picL.baseStr)
                                .owner(this)
                                .showInformation()
                    }
                }
            })
            is LocalPicFiles -> files.add(field {
                textfield { bind(picL.baseStr.toProperty()) }
                button("remove") {
                    action {
                        dialog {
                            text("Not implemented!!")
                        }
                    }
                }
            })
            else             -> {
                log.warning("broken config data")
            }
        }
    }

    class AddDirDialogView : UIComponent("Add new") {
        override val root = form {
        }
    }
}