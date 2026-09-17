/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import org.florisboard.lib.kotlin.tryOrNull

/**
 * Watches MediaStore for newly-created images that live in a screenshots
 * folder and, when one appears, synthesizes a [ClipboardItem] for it and
 * hands it to [onScreenshotDetected] — the same way a real clipboard event
 * would.
 *
 * This exists because Android does NOT fire a clipboard change event when a
 * screenshot is taken; screenshots only ever reach MediaStore. Apps like
 * Gboard get their "recent screenshot" suggestion by watching MediaStore
 * directly, which is what this class replicates.
 *
 * Requires READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE (below) to
 * already be GRANTED before [register] is called — this class does not
 * request permissions itself.
 */
class ScreenshotObserver(
    private val context: Context,
    private val onScreenshotDetected: (ClipboardItem) -> Unit,
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private var registered = false
    private var lastHandledId: Long = -1L
    private var lastHandledAtMs: Long = 0L

    // Two onChange() calls for the same row (insert, then metadata update)
    // typically land within a few hundred ms of each other. Anything wider
    // apart is treated as a genuinely new event even if the id repeats
    // (unlikely, but MediaStore ids can theoretically be reused after a
    // delete on some OEM ROMs).
    private val dedupWindowMs = 3000L

    // Common relative-path fragments used by stock Android and most OEM
    // skins (MIUI, OneUI, ColorOS, etc.) for screenshots. Matched
    // case-insensitively against MediaStore's RELATIVE_PATH / DATA column.
    private val screenshotPathHints = listOf(
        "screenshot",   // covers "Screenshots/" and "Pictures/Screenshots/"
        "screenshots",
    )

    fun register() {
        if (registered) return
        tryOrNull {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true, // notifyForDescendants — needed to catch inserts into sub-folders
                this,
            )
            registered = true
        }
    }

    fun unregister() {
        if (!registered) return
        tryOrNull {
            context.contentResolver.unregisterContentObserver(this)
        }
        registered = false
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        if (uri == null) return
        // Only care about row-level inserts (content://media/external/images/media/<id>),
        // not the bare table-level URI some OEMs also notify on.
        val id = tryOrNull { ContentUris.parseId(uri) } ?: return

        val now = android.os.SystemClock.elapsedRealtime()
        if (id == lastHandledId && (now - lastHandledAtMs) < dedupWindowMs) {
            // Same row fired again (e.g. the metadata-finalized update that
            // follows the initial insert) — skip it, we already added this
            // screenshot to history on the first notification.
            return
        }
        lastHandledId = id
        lastHandledAtMs = now

        checkAndHandle(uri, id)
    }

    private fun checkAndHandle(uri: Uri, id: Long) {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DATE_ADDED)
        } else {
            arrayOf(MediaStore.Images.Media.DATA, MediaStore.Images.Media.DATE_ADDED)
        }

        tryOrNull {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val pathColumn = cursor.getColumnIndex(projection[0])
                val path = if (pathColumn >= 0) cursor.getString(pathColumn) else null

                val isScreenshot = path != null && screenshotPathHints.any {
                    path.contains(it, ignoreCase = true)
                }
                if (!isScreenshot) return@use

                // Build a ClipData pointing at the MediaStore uri, then reuse
                // Floris's existing ClipboardItem.fromClipData — same path
                // real clipboard copies go through, so downstream code
                // (history insert, thumbnailing, paste) needs no changes.
                val clipData = ClipData.newUri(context.contentResolver, "Screenshot", uri)
                val item = ClipboardItem.fromClipData(context, clipData, cloneUri = true)
                onScreenshotDetected(item)
            }
        }
    }
}
