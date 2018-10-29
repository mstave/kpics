package kgui.view

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.geometry.Orientation
import javafx.scene.image.Image
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kgui.exif.ExifView
import kpics.AbstractPicCollection
import kpics.LightroomDB
import kpics.LocalPicFiles
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*
import java.io.File
import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonObject
import kotlin.system.measureTimeMillis



class MainView : View("Pics") {
    private val logger: KLogger = KotlinLogging.logger {}
    private val controller: PicCollectionsController by inject()
    override val root = tabpane()
    private val dupes: DupeView by inject()

    init {
        logger.debug("MainView init")
        with(root) {
            tab("Setup") {
                this += find<DBForm>(DBForm::pCont to controller)
            }
            tab("All") {
                splitpane(Orientation.HORIZONTAL) {
                    prefWidth = 1200.0
                    minHeight = 500.0
                    for (v in controller.allPicLibs) {
                        this += find<PicsFragment>(mapOf(PicsFragment::picObj to v))
                    }
                    // even out dividers, weird that this seems to be needed
                    for (i in 0 until dividers.size) {
                        dividers[i].position = (1.0 + i) / (dividers.size + 1)
                    }
                }
            }
            tab("differences") {
                this += find<DiffView>(DiffView::pCont to controller)
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

class DiffView : View() {
    private val exif: ExifView by inject()
    private val imgPath = SimpleStringProperty()
    val pCont: PicCollectionsController by param()
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

class DBForm : View() {
    val pCont: PicCollectionsController by param()
    private val logger: KLogger = KotlinLogging.logger {}
    override val root = form()
    private var dbs = fieldset("Lightroom database files")
    private var files = fieldset("Directories with image files")

    init {
        pCont.allPicLibs.forEach { picL ->
            logger.info("Adding ${picL.baseStr}")
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
data class CollectionsPerPic(
        var filePath: String,
        var basePaths: ConcurrentSkipListSet<String?> // which piclibs have this file
                            )

class CollectionsPerPicModel : ItemViewModel<CollectionsPerPic>() {
    val filePath = bind(CollectionsPerPic::filePath)
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
class PicCollectionsController : Controller() {
    // array of each of pic collection of any type in use in the app
    private val logger: KLogger = KotlinLogging.logger {}
    internal val allPicLibs = ArrayList<AbstractPicCollection>()
    val collectionsPerPicModel = CollectionsPerPicModel()
    private var collectionsPerPic = ConcurrentHashMap<String, CollectionsPerPic>()
    var diffs = ArrayList<CollectionsPerPic>().observable()
    private fun loadDupes() {
        logger.debug("Looking for duplicates")
        allPicLibs.parallelStream().forEach { p ->
            log.fine("   Checking ${p.baseStr}")
            p.relativePaths.parallelStream().forEach { pathVal ->
                val exist = pathVal?.let {
                    collectionsPerPic.putIfAbsent(it,
                                                  CollectionsPerPic(pathVal,
                                                                    ConcurrentSkipListSet(setOf(p.baseStr))))
                }
                exist?.let {
                    collectionsPerPic[pathVal]!!.basePaths.add(p.baseStr)
                }
            }
            diffs = ArrayList(collectionsPerPic.values).observable() // update table after each "column"
            logger.debug("----- Done: ${p.baseStr}")
        }
        logger.debug("-----====  DUPE LOADING COMPLETE")
    }

    init {
        loadPicConfig()
        loadDupes()
    }

    private fun loadPicConfig() {
        val picdbs: JsonArray = app.config.jsonArray("lightroomDbs")!!
        lateinit var picFiles: JsonArray
        if (!app.config.containsKey("lightroomDbs")) {
            createConfig(app)
        } else {
            logger.warn("No property file detected")
            val ex = """
lightroomDbs=[{"path"\:"g\:\\\\Dropbox\\\\pic.db"},{"path"\:"c\:\\\\Temp\\\\Lightroom Catalog.lrcat"}]
localdirs=["g\:\\\\Dropbox\\\\Photos\\\\pics"]
"""
            println("Create conf/[hostname]/app.properties with contents like")
            println(ex)
        }
        picdbs.let { jsonArray ->
            for (dbPath in jsonArray) {
                dbPath.asJsonObject().toModel<PicDBModel>().pdb?.let { allPicLibs.add(it) }
            }
        }
        app.config.jsonArray("localDirs")?.let {
            picFiles = it
            for (path in picFiles) {
                path.asJsonObject().toModel<PicFileModel>().pf?.let { picFiles1 -> allPicLibs.add(picFiles1) }
            }
        } ?: logger.warn("No local paths specified")
    }
}

fun createConfig(app: App) {
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
                             label("Count: " + picObj.count + "\tName: " + picObj.toString()))
}

class DupeView : View() {
    val dupeC: DupeController by inject()
    override val root = scrollpane(true, true) {
        vbox {
            label("Searching for duplicates, this may take a few minutes...", progressindicator()) {
                visibleWhen(!dupeC.doneSearching)
                managedWhen(!dupeC.doneSearching)
            }
            listview<String> {
                prefWidth = 300.0
                minHeight = 600.0
                vgrow = Priority.ALWAYS
                runAsync {
                    dupeC.updateDupes()
                } ui {
                    items = dupeC.dupeStrings
                }
            }
        }
    }
}

class DupeController : Controller() {
    private val logger: KLogger = KotlinLogging.logger {}
    private val picCollectionsCont: PicCollectionsController by inject()
    private val dupesProperty = SimpleObjectProperty<HashSet<HashSet<String>>>()
    private var dupes by dupesProperty
    var doneSearching = SimpleBooleanProperty(false)
    var dupeStrings = ArrayList<String>().observable()
    private var justFiles = picCollectionsCont.allPicLibs.filter { it is LocalPicFiles }.map { it as LocalPicFiles }
    private var pf: LocalPicFiles? = justFiles.first()
    fun updateDupes() {
        logger.info("Looking for dupes")
        val duration = measureTimeMillis { dupes = pf?.getDupes() }
        logger.info("done looking for dupes, took ${duration / 1000} seconds")
        pf?.getDupeFileList()?.let { dupeStrings.addAll(it) }
        doneSearching.set(true)
    }
}