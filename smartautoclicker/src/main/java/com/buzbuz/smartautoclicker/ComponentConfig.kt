/*
 * Copyright (C) 2025 Kevin Buzeau
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
package com.buzbuz.smartautoclicker

import android.content.ComponentName

/** Names of the app components that other modules need to reference. */
object ComponentConfig {

    /** Installed package name (com.nimku.autoclicker, or com.nimku.autoclicker.debug for debug builds). */
    const val ORIGINAL_APP_ID: String = BuildConfig.APPLICATION_ID

    val smartAutoClickerService: ComponentName =
        ComponentName(BuildConfig.APPLICATION_ID, "com.buzbuz.smartautoclicker.SmartAutoClickerService")

    val scenarioActivity: ComponentName =
        ComponentName(BuildConfig.APPLICATION_ID, "com.buzbuz.smartautoclicker.scenarios.ScenarioActivity")
}
