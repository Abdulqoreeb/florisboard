/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.app.settings.clipboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.core.content.ContextCompat
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ClipboardScreen() = FlorisScreen {
    title = stringRes(R.string.settings__clipboard__title)
    previewFieldVisible = true

    content {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val clipboardManager by context.clipboardManager()
        val screenshotDetectionEnabled by prefs.clipboard.screenshotDetectionEnabled.collectAsState()

        val mediaPermission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (!granted) {
                // User said no — flip the switch back off so the UI reflects
                // reality instead of showing "on" with nothing running.
                scope.launch {
                    prefs.clipboard.screenshotDetectionEnabled.set(false)
                }
            }
            clipboardManager.maybeEnableScreenshotDetection()
        }

        // Whenever the preference flips to true, make sure we actually have
        // permission — request it if not — then (re)register the observer.
        // Whenever it flips to false, this just tears the observer down.
        LaunchedEffect(screenshotDetectionEnabled) {
            if (screenshotDetectionEnabled) {
                val hasPermission = ContextCompat.checkSelfPermission(context, mediaPermission) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    permissionLauncher.launch(mediaPermission)
                } else {
                    clipboardManager.maybeEnableScreenshotDetection()
                }
            } else {
                clipboardManager.maybeEnableScreenshotDetection()
            }
        }

        SwitchPreference(
            prefs.clipboard.useInternalClipboard,
            title = stringRes(R.string.pref__clipboard__use_internal_clipboard__label),
            summary = stringRes(R.string.pref__clipboard__use_internal_clipboard__summary),
        )
        ListPreference(
            prefs.clipboard.syncToFloris,
            title = stringRes(R.string.pref__clipboard__sync_from_system_clipboard__label),
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
            enabledIf = { prefs.clipboard.useInternalClipboard isEqualTo true },
        )
        ListPreference(
            prefs.clipboard.syncToSystem,
            title = stringRes(R.string.pref__clipboard__sync_to_system_clipboard__label),
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
            enabledIf = { prefs.clipboard.useInternalClipboard isEqualTo true },
        )

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_clipboard_suggestion__label)) {
            SwitchPreference(
                prefs.clipboard.suggestionEnabled,
                title = stringRes(R.string.pref__clipboard__suggestion_enabled__label),
                summary = stringRes(R.string.pref__clipboard__suggestion_enabled__summary),
            )
            DialogSliderPreference(
                prefs.clipboard.suggestionTimeout,
                title = stringRes(R.string.pref__clipboard__suggestion_timeout__label),
                valueLabel = { stringRes(R.string.pref__clipboard__suggestion_timeout__summary, "v" to it) },
                min = 30,
                max = 300,
                stepIncrement = 5,
                enabledIf = { prefs.clipboard.suggestionEnabled isEqualTo true },
            )
        }

        PreferenceGroup(title = stringRes(R.string.pref__clipboard__group_clipboard_history__label)) {
            SwitchPreference(
                prefs.clipboard.historyEnabled,
                title = stringRes(R.string.pref__clipboard__enable_clipboard_history__label),
                summary = stringRes(R.string.pref__clipboard__enable_clipboard_history__summary),
            )
            DialogSliderPreference(
                primaryPref = prefs.clipboard.historyNumGridColumnsPortrait,
                secondaryPref = prefs.clipboard.historyNumGridColumnsLandscape,
                title = stringRes(R.string.pref__clipboard__num_history_grid_columns__label),
                primaryLabel = stringRes(R.string.screen_orientation__portrait),
                secondaryLabel = stringRes(R.string.screen_orientation__landscape),
                valueLabel = { numGridColumns ->
                    if (numGridColumns == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) {
                        stringRes(R.string.general__auto)
                    } else {
                        numGridColumns.toString()
                    }
                },
                min = 0,
                max = 10,
                stepIncrement = 1,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.historyAutoCleanOldEnabled,
                title = stringRes(R.string.pref__clipboard__clean_up_old__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
            DialogSliderPreference(
                prefs.clipboard.historyAutoCleanOldAfter,
                title = stringRes(R.string.pref__clipboard__clean_up_after__label),
                valueLabel = { pluralsRes(R.plurals.unit__minutes__written, it, "v" to it) },
                min = 0,
                max = 120,
                stepIncrement = 5,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true && prefs.clipboard.historyAutoCleanOldEnabled isEqualTo true },
            )
            SwitchPreference(
                prefs.clipboard.historyAutoCleanSensitiveEnabled,
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
                visibleIf = { AndroidVersion.ATLEAST_API33_T },
            )
            DialogSliderPreference(
                prefs.clipboard.historyAutoCleanSensitiveAfter,
                title = stringRes(R.string.pref__clipboard__auto_clean_sensitive_after__label),
                valueLabel = { pluralsRes(R.plurals.unit__seconds__written, it, "v" to it) },
                min = 0,
                max = 300,
                stepIncrement = 10,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true && prefs.clipboard.historyAutoCleanSensitiveEnabled isEqualTo true },
                visibleIf = { AndroidVersion.ATLEAST_API33_T },
            )
            SwitchPreference(
                prefs.clipboard.historySizeLimitEnabled,
                title = stringRes(R.string.pref__clipboard__limit_history_size__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
            DialogSliderPreference(
                prefs.clipboard.historySizeLimit,
                title = stringRes(R.string.pref__clipboard__max_history_size__label),
                valueLabel = { pluralsRes(R.plurals.unit__items__written, it, "v" to it) },
                min = 5,
                max = 100,
                stepIncrement = 5,
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true && prefs.clipboard.historySizeLimitEnabled isEqualTo true },
            )

            SwitchPreference(
                prefs.clipboard.historyHideOnPaste,
                title = stringRes(R.string.pref__clipboard__history_hide_on_paste__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true }
            )
            SwitchPreference(
                prefs.clipboard.historyHideOnNextTextField,
                title = stringRes(R.string.pref__clipboard__history_hide_on_next_text_field__label),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true }
            )

            SwitchPreference(
                prefs.clipboard.clearPrimaryClipAffectsHistoryIfUnpinned,
                title = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__label),
                summary = stringRes(R.string.pref__clipboard__clear_primary_clip_affects_history_if_unpinned__summary),
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )

            // --- Screenshot auto-detection -------------------------------
            // The switch below just flips the stored preference like any
            // other SwitchPreference. Actually starting/stopping the
            // MediaStore observer, and asking for the READ_MEDIA_IMAGES /
            // READ_EXTERNAL_STORAGE permission when the user turns this on,
            // is handled by the LaunchedEffect + permission launcher above
            // (see top of this Composable), which reacts to the preference
            // value changing rather than being called from here directly.
            SwitchPreference(
                prefs.clipboard.screenshotDetectionEnabled,
                title = "Detect screenshots automatically",
                summary = "Add new screenshots to clipboard history without copying them manually. Requires photo access permission.",
                enabledIf = { prefs.clipboard.historyEnabled isEqualTo true },
            )
        }
    }
}
