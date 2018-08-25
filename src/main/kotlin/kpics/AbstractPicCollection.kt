package kpics

import java.io.File
import java.nio.file.Path
import java.util.*

abstract class AbstractPicCollection {
    abstract val count: Int
    abstract val paths: TreeSet<Path>
    abstract val baseStr: String?
    abstract val relativePaths: TreeSet<String?>
    fun toRelativePathStr(path: Path): String? {
        var pathVal1: String? = this.baseStr?.let {
            path.toString().replace(
                    it, "")
        }
//        (this.baseStr + "/"), "")
        pathVal1 = pathVal1?.replace((this.baseStr + ":/"), "")  // windows
        return pathVal1
    }

    abstract fun getFullPath(relPath: String): String?
    fun containsName(fName: String): Boolean {
        val full = getFullPath(fName)
        return full != null
    }
}

fun makePicInterface(dbOrDir: String): AbstractPicCollection = when {
        File(dbOrDir).isDirectory ->
            PicFiles(dbOrDir)
        else                      ->
            PicDB(dbOrDir)
    }