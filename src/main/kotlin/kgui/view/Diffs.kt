package kgui.view

import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import javafx.scene.image.Image
import javafx.scene.paint.Color
import kgui.app.CollectionsPerPic
import kgui.app.PicCollectionsController
import kgui.exif.ExifView
import tornadofx.*
import java.io.File

class Diffs : View("Differences") {
    private val exif: ExifView by inject()
    private val imgPath = SimpleStringProperty()
    private val pCont: PicCollectionsController by inject()
    private val tv = tableview<CollectionsPerPic> {
        prefHeight = 1000.0
        asyncItems { pCont.diffs }
        columnResizePolicy = SmartResize.POLICY
        readonlyColumn("File", CollectionsPerPic::filePath).prefWidth(320)
        for (v in pCont.allPicLibs) {
            readonlyColumn(v.baseStr!!, CollectionsPerPic::basePaths).minWidth(
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
        bindSelected(pCont.collectionsPerPicModel)
        onSelectionChange { sel ->
            imgV.image = null
            pCont.allPicLibs.forEach { picL ->
                if (imgV.image == null) {
                    val maybeFile = picL.getFullPath(sel!!.filePath)
                    if (maybeFile != null && File(maybeFile).isFile) {
                        imgV.image = Image(File(maybeFile).toURI().toString())
                    }
                    imgPath.set(maybeFile.toString())
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
//        tv.selectionModel.selectFirst()
//        imgV.fitHeightProperty().bind(bottomPane.heightProperty())
    }
}