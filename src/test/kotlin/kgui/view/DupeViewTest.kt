package kgui.view

import kgui.app.Styles
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testfx.api.FxToolkit
import tornadofx.*

internal class DupeViewTest {
//    val primaryStage: Stage = FxToolkit.registerPrimaryStage()
    @BeforeEach
    fun setUp() {
//        stage = FxToolkit.registerPrimaryStage()
    }

    @AfterEach
    fun tearDown() {
    }

    @Test
    fun getRoot() {
        // TODO: break out heavy lifting from the controller to a non-javaFX class for easier testing
        val app = App(DupeView::class, Styles::class)
        FxToolkit.registerPrimaryStage();
        FxToolkit.setupApplication { app }
        val dv = find<DupeView>()
        val ds = dv.dupeC.dupeStrings
        val dc = find<DupeController>()
        while (!dc.doneSearching.get()) {
            Thread.sleep(1000)
        }
//        Thread.sleep(2000)
        println(ds)
        println(dc.dupeStrings)
        Assertions.assertNotNull(ds)
        Assertions.assertNotNull(dc)
        Assertions.assertTrue(dc.dupeStrings.size > 10)
        FxToolkit.cleanupApplication(app)
    }
}
