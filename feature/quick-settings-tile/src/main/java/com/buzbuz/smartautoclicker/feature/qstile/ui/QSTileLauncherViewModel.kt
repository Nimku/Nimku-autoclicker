/*
 * Copyright (C) 2024 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.feature.qstile.ui

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.buzbuz.smartautoclicker.core.base.data.AppComponentsProvider
import com.buzbuz.smartautoclicker.core.base.di.Dispatcher
import com.buzbuz.smartautoclicker.core.base.di.HiltCoroutineDispatchers.IO
import com.buzbuz.smartautoclicker.core.domain.IRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.DumbRepository
import com.buzbuz.smartautoclicker.core.common.permissions.PermissionsController
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionAccessibilityService
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionOverlay
import com.buzbuz.smartautoclicker.core.common.permissions.model.PermissionPostNotification
import com.buzbuz.smartautoclicker.core.settings.domain.SettingsRepository
import com.buzbuz.smartautoclicker.feature.qstile.domain.QSTileRepository

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
class QSTileLauncherViewModel @Inject constructor(
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    private val qsTileRepository: QSTileRepository,
    private val permissionController: PermissionsController,
    private val smartRepository: IRepository,
    private val dumbRepository: DumbRepository,
    private val settingsRepository: SettingsRepository,
    private val appComponentsProvider: AppComponentsProvider,
) : ViewModel() {


    fun startPermissionFlowIfNeeded(activity: AppCompatActivity, onAllGranted: () -> Unit, onMandatoryDenied: () -> Unit) {
        permissionController.startPermissionsUiFlow(
            activity = activity,
            permissions = listOf(
                PermissionOverlay(),
                PermissionAccessibilityService(
                    componentName = appComponentsProvider.klickrServiceComponentName,
                    isServiceRunning = { qsTileRepository.isAccessibilityServiceStarted() },
                ),
                PermissionPostNotification(optional = true),
            ),
            onAllGranted = onAllGranted,
            onMandatoryDenied = onMandatoryDenied,
        )
    }

    fun startSmartScenario(resultCode: Int, data: Intent, scenarioId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val scenario = smartRepository.getScenario(scenarioId) ?: return@launch
            qsTileRepository.startSmartScenario(resultCode, data, scenario)
        }
    }

    fun startDumbScenario(scenarioId: Long) {
        viewModelScope.launch(ioDispatcher) {
            val scenario = dumbRepository.getDumbScenario(scenarioId) ?: return@launch
            qsTileRepository.startDumbScenario(scenario)
        }
    }

    suspend fun isRemoteControlEnabled(): Boolean =
        settingsRepository.isRemoteControlEnabledFlow.first()

    fun stopScenarios() {
        qsTileRepository.stopScenarios()
    }

    /**
     * Find a scenario for a remote control request.
     * @return the database id of the scenario and true if it is a smart scenario, or null if not found.
     */
    suspend fun findScenario(name: String?, id: Long, isSmart: Boolean?): Pair<Long, Boolean>? =
        withContext(ioDispatcher) {
            when {
                !name.isNullOrEmpty() -> {
                    val smartMatch = if (isSmart != false) {
                        smartRepository.scenarios.first().firstOrNull { it.name.equals(name, ignoreCase = true) }
                    } else null
                    if (smartMatch != null) return@withContext smartMatch.id.databaseId to true

                    val dumbMatch = if (isSmart != true) {
                        dumbRepository.dumbScenarios.first().firstOrNull { it.name.equals(name, ignoreCase = true) }
                    } else null
                    dumbMatch?.let { it.id.databaseId to false }
                }

                id > 0 -> when (isSmart) {
                    true -> smartRepository.getScenario(id)?.let { id to true }
                    false -> dumbRepository.getDumbScenario(id)?.let { id to false }
                    null -> smartRepository.getScenario(id)?.let { id to true }
                        ?: dumbRepository.getDumbScenario(id)?.let { id to false }
                }

                else -> {
                    val (lastId, lastIsSmart) = qsTileRepository.getLastScenarioDetails()
                    if (lastId != null && lastIsSmart != null) lastId to lastIsSmart else null
                }
            }
        }

    fun isEntireScreenCaptureForced(): Boolean =
        settingsRepository.isEntireScreenCaptureForced()
}