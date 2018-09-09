package kgui.view

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.image.Image
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kgui.exif.ExifView
import kpics.AbstractPicCollection
import kpics.LightroomDB
import kpics.LocalPicFiles
import tornadofx.*
import java.io.File
import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import javax.json.Json
import javax.json.JsonObject

class MainView : View("Pics") {
    private val controller: PicsController by inject()
    override val root = tabpane()
    private val exif: ExifView by inject()
    private val imgPath = SimpleStringProperty()
    private val dupes: DupeView by inject()

    init {
        with(root) {
            tab("Setup") {
                this += find<DBForm>(DBForm::pCont to controller)
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
                        readonlyColumn(v.baseStr!!, UberFile::basePaths).minWidth(
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
    private var dbs = fieldset("Lightroom database files")
    private var files = fieldset("Directories with image files")

    init {
        pCont.allPicLibs.forEach { picL ->
            log.info("Adding ${picL.baseStr}")
            when (picL) {
                is LightroomDB   -> dbs.add(field {
                    textfield { bind(picL.baseStr.toProperty()) }
                    button("remove") {
                        setOnAction {
                            org.controlsfx.control.Notifications.create()
                                    .title("Removing!")
                                    .text(picL.baseStr)
                                    .owner(this)
                                    .showInformation()
                        }
                    }
                })
                is LocalPicFiles -> files.add(field {
                    textfield { bind(picL.baseStr.toProperty()) }
                    button("remove")
                })
                else             -> {
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
    var pdb: LightroomDB? = null
    override fun updateModel(json: JsonObject) {
        with(json) {
            val pt = string("path")
            pdb = LightroomDB(pt)
        }
    }

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("path", pdb?.baseStr)
        }
    }
}

/**
 * Used for serializing LocalPicFiles to JSON
 *
 */
class PicFileModel : JsonModel {
    var pf: LocalPicFiles? = null
    override fun updateModel(json: JsonObject) {
        with(json) {
            pf = string("path")?.let { LocalPicFiles(it) }
        }
    }

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("path", pf?.baseStr)
        }
    }
}

/**
 * All the data manipulation heavy lifting
 */
class PicsController : Controller() {
    internal val allPicLibs = ArrayList<AbstractPicCollection>()
    val uberFileModel = UberFileModel()
    private var uberMap = ConcurrentHashMap<String, UberFile>()
    var diffs = ArrayList<UberFile>().observable()
    private val picdbs = app.config.jsonArray("lightroomDbs")
    val picFiles = app.config.jsonArray("localDirs")

    init {
        log.info("Loading")
        loadPicConfig()
        allPicLibs.parallelStream().forEach { p ->
            log.info("Loading ${p.baseStr}")
            p.relativePaths.forEach { pathVal ->
                var pathValXPlatform = pathVal
                if (File.separatorChar == '\\') {
                    pathValXPlatform = pathVal?.replace(File.separatorChar, '/')
                }
                    val exist = pathValXPlatform?.let {
                        uberMap.putIfAbsent(it,
                                            UberFile(pathValXPlatform,
                                                     ConcurrentSkipListSet(setOf(p.baseStr))))
                    }
                    exist?.let {
                        uberMap[pathValXPlatform]!!.basePaths.add(p.baseStr)
                }
            }
        }
        diffs = ArrayList(uberMap.values).observable() // update table after each "column"
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
        picdbs?.let { jsonArray ->
            for (dbPath in jsonArray) {
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
        pm1.pdb = LightroomDB(one)
        val pm2 = PicDBModel()
        pm2.pdb = LightroomDB(two)
        val pfm1 = PicFileModel()
        pfm1.pf = LocalPicFiles(three)

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
    val picObj: AbstractPicCollection by param()
    private val lview = listview<String> {
        prefWidth = 300.0
        minHeight = 600.0
        items = picObj.relativePaths.toList().observable()
        selectionModel.select(0)
        vgrow = Priority.ALWAYS
    }
    override val root = VBox(picObj.baseStr?.let { label(it) }, lview,
                             label("Count: " + picObj.count + "\tName: " + picObj.toString())
                            )
}

class DupeView : View() {
    override val root = scrollpane(true, true)
    private val dupeC: DupeController by inject()
    private var msg = text("Searching for duplicates, this may take a few minutes...")

    init {
        with(root) {
            vbox {
                msg.visibleWhen(!dupeC.doneSearching)
                this.add(msg)

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
    private val dupesProperty = SimpleObjectProperty<HashSet<HashSet<String>>>()
    private var dupes by dupesProperty
    var doneSearching = SimpleBooleanProperty(false)
    var dupeStrings = ArrayList<String>().observable()
    private var pf: LocalPicFiles? = null

    init {
        runAsync {
            picsCont.picFiles?.first()?.asJsonObject()?.toModel<PicFileModel>()?.pf?.let {
                pf = it
                dupes = it.getDupes()
            }
            doneSearching.set(true)
        } ui { _ ->
            pf?.let {
                dupeStrings.addAll(it.getDupeFileList())
            }
        }
    }
}