package com.wellnesswingman.domain.report

/**
 * A Google-independent, ordered list of document blocks.
 *
 * The builder produces only these primitives; the future Google Docs client
 * maps them to create/batchUpdate requests. Keeping the model free of any
 * Google type means the builder is pure, deterministic, and fully testable
 * without network or auth.
 */
sealed interface HealthReportBlock {

    /** A titled section or sub-section. */
    data class Heading(val level: Int, val text: String) : HealthReportBlock

    /** A single narrative paragraph. */
    data class Paragraph(val text: String) : HealthReportBlock

    /** A bulleted list. */
    data class BulletList(val items: List<String>) : HealthReportBlock

    /** A compact table with a header row. */
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : HealthReportBlock

    /** A horizontal rule separating major sections. */
    data object Divider : HealthReportBlock
}

/**
 * The completed, ordered document ready for rendering.
 */
data class HealthReportDocument(
    val blocks: List<HealthReportBlock>
)
