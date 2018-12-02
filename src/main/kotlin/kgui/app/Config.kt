package kgui.app

import kpics.AbstractPicCollection
import kpics.LightroomDB
import kpics.LocalPicFiles
import mu.KLogger
import mu.KotlinLogging
import tornadofx.*
import java.net.InetAddress
import java.util.ArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonObject

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
                                                                             ConcurrentSkipListSet(
                                                                                     setOf(p.baseStr))))
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
        val picdbs: JsonArray? = app.config.jsonArray("lightroomDbs")
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
            if (jsonArray != null) {
                for (dbPath in jsonArray) {
                    dbPath.asJsonObject().toModel<PicDBModel>().pdb?.let { allPicLibs.add(it) }
                }
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
        one = "f:\\Dropbox\\pic.db"
        two = "c:\\Temp\\Lightroom Catalog.lrcat"
        three = "f:\\Dropbox\\Photos\\pics"
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