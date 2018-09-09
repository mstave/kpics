package kpics

import java.io.File
import java.nio.file.Path
import java.util.*

abstract class AbstractPicCollection {
    abstract val count: Int
    abstract val paths: TreeSet<Path>
    abstract val baseStr: String?
    abstract val relativePaths: TreeSet<String?>
    abstract fun getFullPath(relPath: String): String?

    fun toRelativePathStr(path: Path): String? {
        var pathVal1 = this.baseStr?.let {
            path.toString().replace(
                    it, "")
        }
        pathVal1 = pathVal1?.replace((this.baseStr + ":/"), "")  // windows
        return pathVal1
    }

    fun containsName(fName: String): Boolean = getFullPath(fName) != null
}

fun makePicInterface(dbOrDir: String): AbstractPicCollection = when {
    File(dbOrDir).isDirectory ->
        LocalPicFiles(dbOrDir)
    else                      ->
        LightroomDB(dbOrDir)
}