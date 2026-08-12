package com.wellnesswingman.domain.checkin

import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn

/**
 * Builds the prompt and response schema for check-in facet extraction.
 *
 * Kept apart from the service so the wording can be read, reviewed and tested on its own. Prompt
 * text is the actual behaviour of this feature; burying it inside a coroutine makes it invisible.
 */
object CheckInFacetPrompt {

    /**
     * JSON Schema for the expected response.
     *
     * `valence` and `origin` are closed enums so the model cannot invent a third category, while
     * `description` and `domain` stay free text to carry the nuance. Compare the older
     * `OtherAnalysisResult`, whose bare string tags produce a fresh vocabulary every call and
     * cannot be aggregated afterwards.
     */
    val RESPONSE_SCHEMA: String = """
        {
          "type": "object",
          "properties": {
            "mentionedFood": {
              "type": "array",
              "description": "Food or drink the user says they consumed. Empty if none is mentioned.",
              "items": {
                "type": "object",
                "properties": {
                  "name": { "type": "string" },
                  "portionSize": { "type": "string", "description": "In the user's own terms, e.g. 'two beers', 'a packet'." },
                  "nutrition": {
                    "type": "object",
                    "description": "Always provide this, with at least totalCalories. A rough estimate is the point of the feature.",
                    "properties": {
                      "totalCalories": { "type": "number" },
                      "protein": { "type": "number", "description": "grams" },
                      "carbohydrates": { "type": "number", "description": "grams" },
                      "fat": { "type": "number", "description": "grams" },
                      "fiber": { "type": "number", "description": "grams" },
                      "sugar": { "type": "number", "description": "grams" },
                      "sodium": { "type": "number", "description": "milligrams" }
                    },
                    "required": ["totalCalories"]
                  },
                  "confidence": { "type": "number", "description": "0.0 to 1.0" },
                  "possiblyAlreadyLogged": { "type": "boolean" },
                  "alreadyLoggedReason": { "type": "string" },
                  "eatenOnCheckInDate": {
                    "type": "boolean",
                    "description": "False when the user says they ate it on a different day, e.g. 'last night' in a morning check-in."
                  }
                },
                "required": ["name", "nutrition", "eatenOnCheckInDate"]
              }
            },
            "factors": {
              "type": "array",
              "description": "Things that helped or hurt. Empty if the user named none.",
              "items": {
                "type": "object",
                "properties": {
                  "description": { "type": "string" },
                  "valence": { "type": "string", "enum": ["Good", "Bad"] },
                  "origin": { "type": "string", "enum": ["Internal", "External"] },
                  "quote": { "type": "string", "description": "The user's own words this came from." },
                  "domain": { "type": "string", "description": "sleep, energy, mood, digestion, pain, stress, social, environment or other" },
                  "confidence": { "type": "number", "description": "0.0 to 1.0" }
                },
                "required": ["description", "valence", "origin"]
              }
            },
            "confidence": { "type": "number", "description": "0.0 to 1.0" },
            "warnings": { "type": "array", "items": { "type": "string" } }
          },
          "required": ["mentionedFood", "factors"]
        }
    """.trimIndent()

    /**
     * @param checkIn the answer being read.
     * @param trackedEntryLines one line per entry already logged that day. Supplied so the model
     *   can recognise food it is being told about for the second time; day totals merge tracked
     *   and mentioned food, so an unmarked duplicate is counted twice.
     */
    fun build(checkIn: DailyCheckIn, trackedEntryLines: List<String>): String {
        val question = when (checkIn.slot) {
            CheckInSlot.MORNING -> "How did you sleep? How do you feel?"
            CheckInSlot.EVENING -> "How did the day feel? Anything other than what you logged?"
        }

        val alreadyLogged = if (trackedEntryLines.isEmpty()) {
            "Nothing was logged on this day."
        } else {
            "Already logged on this day:\n<tracked_entries>\n" +
                trackedEntryLines.joinToString("\n") { "  - ${sanitize(it)}" } +
                "\n</tracked_entries>"
        }

        return """
            You are reading a short, subjective daily check-in from a health-tracking app. The
            user was asked: "$question"

            This check-in is about ${checkIn.checkInDate} (the ${checkIn.slot.toStorageString()
            .lowercase()} check-in). Anything the user places outside that day — "last night",
            "yesterday" — belongs to a different day, which matters for the food flag below.

            Their answer, verbatim:
            <check_in>
            ${sanitize(checkIn.responseText)}
            </check_in>

            $alreadyLogged

            Extract two things, and only what is actually there.

            1. mentionedFood — any food or drink the user says they consumed. ALWAYS include a
               nutrition object with at least totalCalories for every item, even when the portion
               is vague: estimating from a description is the entire point, and an item with no
               number attached contributes nothing to the user's day. Where they gave a weight or
               count, use it ("a slice, probably 20g" is enough to estimate from). Express
               uncertainty by lowering confidence, never by omitting the estimate. If an item
               plausibly refers to something in <tracked_entries>, set possiblyAlreadyLogged to
               true and say which in alreadyLoggedReason. Do not include food they mention
               wanting, avoiding, or planning to eat.

               Set eatenOnCheckInDate to false when the user places the food on a different day.
               A morning check-in invites exactly this — "I had two beers late last night and
               slept badly" describes yesterday's drinking, and those calories belong to
               yesterday, not to the day this check-in is about. Still report the item; the flag
               is what keeps it out of the wrong day's totals. When they give no timing, assume
               it belongs to the check-in's own day and set the flag true.

            2. factors — specific things that helped or hurt. For each, decide:
               - valence: Good if it helped or felt positive, Bad if it hurt or felt negative.
               - origin: Internal if it arose from the user's own body or mind (pain, illness,
                 worry, sore stomach, racing thoughts). External if it came from circumstance
                 (a pet, a noise, weather, another person, work, travel).
               Quote the words it came from.

            Rules:
            - Every confidence is a bare JSON number between 0 and 1, such as 0.4. Never a word
              like "medium", never a percentage, never a string.
            - Nutrition values are bare JSON numbers with no units: 300, not "300 kcal".
            - Report only what the user said or clearly implied. Do not infer a cause they did
              not give, and do not add advice, diagnosis or encouragement.
            - Do not rate the day, the user, or their health. No overall score of any kind. A
              factor is an observation about one specific thing, not a verdict.
            - A neutral or uneventful answer is a normal outcome: return empty arrays rather than
              manufacturing content.
            - Return only the JSON object described by the schema.
        """.trimIndent()
    }

    /**
     * Stops user text from closing a prompt delimiter, matching DailySummaryService's guard.
     * This is user-authored free text going into a prompt, which is exactly what it is for.
     */
    private fun sanitize(text: String): String = text.replace("</", "< /")
}
