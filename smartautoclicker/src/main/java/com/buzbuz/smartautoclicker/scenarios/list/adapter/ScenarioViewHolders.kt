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
package com.buzbuz.smartautoclicker.scenarios.list.adapter

import android.graphics.Bitmap
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.buzbuz.smartautoclicker.R
import com.buzbuz.smartautoclicker.scenarios.list.model.ScenarioListUiState
import com.buzbuz.smartautoclicker.core.base.extensions.setLeftCompoundDrawable
import com.buzbuz.smartautoclicker.core.domain.model.condition.ScreenCondition
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.databinding.ItemDumbScenarioBinding
import com.buzbuz.smartautoclicker.databinding.ItemEmptyScenarioBinding
import com.buzbuz.smartautoclicker.databinding.ItemSmartScenarioBinding
import kotlinx.coroutines.Job


class EmptyScenarioHolder(
    private val viewBinding: ItemEmptyScenarioBinding,
    private val startScenarioListener: ((ScenarioListUiState.Item.ScenarioItem.Empty) -> Unit),
    private val deleteScenarioListener: ((ScenarioListUiState.Item.ScenarioItem.Empty) -> Unit),
): RecyclerView.ViewHolder(viewBinding.root) {

    fun onBind(scenarioItem: ScenarioListUiState.Item.ScenarioItem.Empty) = viewBinding.apply {
        scenarioName.text = scenarioItem.displayName
        scenarioName.setLeftCompoundDrawable(
            if (scenarioItem.scenario is DumbScenario) R.drawable.ic_dumb
            else R.drawable.ic_smart
        )

        buttonStart.setOnClickListener { startScenarioListener(scenarioItem) }
        buttonDelete.setOnClickListener { deleteScenarioListener(scenarioItem) }
    }
}

/** ViewHolder for the [ScenarioAdapter]: simple row with the name, the action count, copy and delete. */
class DumbScenarioViewHolder(
    private val viewBinding: ItemDumbScenarioBinding,
    private val startScenarioListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    @Suppress("unused") private val expandCollapseListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    private val exportClickListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    private val copyClickedListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    private val deleteScenarioListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
) : RecyclerView.ViewHolder(viewBinding.root) {

    fun onBind(scenarioItem: ScenarioListUiState.Item.ScenarioItem.Valid.Dumb) = viewBinding.apply {
        scenarioName.text = scenarioItem.displayName

        val actionCount = scenarioItem.clickCount + scenarioItem.gestureCount + scenarioItem.pauseCount
        scenarioSubtitle.text = root.context.resources.getQuantityString(
            R.plurals.item_scenario_action_count, actionCount, actionCount,
        )

        bindButtons(
            showExportCheckbox = scenarioItem.showExportCheckbox,
            onRowClicked = {
                if (scenarioItem.showExportCheckbox) exportClickListener(scenarioItem)
                else startScenarioListener(scenarioItem)
            },
            setExportChecked = { buttonExport.isChecked = scenarioItem.checkedForExport },
            root = root, buttonCopy = buttonCopy, buttonDelete = buttonDelete, buttonExport = buttonExport,
        )

        buttonCopy.setOnClickListener { copyClickedListener(scenarioItem) }
        buttonDelete.setOnClickListener { deleteScenarioListener(scenarioItem) }
        buttonExport.setOnClickListener { exportClickListener(scenarioItem) }
    }
}

/** ViewHolder for the [ScenarioAdapter]: simple row with the name, the event count, copy and delete. */
class SmartScenarioViewHolder(
    private val viewBinding: ItemSmartScenarioBinding,
    @Suppress("unused") bitmapProvider: (ScreenCondition.Image, onBitmapLoaded: (Bitmap?) -> Unit) -> Job?,
    private val startScenarioListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    @Suppress("unused") private val expandCollapseListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    private val exportClickListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    private val copyClickedListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
    private val deleteScenarioListener: ((ScenarioListUiState.Item.ScenarioItem.Valid) -> Unit),
) : RecyclerView.ViewHolder(viewBinding.root) {

    fun onBind(scenarioItem: ScenarioListUiState.Item.ScenarioItem.Valid.Smart) = viewBinding.apply {
        scenarioName.text = scenarioItem.displayName

        val eventCount = scenarioItem.eventsItems.size + scenarioItem.triggerEventCount
        scenarioSubtitle.text = root.context.resources.getQuantityString(
            R.plurals.item_scenario_event_count, eventCount, eventCount,
        )

        bindButtons(
            showExportCheckbox = scenarioItem.showExportCheckbox,
            onRowClicked = {
                if (scenarioItem.showExportCheckbox) exportClickListener(scenarioItem)
                else startScenarioListener(scenarioItem)
            },
            setExportChecked = { buttonExport.isChecked = scenarioItem.checkedForExport },
            root = root, buttonCopy = buttonCopy, buttonDelete = buttonDelete, buttonExport = buttonExport,
        )

        buttonCopy.setOnClickListener { copyClickedListener(scenarioItem) }
        buttonDelete.setOnClickListener { deleteScenarioListener(scenarioItem) }
        buttonExport.setOnClickListener { exportClickListener(scenarioItem) }
    }
}

/** In export mode, the copy/delete buttons are replaced by the export checkbox. */
private fun bindButtons(
    showExportCheckbox: Boolean,
    onRowClicked: () -> Unit,
    setExportChecked: () -> Unit,
    root: View,
    buttonCopy: View,
    buttonDelete: View,
    buttonExport: View,
) {
    if (showExportCheckbox) {
        buttonCopy.visibility = View.GONE
        buttonDelete.visibility = View.GONE
        buttonExport.visibility = View.VISIBLE
        setExportChecked()
    } else {
        buttonCopy.visibility = View.VISIBLE
        buttonDelete.visibility = View.VISIBLE
        buttonExport.visibility = View.GONE
    }
    root.setOnClickListener { onRowClicked() }
}
