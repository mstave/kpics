package kpics

import java.io.File
import java.nio.file.Path
import java.util.*

abstract class AbstractPicCollection {
    abstract val count: Int
    abstract val paths: TreeSet<Path>
    abstract val baseStr: String?
    abstract val relativePaths: TreeSet<String?>
    abstract fun getFullPath(relPath: String?): String?

    fun toRelativePathStr(path: Path): String? {
        this.baseStr?.let { bstr ->
            var pathMinusBase = path.toString().replaceFirst(bstr, "")
            if (pathMinusBase.startsWith("/", 0)) {
                pathMinusBase = pathMinusBase.replaceFirst("/", "")
            }
            return pathMinusBase
        }
        return null
    }

    fun containsName(fName: String): Boolean = getFullPath(fName) != null
}

fun makePicInterface(dbOrDir: String): AbstractPicCollection = when {
    File(dbOrDir).isDirectory ->
        LocalPicFiles(dbOrDir)
    else                      ->
        LightroomDB(dbOrDir)
}