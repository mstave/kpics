package kgui.exif

import com.drew.imaging.ImageMetadataReader
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.control.TreeItem
import tornadofx.*
import java.io.File
import java.util.*

data class ExifGroup(val name: String, val tagName: String, val desc: String, val children: List<ExifGroup>? = null)
data class ExifDetail(val k: String, val v: String)
class ExifViewModel(imgPath: String) : ItemViewModel<ExifGroup>() {
    init {
        val data = ImageMetadataReader.readMetadata(File(imgPath))

        data.directories.first().tags.stream().forEach {
            println("description: ${it.description}, directoryName: ${it.directoryName}, tagName: ${it.tagName}")
            println(it.directoryName)
        }
    }
}

class ExifController : ItemViewModel<ExifGroup>() {
    var pathProp = SimpleStringProperty()
    private val fixedProp = SimpleStringProperty("start")
    var listProp = SimpleListProperty(ArrayList<ExifGroup>().observable())
    private fun updateList() {
        if (pathProp.value != null && File(pathProp.get()).isFile) {
            if (listProp.size > 0)
                listProp.clear()
            val data = ImageMetadataReader.readMetadata(File(pathProp.get()))
            // TODO replace with something like fold
            data.directories.forEach { dir ->
                val child = ArrayList<ExifGroup>()
                dir.tags.stream().forEach {
                    if (!it.tagName.startsWith("Unknown"))
                        child.add(ExifGroup("", it.tagName, it.description, null))
                }
                listProp.value.add(ExifGroup(dir.name, "", "", child))
            }
        }
    }

    init {
        pathProp.onChange {
            updateList()
            println(listProp.size)
        }
    }
}

class ExifView : View() {
    //    val imgFilePath : String by param()
    val xCont: ExifController by inject()
    override val root = scrollpane(true, true)

    init {
//        vbox {
        /*
        hbox {
            label("File Name") {
                hboxConstraints { margin = Insets(5.0) }
            }
            textfield {
                hboxConstraints { margin = Insets(5.0) }
                useMaxWidth = true
                this.textProperty().bind(xCont.pathProp)
            }
        }
*/
        treetableview<ExifGroup> {
            column("Category", ExifGroup::name) { minWidth(150.0) }
            column("Tag", ExifGroup::tagName) { minWidth(150.0) }
            column("Description", ExifGroup::desc) {
                minWidth(50.0)
                prefWidth(450)
            }
            root = TreeItem(ExifGroup("data", "", "", xCont.listProp))
            populate {
                it.value.children
            }
            root.isExpanded = true
            root.children.forEach { it.isExpanded = true }
            smartResize()
            // Resize to display all elements on the first two levels
            resizeColumnsToFitContent()
            this.fitToParentSize()
        }
    }
//    }
}