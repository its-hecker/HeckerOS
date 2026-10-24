package com.android.systemui.lifecycle
abstract class HydratedActivatable { abstract suspend fun onActivated(): Nothing }
