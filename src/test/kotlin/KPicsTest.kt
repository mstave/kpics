import com.drew.imaging.ImageMetadataReader
import javafx.beans.property.SimpleListProperty
import kpics.LightroomDB
import kpics.LocalPicFiles
import kpics.makePicInterface
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tornadofx.*
import java.io.File
import java.nio.file.Path
import java.util.*

class KPicsTest {
    companion object {
        fun getBothTestPics(): Pair<LocalPicFiles, LightroomDB> {
            return Pair(LocalPicFilesTest.getTestPics(), LightroomDBTest.getCustomTestPics())
        }

    }

    // TODO re-enable after adding sample db
    @Disabled
    @Test
    fun testComboPaths() {
        val (pF, pDB) = getBothTestPics()
        transaction {
            val fPaths: TreeSet<Path> = pF.paths
            val dPaths: TreeSet<Path> = pDB.paths
            val firstF = fPaths.first()
            val firstD = dPaths.first()

            Assertions.assertNotNull(firstF)
            Assertions.assertNotNull(firstD)
//            Assertions.assertEquals(md5FirstF, getMD5(firstD!!))
//            Assertions.assertEquals(firstF, firstD)
            var missingCount = 0
            var matchedCount = 0
            for (f in fPaths) {
                if (!dPaths.contains(f)) {
                    missingCount++
                } else matchedCount++
            }

            println("files -> db : $missingCount missing, $matchedCount matched")
            missingCount = 0
            matchedCount = 0
            for (f in dPaths) {
                if (!fPaths.contains(f)) {
                    missingCount++
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
    fun testProp() {
        val lp = SimpleListProperty(ArrayList<String>().observable())
        lp.add("foo")
    }

    @Test
    fun toPath() {
    }

    @Test
    fun testBaseStr() {
        Assertions.assertTrue(LocalPicFilesTest.getTestPics().baseStr.endsWith(File.separator))
    }

    @Test
    fun testTrailingSlash() {
        val p = makePicInterface(LocalPicFilesTest.getTestPics().baseStr)
        Assertions.assertNotNull(p.baseStr)
        Assertions.assertTrue(p.baseStr!!.endsWith(File.separator))
    }

    @Test
    fun testMakePicInterfaceFile() {
        val f = makePicInterface(LocalPicFilesTest.getTestPics().baseStr)
        Assertions.assertEquals(f.javaClass, LocalPicFiles::class.java)
    }

    @Test
    fun testMakePicInterfaceDB() {
        val dbPath = "/Users/mstave/Dropbox/pic.db"
        val ff = File(dbPath)
        org.junit.jupiter.api.Assumptions.assumeTrue {
            ff.exists()
        }
        val f = makePicInterface(dbPath)
        Assertions.assertEquals(f.javaClass, LightroomDB::class.java)
    }

    @Test
    fun testMeta() {
        val imgPath = LocalPicFilesTest.getTestPics().basePath.toString() + File.separator + "312.png"
//        val imgPath = "/Users/mstave/Dropbox/Photos/pics/Holly/HollyCat.JPG"
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