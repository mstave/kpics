package kgui.view

import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import javafx.scene.control.TableView
import javafx.scene.image.Image
import javafx.scene.paint.Color
import kgui.app.CollectionsPerPic
import kgui.app.PicCollectionsController
import kgui.exif.ExifView
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*
import java.io.File

class Diffs : View("Differences") {
    private val logger: KLogger = KotlinLogging.logger {}
    private val exif: ExifView by inject()
    private val imgPath = SimpleStringProperty()
    private val pCont: PicCollectionsController by inject()
    private val tv = tableview<CollectionsPerPic> {
        prefHeight = 1000.0
        refreshColumns()
        bindSelected(pCont.collectionsPerPicModel)
        pCont.allPicLibs.onChange {
            pCont.diffs.invalidate()
            pCont.loadDiffs()
            refreshColumns()
        }
        onSelectionChange { sel ->
            imgV.image = null
            pCont.allPicLibs.forEach { picL ->
                if (imgV.image == null) {
                    val maybeFile = picL.getFullPath(sel!!.picFilePath)
                    if (maybeFile != null && File(maybeFile).isFile) {
                        imgV.image = Image(File(maybeFile).toURI().toString())
                    }
                    imgPath.set(maybeFile.toString())
                }
            }
        }
    }

    private fun TableView<CollectionsPerPic>.refreshColumns() {
        asyncItems { pCont.diffs }
        columns.clear()
        columnResizePolicy = SmartResize.POLICY
        readonlyColumn("File", CollectionsPerPic::picFilePath).prefWidth(320).minWidth(300)
        for (v in pCont.allPicLibs) {
            readonlyColumn(v.baseStr!!, CollectionsPerPic::picLibBasePaths).minWidth(
                    150.0).cellFormat { bPaths ->
                if (bPaths.contains(v.baseStr)) {
                    text = "present"
                    style {
                        textFill = Color.DARKGREEN
                    }
                } else {
                    text = "missing"
                    style {
                        textFill = Color.RED
                    }
                }
            }
        }
    }
    private val imgV = imageview {
        isPreserveRatio = true
        exif.xCont.pathProp.bind(imgPath)
    }
    private val bottomPane = splitpane(Orientation.HORIZONTAL) {
        setDividerPosition(0, .35)
        fitToParentSize()
        add(exif)
        add(pane {
            fitToParentSize()
            prefWidth(1500.0)
            imgV.fitHeightProperty().bind(heightProperty())
            add(imgV)
        })
    }
    override val root = splitpane(Orientation.VERTICAL) {
        add(tv)
        add(bottomPane)
    }

    init {
        tv.selectionModel.clearAndSelect(1)
    }
}