package app.cellaroo

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.cellaroo.core.Source
import app.cellaroo.core.WineType
import app.cellaroo.data.MissingAction
import app.cellaroo.data.WineDraft
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the view model the way the screens do, against the real database and image store. No network. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ViewModelFlowTest {
    private val app: CellarooApp get() = ApplicationProvider.getApplicationContext()

    private fun await(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for: $what")
        }
    }

    private fun item(id: Int, producer: String, name: String, vintage: String, qty: Int = 1, price: String = "", box: List<Int> = listOf(52, 200, 440, 410)) =
        ScanItem(id, 0, listOf(box), producer, name, "", "", vintage, WineType.RED, "", null, 750, null, true, 0.9, qty, price)

    @Test fun scanThenShelfCheckThenUndo() {
        val vm = MainViewModel(app)
        await("default shelf") { vm.cellar.value.locations.isNotEmpty() }
        val shelf = vm.cellar.value.locations.first().id
        val photo = assertNotNull(app.container.images.importFile(File("../samples/costco-cart-6.png")))
        assertTrue(photo.width <= 2048 && photo.height <= 2048)
        val photos = listOf(ScanPhoto(photo.name, photo.width, photo.height))

        // 1. Save a scan with two labels, one of them twice.
        vm.scan.value = ScanSession(
            ScanKind.ADD, null, photos,
            listOf(item(1, "Viu Manent", "La Capilla Single Vineyard Cabernet Sauvignon", "2022", qty = 2, price = "15.99"), item(2, "Luis R", "Rioja Crianza", "2021")),
        )
        vm.saveScan(shelf)
        await("scan saved") { vm.cellar.value.rows.size == 2 && vm.scan.value == null }
        fun capilla() = vm.cellar.value.rows.first { it.wine.producer == "Viu Manent" }
        fun luis() = vm.cellar.value.rows.first { it.wine.producer == "Luis R" }
        assertEquals(2, capilla().qty)
        assertEquals(31.98, capilla().costBasis, 0.001)
        assertTrue(capilla().bottles.all { it.locationId == shelf && it.addedVia == "SCAN" })
        assertNotNull(app.container.images.file(capilla().thumb), "the bottle crop should exist on disk")

        // 2. A later scan reads the same label slightly differently: it joins the existing wine.
        vm.scan.value = ScanSession(ScanKind.ADD, null, photos, listOf(item(1, "VIU MANENT", "La Capilla Cabernet Sauvignon", "2022")))
        vm.saveScan(shelf)
        await("second scan saved") { vm.scan.value == null && capilla().qty == 3 }
        assertEquals(2, vm.cellar.value.rows.size)

        // 3. Shelf check: the photo shows the three La Capilla and a wine the cellar has never seen. Luis R is gone.
        vm.startScan(ScanKind.RECONCILE, shelf)
        vm.scan.value = vm.scan.value!!.copy(
            photos = photos,
            items = listOf(
                item(1, "Viu Manent", "La Capilla Single Vineyard Cabernet Sauvignon", "2022"),
                item(2, "Viu Manent", "La Capilla Single Vineyard Cabernet Sauvignon", "2022"),
                item(3, "Viu Manent", "La Capilla Single Vineyard Cabernet Sauvignon", "2022"),
                item(4, "Bodegas Muga", "Reserva", "2020", price = "24"),
            ),
        )
        val plan = assertNotNull(vm.reconcilePlan(vm.scan.value!!))
        assertEquals(3, plan.matchedCount)
        assertEquals(listOf(luis().bottles.single().id), plan.missing.map { it.bottleId })
        assertEquals(listOf(4), plan.unknown.map { it.index })
        assertTrue(plan.isClean)
        assertEquals(MissingAction.DRUNK, vm.defaultMissingAction(plan))
        vm.applyReconcile()
        await("shelf check applied") { vm.scan.value == null && vm.cellar.value.rows.size == 3 }
        assertEquals(0, luis().qty)
        assertEquals(1, luis().logs.size)
        assertEquals(3, capilla().qty)
        val muga = vm.cellar.value.rows.first { it.wine.producer == "Bodegas Muga" }
        assertEquals(shelf, muga.bottles.single().locationId)
        assertEquals(24.0, muga.bottles.single().purchasePriceUsd)

        // 4. Undo puts everything back.
        await("change recorded") { vm.changeSets.value.firstOrNull()?.kind == "RECONCILE" }
        vm.undo(vm.changeSets.value.first().id)
        await("undo applied") { vm.cellar.value.rows.size == 2 }
        assertEquals(1, luis().qty)
        assertTrue(luis().logs.isEmpty())
    }

    @Test fun pasteMergesWithoutAnApiKey() {
        val vm = MainViewModel(app)
        await("default shelf") { vm.cellar.value.locations.isNotEmpty() }
        var id = 0L
        vm.addManualWine(WineDraft("Ciacci Piccolomini d'Aragona", "Brunello di Montalcino Pianrosso", vintage = 2019), 2, 68.0, vm.cellar.value.locations.first().id) { id = it }
        await("wine added") { id != 0L && vm.cellar.value.row(id)?.qty == 2 }

        vm.parsePaste(
            """
            2019 Ciacci Piccolomini d'Aragona Brunello di Montalcino Pianrosso
            CT93.4 (212 notes)   WA 95   Vinous 96
            Drinking Windows: Drink between 2026 and 2040 (based on 31 user opinions)
            Community average value: ${'$'}72.14
            """.trimIndent()
        )
        await("paste parsed") { vm.paste.value != null }
        val p = vm.paste.value!!
        assertFalse(p.usedAi)
        assertEquals(2040, p.window.until)
        assertEquals(72.14, p.priceUsd)
        assertEquals(setOf("CT", "WA", "VINOUS"), p.scores.map { it.source }.toSet())

        vm.applyPaste(id, setOf("window", "price", "scores"), null) {}
        await("paste merged") { vm.cellar.value.row(id)?.wine?.drinkUntilYear == 2040 && vm.cellar.value.row(id)?.scores?.size == 3 }
        val w = vm.cellar.value.row(id)!!.wine
        assertEquals(Source.PASTE, w.windowSource)
        assertEquals(72.14, w.marketPriceUsd)
        assertEquals(Source.PASTE, w.marketPriceSource)
        assertEquals(2026, w.drinkFromYear)
    }
}
