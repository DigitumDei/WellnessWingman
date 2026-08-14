package com.wellnesswingman.domain.analysis

/**
 * Prevents user-authored text from closing the prompt's XML-like data blocks.
 */
internal fun sanitizeForPrompt(text: String): String = text.replace("</", "< /")
