package com.zacco.rytm.wear.hrv

import android.content.Context

fun createIbiSource(context: Context): IbiSource = SamsungIbiSource(context.applicationContext)
