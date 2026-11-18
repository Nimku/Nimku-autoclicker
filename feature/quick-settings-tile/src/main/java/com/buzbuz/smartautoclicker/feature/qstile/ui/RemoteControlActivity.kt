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
package com.buzbuz.smartautoclicker.feature.qstile.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

import com.buzbuz.smartautoclicker.feature.qstile.R

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Entry point for starting and stopping scenarios from outside the app (ADB, Tasker, other automation apps).
 *
 * Only works when the "Remote control" setting is enabled.
 *
 * Start a scenario by name (a smart scenario wins over a simple one with the same name):
 *   adb shell am start -a com.nimku.autoclicker.START -n <package>/com.buzbuz.smartautoclicker.feature.qstile.ui.RemoteControlActivity --es name "My scenario"
 * Start by database id: --el id 3 --ez smart true
 * Start the last scenario used with the quick settings tile: no extras.
 * Stop the running scenario: -a com.nimku.autoclicker.STOP
 */
@AndroidEntryPoint
class RemoteControlActivity : AppCompatActivity() {

    private val viewModel: QSTileLauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val receivedIntent = intent
        lifecycleScope.launch {
            if (!viewModel.isRemoteControlEnabled()) {
                Log.w(TAG, "Remote control is disabled in the settings, ignoring request")
                Toast.makeText(this@RemoteControlActivity, R.string.toast_remote_control_disabled, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }

            when (receivedIntent?.action) {
                ACTION_STOP -> {
                    Log.i(TAG, "Remote stop request")
                    viewModel.stopScenarios()
                }
                else -> handleStart(receivedIntent)
            }
            finish()
        }
    }

    private suspend fun handleStart(startIntent: Intent?) {
        val name = startIntent?.getStringExtra(EXTRA_NAME)?.trim()
        val id = startIntent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val smart: Boolean? =
            if (startIntent?.hasExtra(EXTRA_SMART) == true) startIntent.getBooleanExtra(EXTRA_SMART, false)
            else null

        val target = viewModel.findScenario(name = name, id = id, isSmart = smart)
        if (target == null) {
            Log.e(TAG, "Remote start request: no scenario found for name=$name id=$id smart=$smart")
            Toast.makeText(this, getString(R.string.toast_remote_control_not_found, name ?: id.toString()), Toast.LENGTH_LONG).show()
            return
        }

        Log.i(TAG, "Remote start request for scenario ${target.first} (smart=${target.second})")
        startActivity(QSTileLauncherActivity.getStartIntent(this, target.first, target.second))
    }

    companion object {
        const val ACTION_START = "com.nimku.autoclicker.START"
        const val ACTION_STOP = "com.nimku.autoclicker.STOP"
        const val EXTRA_NAME = "name"
        const val EXTRA_ID = "id"
        const val EXTRA_SMART = "smart"
    }
}

private const val TAG = "RemoteControlActivity"
