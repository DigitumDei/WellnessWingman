package com.wellnesswingman.data.googleexport

import com.wellnesswingman.domain.report.HealthReportBlock
import com.wellnesswingman.domain.report.HealthReportDocument
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleDocsBatchBuilderTest {

    private val builder = GoogleDocsBatchBuilder()

    @Test
    fun `starts at Google Docs writable body index and offsets format ranges`() {
        val batch = builder.build(
            HealthReportDocument(listOf(HealthReportBlock.Heading(1, "Report")))
        )

        assertEquals("Report\n", batch.text)
        val insert = batch.requests.first()["insertText"]!!.jsonObject
        assertEquals(1, insert["location"]!!.jsonObject["index"]!!.jsonPrimitive.int)

        val styleRange = batch.requests[1]["updateParagraphStyle"]!!.jsonObject["range"]!!.jsonObject
        assertEquals(1, styleRange["startIndex"]!!.jsonPrimitive.int)
        assertEquals(8, styleRange["endIndex"]!!.jsonPrimitive.int)
    }

    @Test
    fun `uses Google Docs bullet request over contiguous bullet paragraphs`() {
        val batch = builder.build(
            HealthReportDocument(listOf(HealthReportBlock.BulletList(listOf("One", "Two"))))
        )

        assertEquals(2, batch.paragraphCount)
        val bullets = batch.requests.last()["createParagraphBullets"]!!.jsonObject
        val range = bullets["range"]!!.jsonObject
        assertEquals(1, range["startIndex"]!!.jsonPrimitive.int)
        assertEquals(9, range["endIndex"]!!.jsonPrimitive.int)
        assertTrue(bullets["bulletPreset"]!!.jsonPrimitive.content.isNotBlank())
    }
}
