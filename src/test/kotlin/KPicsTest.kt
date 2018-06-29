
import com.drew.imaging.ImageMetadataReader
import javafx.beans.property.SimpleListProperty
import kpics.PicDB
import kpics.PicFiles
import kpics.makePicInterface
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import tornadofx.*
import java.io.File
import java.nio.file.Path
import java.util.*

class KPicsTest {
    companion object {
        fun getBothTestPics(): Pair<PicFiles, PicDB> {
            return Pair(PicFilesTest.getTestPics(), PicDBTest.getTestPics())
        }

        fun getMD5(path: Path): String {
            val md = java.security.MessageDigest.getInstance("MD5")
            val bytes = md.digest(path.toFile().readBytes())
            var result = ""
            for (byte in bytes) {
                result += "%02x".format(byte)
            }
            return result
        }
    }

    //    @Test
//    fun testComboCount() {
//        val (pF, pDB) = getBothTestPics()
//        Assertions.assertEquals(pF.getCount(), pDB.getCount())
//
//    }
    @Test
    fun testMD5() {
    }

    @Test
    fun testComboPaths() {
        val (pF, pDB) = getBothTestPics()
        transaction {
            val fPaths: TreeSet<Path> = pF.getPaths()
            val dPaths: TreeSet<Path> = pDB.getPaths()
            val firstF = fPaths.first()
            val firstD = dPaths.first()

            Assertions.assertNotNull(firstF)
            Assertions.assertNotNull(firstD)
            val md5FirstF = getMD5(firstF!!)
            Assertions.assertEquals(md5FirstF, getMD5(firstD!!))
            Assertions.assertEquals(firstF, firstD)
            var missingCount = 0
            var matchedCount = 0
            for (f in fPaths) {
                if (!dPaths.contains(f)) {
                    missingCount++
                    println(f)
                } else matchedCount++
            }

            println("files -> db : $missingCount missing, $matchedCount matched")
            missingCount = 0
            matchedCount = 0
            for (f in dPaths) {
                if (!fPaths.contains(f)) {
                    missingCount++
                    println(f)
                } else matchedCount++
            }
            println("db -> files : $missingCount missing, $matchedCount matched")
//            Assertions.assertIterableEquals(fPaths.dd, dPaths)
//            Assertions.assertEquals(
//                    fPaths,
//                    dPaths
//            )
        }
    }

    @Test
    fun getBaseName() {
    }

    @Test
    fun toPathFromRoot() {
    }

    @Test
    fun testProp() {
        val lp = SimpleListProperty(ArrayList<String>().observable())
        lp.add("foo")
    }

    @Test
    fun toPath() {
    }

    @Test
    fun testMakePicInterfaceFile() {
        val f = makePicInterface("/Users/mstave/Dropbox/Photos/pics/2017")
        println(f.javaClass)
        Assertions.assertEquals(f.javaClass, PicFiles::class.java)
    }

    @Test
    fun testMakePicInterfaceDB() {
        val f = makePicInterface("/Users/mstave/Dropbox/pic.db")
        println(f.javaClass)
        Assertions.assertEquals(f.javaClass, PicDB::class.java)
    }

    @Test
    fun testMeta() {
        val imgPath = "/Users/mstave/Dropbox/Photos/pics/Holly/HollyCat.JPG"
//        val imgPath = "/Users/mstave/Dropbox/Photos/pics/HollyJTea.jpg"
        val item = ImageMetadataReader.readMetadata(File(imgPath))
        item.directories.forEach { dir ->
            println(dir.name)
            dir.tags.stream().forEach {
                println("\t${it.tagName} : ${it.description},  ")
            }
        }
//        item.directories.first().tags.stream().forEach {
//            println("description: ${it.description}, directoryName: ${it.directoryName}, tagName: ${it.tagName}")
//        }
    }
}