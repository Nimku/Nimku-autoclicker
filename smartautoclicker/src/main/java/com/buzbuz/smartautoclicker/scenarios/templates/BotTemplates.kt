/*
 * Copyright (C) 2026 Nimku
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.scenarios.templates

import android.graphics.Rect

import com.buzbuz.smartautoclicker.code.smart.detectionmodels.text.domain.OCRAlphabet
import com.buzbuz.smartautoclicker.core.base.identifier.DATABASE_ID_INSERTION
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.Click
import com.buzbuz.smartautoclicker.core.domain.model.action.Pause
import com.buzbuz.smartautoclicker.core.domain.model.action.position.CoordinatesValue
import com.buzbuz.smartautoclicker.core.domain.model.action.position.Position
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.domain.model.condition.TriggerCondition
import com.buzbuz.smartautoclicker.core.domain.model.event.Event
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.domain.model.event.TriggerEvent
import com.buzbuz.smartautoclicker.core.domain.model.scenario.Scenario

/**
 * Ready-made Smart scenarios for game automation.
 *
 * They rely on text detection (the button labels of the game), so they work on any screen size without capturing
 * pictures. Fixed buttons without text (like the map search button) are tapped at a position relative to the screen.
 */
enum class BotTemplate(val scenarioName: String) {
    /** Clash of Kings: search a monster, attack it, send the march, repeat. */
    COK_KILL_MONSTERS("CoK - Kill monsters"),
    /** Clash of Kings: search a resource tile, gather it, send the march, repeat. */
    COK_GATHER("CoK - Gather resources"),
}

/** Creates the scenario of a template in the database. Returns the new scenario database id. */
suspend fun IRepository.createBotTemplate(
    template: BotTemplate,
    screenWidth: Int,
    screenHeight: Int,
    detectionQuality: Int,
): Long {
    val scenarioDbId = addScenario(
        Scenario(
            id = Identifier(databaseId = DATABASE_ID_INSERTION, tempId = 0L),
            name = template.scenarioName,
            detectionQuality = detectionQuality,
            randomize = true,
        )
    )
    val scenario = getScenario(scenarioDbId) ?: return scenarioDbId

    val builder = TemplateBuilder(scenario.id, screenWidth, screenHeight)
    val events = when (template) {
        BotTemplate.COK_KILL_MONSTERS -> builder.cokKillMonsters()
        BotTemplate.COK_GATHER -> builder.cokGather()
    }

    updateScenario(scenario, events, emptyList())
    return scenarioDbId
}

private class TemplateBuilder(
    private val scenarioId: Identifier,
    private val width: Int,
    private val height: Int,
) {
    private var lastTempId = 0L
    private fun newId(): Identifier = Identifier(databaseId = DATABASE_ID_INSERTION, tempId = ++lastTempId)

    /** Rectangle in screen fractions (0 to 1). */
    private fun area(left: Float, top: Float, right: Float, bottom: Float) =
        Rect((left * width).toInt(), (top * height).toInt(), (right * width).toInt(), (bottom * height).toInt())

    private var eventPriority = 0

    /** Event: when [text] is visible in [area], tap it, then run [after] actions. */
    fun tapText(
        name: String,
        text: String,
        area: Rect,
        pauseAfterMs: Long,
        after: (eventId: Identifier) -> List<Action> = { emptyList() },
    ): ScreenEvent {
        val eventId = newId()
        val condition = ScreenCondition.Text(
            id = newId(),
            eventId = eventId,
            name = "\"$text\" visible",
            threshold = TEXT_THRESHOLD,
            shouldBeDetected = true,
            priority = 0,
            text = text,
            detectionArea = area,
            alphabet = OCRAlphabet.LATIN,
        )
        val actions = mutableListOf<Action>(
            Click(
                id = newId(),
                eventId = eventId,
                name = "Tap $text",
                priority = 0,
                pressDuration = PRESS_DURATION_MS,
                position = Position.Condition(condition.id),
            ),
        )
        actions += after(eventId)
        actions += pause(eventId, pauseAfterMs)
        actions.forEachIndexed { index, action -> action.priority = index }

        return ScreenEvent(
            id = eventId,
            scenarioId = scenarioId,
            name = name,
            conditionOperator = OR,
            actions = actions,
            conditions = listOf(condition),
            enabledOnStart = true,
            priority = eventPriority++,
            keepDetecting = false,
            cooldownMs = 0,
        )
    }

    /** Event: every [periodMs], tap at a position in screen fractions. */
    fun tapEvery(name: String, periodMs: Long, x: Float, y: Float, pauseAfterMs: Long): TriggerEvent {
        val eventId = newId()
        return TriggerEvent(
            id = eventId,
            scenarioId = scenarioId,
            name = name,
            conditionOperator = OR,
            conditions = listOf(
                TriggerCondition.OnTimerReached(
                    id = newId(),
                    eventId = eventId,
                    name = "Every ${periodMs / 1000}s",
                    durationMs = periodMs,
                    restartWhenReached = true,
                )
            ),
            actions = listOf(
                tapAt(eventId, name, x, y).apply { priority = 0 },
                pause(eventId, pauseAfterMs).apply { priority = 1 },
            ),
            enabledOnStart = true,
        )
    }

    fun tapAt(eventId: Identifier, name: String, x: Float, y: Float) = Click(
        id = newId(),
        eventId = eventId,
        name = name,
        priority = 0,
        pressDuration = PRESS_DURATION_MS,
        position = Position.Coordinates(
            x = CoordinatesValue.Literal((x * width).toInt()),
            y = CoordinatesValue.Literal((y * height).toInt()),
        ),
    )

    fun pause(eventId: Identifier, durationMs: Long) = Pause(
        id = newId(),
        eventId = eventId,
        name = "Wait",
        priority = 0,
        pauseDuration = durationMs,
    )

    // ------------------------------------------- Clash of Kings -------------------------------------------
    // Positions measured on the 9:16 game screen. The search panel remembers the last type and level used,
    // so the user selects them once by hand.

    /** March screen: the "March" button at the bottom right. */
    private fun cokMarch() = tapText(
        name = "Send the march",
        text = "March",
        area = area(0.45f, 0.85f, 1f, 1f),
        pauseAfterMs = 3_000,
    )

    /** Search panel: the "Search" button, then tap the target found in the middle of the map. */
    private fun cokSearch() = tapText(
        name = "Search and select the target",
        text = "Search",
        area = area(0f, 0.86f, 1f, 1f),
        pauseAfterMs = 1_500,
    ) { eventId ->
        listOf(pause(eventId, 2_500), tapAt(eventId, "Tap the target", 0.5f, 0.5f))
    }

    /** World map: open the search panel regularly (round button with a magnifier, bottom right). */
    private fun cokOpenSearch() = tapEvery(
        name = "Open the map search",
        periodMs = 40_000,
        x = 0.914f,
        y = 0.72f,
        pauseAfterMs = 2_000,
    )

    fun cokKillMonsters(): List<Event> = listOf(
        cokMarch(),
        tapText(
            name = "Attack the monster",
            text = "Attack",
            area = area(0f, 0.62f, 1f, 0.9f),
            pauseAfterMs = 2_000,
        ),
        cokSearch(),
        cokOpenSearch(),
    )

    fun cokGather(): List<Event> = listOf(
        cokMarch(),
        tapText(
            name = "Gather the resource",
            text = "Gather",
            area = area(0f, 0.45f, 1f, 0.95f),
            pauseAfterMs = 2_000,
        ),
        cokSearch(),
        cokOpenSearch(),
    )
}

private const val TEXT_THRESHOLD = 10
private const val PRESS_DURATION_MS = 60L
