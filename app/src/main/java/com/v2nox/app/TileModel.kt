package com.v2nox.app

import android.service.quicksettings.Tile

data class TileModel(
    val state: Int,
    val subtitle: String?,
    val iconRes: Int,
)

fun tileModelFor(state: VpnState, serverName: String?): TileModel = when (state) {
    VpnState.DISCONNECTED -> TileModel(
        Tile.STATE_INACTIVE, serverName ?: "Выберите сервер", R.drawable.ic_tile_off,
    )
    VpnState.CONNECTING -> TileModel(
        Tile.STATE_ACTIVE, "Подключение…", R.drawable.ic_tile_on,
    )
    VpnState.CONNECTED -> TileModel(
        Tile.STATE_ACTIVE, serverName, R.drawable.ic_tile_on,
    )
    VpnState.STOPPING -> TileModel(
        Tile.STATE_INACTIVE, "Отключение…", R.drawable.ic_tile_off,
    )
}
