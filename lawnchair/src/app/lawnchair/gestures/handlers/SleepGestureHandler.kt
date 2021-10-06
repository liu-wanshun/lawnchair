/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.annotation.TargetApi
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.GestureHandler
import app.lawnchair.lawnchairApp
import app.lawnchair.root.RootHelperManager
import app.lawnchair.ui.AlertBottomSheetContent
import app.lawnchair.ui.preferences.components.BottomSheetState
import app.lawnchair.views.showBottomSheet
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlinx.coroutines.launch

class SleepGestureHandler(launcher: LawnchairLauncher) : GestureHandler() {

    override suspend fun onTrigger() {
        methods.first { it.isSupported() }.sleep()
    }

    @OptIn(ExperimentalMaterialApi::class)
    private val methods = listOf(
        SleepMethodRoot(launcher),
        SleepMethodPieAccessibility(launcher),
        SleepMethodDeviceAdmin(launcher)
    )

    abstract class SleepMethod(protected val launcher: LawnchairLauncher) {
        abstract suspend fun isSupported(): Boolean
        abstract suspend fun sleep()
    }
}

class SleepMethodRoot(launcher: LawnchairLauncher) : SleepGestureHandler.SleepMethod(launcher) {
    private val rootHelperManager = RootHelperManager.INSTANCE.get(launcher)

    override suspend fun isSupported() = rootHelperManager.isAvailable()

    override suspend fun sleep() {
        rootHelperManager.getService().goToSleep()
    }
}

@ExperimentalMaterialApi
class SleepMethodPieAccessibility(launcher: LawnchairLauncher) : SleepGestureHandler.SleepMethod(launcher) {
    override suspend fun isSupported() = Utilities.ATLEAST_P

    @TargetApi(Build.VERSION_CODES.P)
    override suspend fun sleep() {
        val app = launcher.lawnchairApp
        if (!app.isAccessibilityServiceBound()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launcher.showBottomSheet { state ->
                ServiceWarningDialog(
                    title = R.string.dt2s_a11y_hint_title,
                    description = R.string.dt2s_a11y_hint,
                    settingsIntent = intent,
                    sheetState = state
                )
            }
            return
        }
        launcher.lawnchairApp.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
    }
}

@ExperimentalMaterialApi
class SleepMethodDeviceAdmin(launcher: LawnchairLauncher) : SleepGestureHandler.SleepMethod(launcher) {
    override suspend fun isSupported() = true

    override suspend fun sleep() {
        val devicePolicyManager = launcher.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!devicePolicyManager.isAdminActive(ComponentName(launcher, SleepDeviceAdmin::class.java))) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(launcher, SleepDeviceAdmin::class.java)
            )
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, launcher.getString(R.string.dt2s_admin_hint))
            launcher.showBottomSheet { state ->
                ServiceWarningDialog(
                    title = R.string.dt2s_admin_hint_title,
                    description = R.string.dt2s_admin_hint,
                    settingsIntent = intent,
                    sheetState = state
                )
            }
            return
        }
        devicePolicyManager.lockNow()
    }

    class SleepDeviceAdmin : DeviceAdminReceiver() {

        override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
            return context.getString(R.string.dt2s_admin_warning)
        }
    }
}

@ExperimentalMaterialApi
@Composable
fun ServiceWarningDialog(
    title: Int,
    description: Int,
    settingsIntent: Intent,
    sheetState: BottomSheetState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertBottomSheetContent(
        title = { Text(text = stringResource(id = title)) },
        text = { Text(text = stringResource(id = description)) },
        buttons = {
            OutlinedButton(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    scope.launch { sheetState.hide() }
                }
            ) {
                Text(text = stringResource(id = android.R.string.cancel))
            }
            Spacer(modifier = Modifier.requiredWidth(16.dp))
            Button(
                shape = MaterialTheme.shapes.small,
                onClick = {
                    context.startActivity(settingsIntent)
                    scope.launch { sheetState.hide() }
                }
            ) {
                Text(text = stringResource(id = R.string.dt2s_warning_open_settings))
            }
        }
    )
}