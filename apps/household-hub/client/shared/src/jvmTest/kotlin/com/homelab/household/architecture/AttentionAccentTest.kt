package com.homelab.household.architecture

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Terracotta is the attention accent, and it lives in tertiary (design notes §3). Secondary is the
 * quiet partner of primary, kept for calm accents such as a proposed change. The places below mean
 * "attention", "time running out" or "busy", so reading secondary there would quietly swap them to
 * the calm accent.
 */
class AttentionAccentTest {
    private val clientRootDir =
        File(System.getProperty("user.dir")).let { dir ->
            if (dir.name == "shared") dir.parentFile else dir
        }

    private val appDir = File(clientRootDir, "composeApp/src/commonMain/kotlin/com/homelab/household/app")

    private val attentionSites =
        listOf(
            "components/CodeCard.kt",
            "components/ToolRunningChip.kt",
            "screens/changepin/ChangePinContent.kt",
            "screens/calendarconnect/CalendarConnectContent.kt",
        )

    @Test
    fun attention_accents_read_tertiary_not_secondary() {
        val secondary = Regex("""\bcolors\.(on)?[sS]econdary(Container)?\b""")

        val violations =
            attentionSites.flatMap { path ->
                val file = File(appDir, path)
                assertTrue(file.exists(), "Attention site must exist at: ${file.absolutePath}")
                file.readLines().mapIndexedNotNull { index, line ->
                    secondary.find(line)?.let {
                        "${file.toRelativeString(clientRootDir)}:${index + 1} reads '${it.value}' " +
                            "— an attention accent takes tertiary"
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Attention accents on secondary (${violations.size}):\n" + violations.joinToString("\n"),
        )
    }
}
