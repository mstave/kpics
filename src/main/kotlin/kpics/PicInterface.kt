package kpics

import java.io.File
import java.nio.file.Path
import java.util.*

interface PicInterface {
    fun getCount(): Int
    fun getPaths(): TreeSet<Path>
    fun getBaseStr(): String?
    val relativePathSet: HashSet<String>?
    fun toRelativePathStr(path: Path): String {
        var pathVal1: String = path.toString().replace(
                (this.getBaseStr() + "/"), "")
        pathVal1 = pathVal1.replace((this.getBaseStr() + ":/"), "")  // windows
        return pathVal1
    }

    fun getFullPath(relPath: String): String?

}

fun makePicInterface(dbOrDir: String): PicInterface = when {
        File(dbOrDir).isDirectory ->
            PicFiles(dbOrDir)
        else                      ->
            PicDB(dbOrDir)
    }