package kgui.view

import com.drew.imaging.ImageMetadataReader
import com.drew.metadata.exif.ExifThumbnailDirectory
import javafx.scene.image.Image
import tornadofx.*
import java.io.File
import java.io.FileInputStream

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
                val thumbnailDirectory = i2.getFirstDirectoryOfType(
                        ExifThumbnailDirectory::class.java)
                val n = thumbnailDirectory.getString(ExifThumbnailDirectory.TAG_THUMBNAIL_OFFSET)
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