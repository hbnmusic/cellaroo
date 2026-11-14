package app.cellaroo

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cellaroo.core.BottleStatus
import app.cellaroo.core.ParsedScore
import app.cellaroo.data.AddItem
import app.cellaroo.data.AppJson
import app.cellaroo.data.BackupData
import app.cellaroo.data.BottleDraft
import app.cellaroo.data.CellarDb
import app.cellaroo.data.CellarRepository
import app.cellaroo.data.MissingAction
import app.cellaroo.data.ReconcileOps
import app.cellaroo.data.WineDraft
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the real Room database (in memory) through the repository. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RepositoryTest {
    private lateinit var db: CellarDb
    private lateinit var repo: CellarRepository

    private fun newDb(): CellarDb =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CellarDb::class.java).allowMainThreadQueries().build()

    private val capilla = WineDraft("Viu Manent", "La Capilla Cabernet Sauvignon", vintage = 2022)
    private val riscal = WineDraft("Marqués de Riscal", "Reserva", vintage = 2021)

    @Before fun setUp() {
        db = newDb()
        repo = CellarRepository(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun state() = repo.state.first { it.loaded }

    @Test fun addingTheSameLabelTwiceKeepsOneWine() = runBlocking<Unit> {
        val loc = repo.ensureDefaultLocation()
        assertEquals(loc, repo.ensureDefaultLocation())
        repo.addBottles(listOf(AddItem(null, capilla, listOf(BottleDraft(loc, 15.99), BottleDraft(loc, 15.99)))), "ADD", "first")
        val second = repo.addBottles(listOf(AddItem(null, capilla.copy(producer = "VIU MANENT"), listOf(BottleDraft(loc)))), "ADD", "second")
        var s = state()
        assertEquals(1, s.rows.size)
        assertEquals(3, s.rows.single().qty)
        assertEquals(31.98, s.rows.single().costBasis, 0.001)
        assertEquals(2, s.rows.single().pricedBottles)
        assertEquals("2022 Viu Manent La Capilla Cabernet Sauvignon", s.rows.single().title)
        assertTrue(repo.undo(second))
        s = state()
        assertEquals(2, s.rows.single().qty)
        assertFalse(repo.undo(second))
    }

    @Test fun removeLogsAndUndoRestores() = runBlocking<Unit> {
        val loc = repo.ensureDefaultLocation()
        repo.addBottles(listOf(AddItem(null, capilla, listOf(BottleDraft(loc), BottleDraft(loc)))), "ADD", "add")
        val bottle = state().rows.single().bottles.first()
        val cs = repo.removeBottle(bottle.id, BottleStatus.CONSUMED, 4f, "Good with steak", "ribeye")
        assertNotNull(cs)
        var row = state().rows.single()
        assertEquals(1, row.qty)
        assertEquals(1, row.logs.size)
        assertEquals(80.0, row.myRating100)
        assertEquals("ribeye", row.logs.single().pairedWith)
        assertTrue(repo.undo(cs))
        row = state().rows.single()
        assertEquals(2, row.qty)
        assertTrue(row.logs.isEmpty())
        assertNull(repo.removeBottle(99999))
    }

    @Test fun shelfCheckAppliesAndUndoesAsOneChange() = runBlocking<Unit> {
        val a = repo.ensureDefaultLocation()
        val b = repo.addLocation("Counter")
        repo.addBottles(
            listOf(
                AddItem(null, capilla, listOf(BottleDraft(a), BottleDraft(a))),
                AddItem(null, riscal, listOf(BottleDraft(b))),
            ), "ADD", "seed",
        )
        val before = state()
        val capillaBottles = before.rows.first { it.wine.producer == "Viu Manent" }.bottles
        val riscalBottle = before.rows.first { it.wine.wineName == "Reserva" }.bottles.single()
        val luis = WineDraft("Luis R", "Rioja Crianza", vintage = 2021)
        val cs = repo.applyReconcile(
            a,
            ReconcileOps(
                missing = listOf(capillaBottles[0].id to MissingAction.DRUNK, capillaBottles[1].id to MissingAction.STILL_HERE),
                moveHere = listOf(riscalBottle.id),
                additions = listOf(AddItem(null, luis, listOf(BottleDraft(a, 12.5, addedVia = "RECONCILE")))),
            ),
        )
        var s = state()
        assertEquals(3, s.rows.size)
        assertEquals(1, s.rows.first { it.wine.producer == "Viu Manent" }.qty)
        assertEquals(a, s.rows.first { it.wine.wineName == "Reserva" }.bottles.single().locationId)
        assertEquals(1, s.rows.first { it.wine.producer == "Luis R" }.qty)
        assertNotNull(s.locations.first { it.id == a }.lastReconciledAt)
        assertTrue(repo.undo(cs))
        s = state()
        assertEquals(2, s.rows.size)
        assertEquals(2, s.rows.first { it.wine.producer == "Viu Manent" }.qty)
        assertEquals(b, s.rows.first { it.wine.wineName == "Reserva" }.bottles.single().locationId)
        assertTrue(s.rows.all { it.logs.isEmpty() })
    }

    @Test fun scoresReplacePerSourceAndDeleteCascades() = runBlocking<Unit> {
        val loc = repo.ensureDefaultLocation()
        repo.addBottles(listOf(AddItem(null, riscal, listOf(BottleDraft(loc)))), "ADD", "seed")
        val id = state().rows.single().wine.id
        repo.addScores(id, listOf(ParsedScore("WA", "95", 95.0), ParsedScore("OTHER", "90 pts", 90.0)), "PASTE")
        repo.addScores(id, listOf(ParsedScore("WA", "96+", 96.0), ParsedScore("OTHER", "91 pts", 91.0)), "PASTE")
        val row = state().rows.single()
        assertEquals(listOf(96f), row.scores.filter { it.source == "WA" }.map { it.score100 })
        assertEquals(2, row.scores.count { it.source == "OTHER" })
        assertEquals("Wine Advocate", row.scores.first { it.source == "WA" }.sourceLabel)
        repo.deleteLocation(loc)
        assertNull(state().rows.single().bottles.single().locationId)
        repo.deleteWine(id)
        assertTrue(repo.dao.bottles().isEmpty())
        assertTrue(repo.dao.scores().isEmpty())
    }

    @Test fun backupRoundTrip() = runBlocking<Unit> {
        val loc = repo.ensureDefaultLocation()
        repo.addBottles(listOf(AddItem(null, capilla, listOf(BottleDraft(loc, 15.99))), AddItem(null, riscal, listOf(BottleDraft(loc)))), "ADD", "seed")
        val first = state().rows.first().bottles.single()
        repo.removeBottle(first.id, BottleStatus.GIFTED)
        val json = AppJson.encodeToString(BackupData.serializer(), repo.snapshot("test"))
        val data = AppJson.decodeFromString(BackupData.serializer(), json)

        val db2 = newDb()
        val repo2 = CellarRepository(db2)
        repo2.addBottles(listOf(AddItem(null, WineDraft("Old", "Stuff"), listOf(BottleDraft(repo2.ensureDefaultLocation())))), "ADD", "junk")
        repo2.restore(data)
        val s = repo2.state.first { it.loaded }
        assertEquals(2, s.rows.size)
        assertEquals(1, s.rows.sumOf { it.qty })
        assertEquals(1, s.rows.sumOf { it.logs.size })
        assertEquals(BottleStatus.GIFTED, s.rows.flatMap { it.allBottles }.first { it.id == first.id }.status)
        assertEquals("Default", s.locations.single().name)
        db2.close()
    }
}
