package kgui.view

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifThumbnailDirectory
import com.drew.metadata.exif.ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.control.TreeItem
import javafx.scene.image.Image
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kpics.PicDB
import kpics.PicFiles
import kpics.PicInterface
import tornadofx.*
import java.io.File
import java.io.FileInputStream
import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import javax.json.Json
import javax.json.JsonObject
import org.controlsfx.control.Notifications

class MainView : View("Pics") {
    private val controller: PicsController by inject()
    override val root = tabpane()
    val exif: ExifView by inject()
    val imgPath = SimpleStringProperty()
    val dupes: DupeView by inject()

    init {
        with(root) {
            tab("Setup") {
                this += find<DBForm>(DBForm::pCont to controller)
//                form() {
                //                    DB
//                    text("Lightroom database files")
//                    fieldset("DB files") {
//                        field("path") {
//
//                        }
//                    }
//                    text("Local directories with image files")
//                }
            }
            tab("All") {
                splitpane(Orientation.HORIZONTAL) {
                    setDividerPositions(0.33, 0.66)
                    prefWidth = 1200.0
                    minHeight = 500.0
                    for (v in controller.allPicLibs) {
                        this += find<PicsFragment>(mapOf(PicsFragment::picObj to v))
                    }
                }
            }
            tab("differences") {
                val tv = tableview<UberFile> {
                    asyncItems { controller.diffs }
                    columnResizePolicy = SmartResize.POLICY
                    readonlyColumn("File", UberFile::filePath).prefWidth(320)
                    for (v in controller.allPicLibs) {
                        readonlyColumn(v.getBaseStr()!!, UberFile::basePaths).minWidth(
                                150.0).cellFormat { bPaths ->
                            if (bPaths.contains(v.getBaseStr())) {
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
                    selectFirst()
                    bindSelected(controller.uberFileModel)
                }
                tv.prefHeight = 1000.0
                val imgV = imageview()
                imgV.isPreserveRatio = true
                val iPane = hbox {
                }
                val pathText = text(controller.uberFileModel.filePath)
                pathText.vboxConstraints { margin = Insets(5.0) }
                val info = vbox()
                imgV.fitHeightProperty().bind(iPane.heightProperty())
//                info.add(pathText)
                exif.xCont.pathProp.bind(imgPath)
//                imgPath.bind(exif.xCont.pathProp)
                info.add(exif)
                iPane.add(info)
                iPane.add(pane { add(imgV) })
                vbox {
                    //                    tv.bindSelected(Property)
                    tv.onSelectionChange { sel ->
                        imgV.image = null
                        controller.allPicLibs.forEach { picL ->
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
                splitpane(Orientation.VERTICAL) {
                    add(tv)
                    add(iPane)
                }
            }
            tab("EXIF details") {
                //                this += exif.root
            }
            tab("Dupes") {
                this += dupes.root
            }
        }
    }
}

class DBForm : View() {
    val pCont: PicsController by param()
    override val root = form()
    var dbs = fieldset("Lightroom database files")
    var files = fieldset("Directories with image files")

    init {
        pCont.allPicLibs.forEach { picL ->
            log.info("Adding ${picL.getBaseStr()}")
            when (picL) {
                is PicDB    -> dbs.add(field {
                    textfield { bind(picL.getBaseStr().toProperty()) }
                    button("remove") {
                        setOnAction {
                            org.controlsfx.control.Notifications.create()
                                    .title("Removing!")
                                    .text("${picL.getBaseStr()}")
                                    .owner(this)
                                    .showInformation()
                        }
                    }
                })
                is PicFiles -> files.add(field {
                    textfield { bind(picL.getBaseStr().toProperty()) }
                    button("remove")
                })
                else        -> {
                    log.warning("broken config data")
                }
            }
        }
        dbs.add(button("Add a db file"))
        files.add(button("Add a directory"))
        root.add(dbs)
        root.add(files)
    }
}

/**
 * Aggregates picture paths from all sources into one data structure
 */
data class UberFile(
        var filePath: String,
        var basePaths: ConcurrentSkipListSet<String?> // which piclibs have this file
                   )

class UberFileModel : ItemViewModel<UberFile>() {
    val filePath = bind(UberFile::filePath)
//    val basePaths = bind(UberFile::basePaths)
}

/**
 * Used for serializing PicDbs to JSON
 * In this file rather than alongside the source to
 * keep the TonadoFX stuff separate
 */
class PicDBModel : JsonModel {
    var pdb: PicDB? = null
    override fun updateModel(json: JsonObject) {
        with(json) {
            val pt = string("path")
            pdb = PicDB(pt)
        }
    }

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("path", pdb?.getBaseStr())
        }
    }
}

/**
 * Used for serializing PicFiles to JSON
 *
 */
class PicFileModel : JsonModel {
    var pf: PicFiles? = null
    override fun updateModel(json: JsonObject) {
        with(json) {
            pf = string("path")?.let { PicFiles(it) }
        }
    }

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("path", pf?.getBaseStr())
        }
    }
}

/**
 * All the data manipulation heavy lifting
 */
class PicsController : Controller() {
    internal val allPicLibs = ArrayList<PicInterface>()
    val uberFileModel = UberFileModel()
    private var uberMap = ConcurrentHashMap<String, UberFile>()
    var diffs = ArrayList<UberFile>().observable()
    val picdbs = app.config.jsonArray("lightroomDbs")
    val picFiles = app.config.jsonArray("localDirs")

    init {
        log.info("Loading")
        loadPicConfig()
        allPicLibs.parallelStream().forEach { p ->
            log.info("Loading ${p.getBaseStr()}")
            p.relativePathSet?.forEach { pathVal ->
                var pathValXPlatform = pathVal
                if (File.separatorChar == '\\') {
                    pathValXPlatform = pathVal.replace(File.separatorChar, '/')
                }
                val exist = uberMap.putIfAbsent(pathValXPlatform,
                                                UberFile(pathValXPlatform,
                                                         ConcurrentSkipListSet(setOf(p.getBaseStr()))))
                exist?.let {
                    uberMap[pathValXPlatform]!!.basePaths.add(p.getBaseStr())
                }
            }
            diffs = ArrayList(uberMap.values).observable() // update table after each "column"
        }
    }

    private fun loadPicConfig() {
        if (!app.config.containsKey("lightroomDbs")) {
            createConfig()
        } else {
            println("No property file detected")
            val ex = """
lightroomDbs=[{"path"\:"g\:\\\\Dropbox\\\\pic.db"},{"path"\:"c\:\\\\Temp\\\\Lightroom Catalog.lrcat"}]
localdirs=["g\:\\\\Dropbox\\\\Photos\\\\pics"]
"""
            println("Create conf/[hostname]/app.properties with contents like")
            println(ex)
        }
        picdbs?.let {
            for (dbPath in it) {
                dbPath.asJsonObject().toModel<PicDBModel>().pdb?.let { allPicLibs.add(it) }
            }
        }
        if (picFiles == null) {
            log.warning("No local paths specified")
        } else for (path in picFiles) {
            path.asJsonObject().toModel<PicFileModel>().pf?.let { picFiles1 -> allPicLibs.add(picFiles1) }
        }
    }

    private fun createConfig() {
        println("Creating config for ${InetAddress.getLocalHost().hostName}")
        val drop = "/Users/mstave/Dropbox"
        var two = "/Users/mstave/temp/sb.db"
        var one = "$drop/pic.db"
        var three = "$drop/Photos/pics"
        if (InetAddress.getLocalHost().hostName == "lp") {
            one = "g:\\Dropbox\\pic.db"
            two = "c:\\Temp\\Lightroom Catalog.lrcat"
            three = "g:\\Dropbox\\Photos\\pics"
        }
//        lightroomDbs=[{"path"\:"g\:\\\\Dropbox\\\\pic.db"},{"path"\:"c\:\\\\Temp\\\\Lightroom Catalog.lrcat"}]
//        localDirs=[{"path"\:"g\:\\\\Dropbox\\\\Photos\\\\pics"}]
        val pm1 = PicDBModel()
        pm1.pdb = PicDB(one)
        val pm2 = PicDBModel()
        pm2.pdb = PicDB(two)
        val pfm1 = PicFileModel()
        pfm1.pf = PicFiles(three)

        with(app.config) {
            set("lightroomDbs" to Json.createArrayBuilder().add(pm1.toJSON().asJsonObject()).add(
                    pm2.toJSON().asJsonObject()).build())
            set("localDirs" to Json.createArrayBuilder().add(pfm1.toJSON().asJsonObject()).build())
            save()
        }
        println("Config complete")
    }
}

class PicsFragment : Fragment() {
    val picObj: PicInterface by param()
    private val lview = listview<String> {
        prefWidth = 300.0
        minHeight = 600.0
        items = picObj.relativePathSet!!.toList().observable()
        selectionModel.select(0)
        vgrow = Priority.ALWAYS
    }
    override val root = VBox(picObj.getBaseStr()?.let { label(it) }, lview,
                             label("Count: " + picObj.getCount() + "\tName: " + picObj.toString())
                            )
}

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
//   val imgFilePath : String by param() dd
}

class ExifController : ItemViewModel<ExifGroup>() {
    //class ExifController(val imgFilePath : String) : Controller() {
    var pathProp = SimpleStringProperty()
    val fixedProp = SimpleStringProperty("start")
    var listProp = SimpleListProperty(ArrayList<ExifGroup>().observable())
    fun updateList() {
        println(pathProp)
        if (pathProp.value != null && File(pathProp.get()).isFile) {
            if (listProp.size > 0)
                listProp.clear()
            val data = ImageMetadataReader.readMetadata(File(pathProp.get()))
            data.directories.forEach { dir ->
                val child = ArrayList<ExifGroup>()
                dir.tags.stream().forEach {
                    println(it.description)
                    if (!it.tagName.startsWith("Unknown"))
                        child.add(ExifGroup("", it.tagName, it.description, null))
                }
                listProp.value.add(ExifGroup(dir.name, "", "", child))
            }
            fixedProp.set("Newval")
            fixedProp.value = "beans"
            println(listProp.size)
            println(listProp.value.size)
        }
    }

    init {
        pathProp.onChange {
            updateList()
            println(listProp.size)
        }
    }
}

class TestV : View() {
    override val root = pane()

    init {
        // off 56320
        // len 17015
        with(root) {
            this += imageview {
                //                var i = FileInputStream(File("/tmp/b.cr2"))
//                i.skip(56320)
//                image = Image(i)
//                i.close()
                minWidth(1000.0)
                prefWidth = 1000.0
                prefHeight = 1000.0

                isPreserveRatio = true
                val i = FileInputStream(File("/tmp/c.arw"))
//                var i = FileInputStream(File("/tmp/b.cr2"))
                val i2 = ImageMetadataReader.readMetadata(i)
                println(i2)
                i.close()
                val thumbnailDirectory = i2.getFirstDirectoryOfType(ExifThumbnailDirectory::class.java)
                val n = thumbnailDirectory.getString(TAG_THUMBNAIL_OFFSET)
                val jj = FileInputStream(File("/tmp/c.arw"))
//                var jj = FileInputStream(File("/tmp/b.cr2"))
                jj.skip(n.toLong())
                image = Image(jj, 800.0, 0.0, true, true)

                isPreserveRatio = true
                minWidth = 1000.0
                minHeight = 1000.0
                prefHeight = 1000.0
            }
        }
    }
}

class ExifView : View() {
    //    val imgFilePath : String by param()
    val xCont: ExifController by inject()
    override val root = scrollpane(true, true)

    init {
        vbox {
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
                column("Description", ExifGroup::desc)
                root = TreeItem(ExifGroup("data", "", "", xCont.listProp))
                populate {
                    it.value.children
                }
                root.isExpanded = true
                root.children.forEach { it.isExpanded = true }
                smartResize()
                // Resize to display all elements on the first two levels
                resizeColumnsToFitContent()
            }
        }
    }
}

class DupeView : View() {
    override val root = scrollpane(true, true)
    private val dupeC: DupeController by inject()

    init {
        with(root) {
            vbox {
                listview<String> {
                    prefWidth = 300.0
                    minHeight = 600.0
                    items = dupeC.dupeStrings
                    vgrow = Priority.ALWAYS
                }
            }
        }
    }
}

class DupeController : Controller() {
    private val picsCont: PicsController by inject()
    val dupesProperty = SimpleObjectProperty<ConcurrentHashMap<String, ConcurrentSkipListSet<String>>>(
            ConcurrentHashMap<String, ConcurrentSkipListSet<String>>())
    var dupes by dupesProperty
    var dupeStrings = ArrayList<String>().observable()
    fun getTheDupes(): ConcurrentHashMap<String, ConcurrentSkipListSet<String>>? {
        return dupes
    }

    init {
        runAsync {
            picsCont.picFiles?.first()?.asJsonObject()?.toModel<PicFileModel>()?.pf?.let {
                dupes = it.getDupes()
            }
        } ui {
            dupes.forEach() {
                dupeStrings.add(it.key + " : " + it.value.toString())
            }
        }
    }
}