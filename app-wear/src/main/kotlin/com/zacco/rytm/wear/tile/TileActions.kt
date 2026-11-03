package com.zacco.rytm.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.modifiers.clickable
import com.zacco.rytm.wear.MainActivity

internal object TileActions {

    /** Otwiera aplikację na wskazanym ekranie (patrz [MainActivity.EXTRA_SCREEN]). */
    fun open(context: Context, screen: String): Clickable {
        val activity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(MainActivity::class.java.name)
            .addKeyToExtraMapping(
                MainActivity.EXTRA_SCREEN,
                ActionBuilders.AndroidStringExtra.Builder().setValue(screen).build(),
            )
            .build()
        return clickable(
            action = ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build(),
            id = "open:$screen",
        )
    }
}
