package dev.gomoku.zenyth.data

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The backup rules (res/xml/data_extraction_rules.xml, backup_rules.xml) name
 * files one by one — Android's rules have no wildcards — so a weight renamed or
 * added under assets/engine would quietly go back into everyone's cloud backup.
 * This catches that, and keeps the RenjuNet-derived packs out of the cloud.
 */
class BackupRulesTest {

    private fun excludes(file: String, section: String?): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$file"))
        val root = if (section == null) doc.documentElement else doc.getElementsByTagName(section).item(0) as Element
        val nodes = root.getElementsByTagName("exclude")
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .filter { it.getAttribute("domain") == "file" }
            .map { it.getAttribute("path") }
            .toSet()
    }

    private val rules = listOf(
        "cloud-backup" to excludes("data_extraction_rules.xml", "cloud-backup"),
        "device-transfer" to excludes("data_extraction_rules.xml", "device-transfer"),
        "full-backup-content" to excludes("backup_rules.xml", null),
    )

    @Test
    fun `every file the engine extracts stays out of every backup`() {
        val extracted = File("src/main/assets/engine").list().orEmpty().map { "engine/$it" } +
            listOf("engine/.installed", "engine/.dotprod-sigill")
        assertThat(extracted).contains("engine/config.toml")
        for ((name, paths) in rules) {
            for (path in extracted) assertWithMessage("$name: $path").that(paths).contains(path)
            assertWithMessage("$name: debug log").that(paths).contains("zenyth-debug.log")
        }
    }

    @Test
    fun `what the user made is kept`() {
        for ((name, paths) in rules) {
            for (kept in listOf("engine/rapfi.db", "engine", "joseki.json", "datastore", "shared_prefs")) {
                assertWithMessage("$name must keep $kept").that(paths).doesNotContain(kept)
            }
        }
    }

    /** RenjuNet's licence: nothing derived from it in any online system. */
    @Test
    fun `renjunet packs never reach the cloud`() {
        assertThat(rules.toMap().getValue("cloud-backup")).contains("packs")
        assertThat(rules.toMap().getValue("full-backup-content")).contains("packs")
    }
}
