package com.v2nox.app

import android.service.quicksettings.Tile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TileModelTest {
    @Test
    fun disconnectedShowsSelectedServerOrPrompt() {
        assertEquals(
            TileModel(Tile.STATE_INACTIVE, "Сервер", R.drawable.ic_tile_off),
            tileModelFor(VpnState.DISCONNECTED, "Сервер"),
        )
        assertEquals("Выберите сервер", tileModelFor(VpnState.DISCONNECTED, null).subtitle)
    }

    @Test
    fun connectingIsActiveWithProgressSubtitle() {
        for (name in listOf("Сервер", null)) {
            assertEquals(
                TileModel(Tile.STATE_ACTIVE, "Подключение…", R.drawable.ic_tile_on),
                tileModelFor(VpnState.CONNECTING, name),
            )
        }
    }

    @Test
    fun connectedIsActiveWithServerName() {
        assertEquals(
            TileModel(Tile.STATE_ACTIVE, "Сервер", R.drawable.ic_tile_on),
            tileModelFor(VpnState.CONNECTED, "Сервер"),
        )
        assertNull(tileModelFor(VpnState.CONNECTED, null).subtitle)
    }

    @Test
    fun stoppingIsInactiveWithProgressSubtitle() {
        for (name in listOf("Сервер", null)) {
            assertEquals(
                TileModel(Tile.STATE_INACTIVE, "Отключение…", R.drawable.ic_tile_off),
                tileModelFor(VpnState.STOPPING, name),
            )
        }
    }

    @Test
    fun tileIsAlwaysClickableEvenWithoutServer() {
        for (state in VpnState.entries) {
            for (name in listOf("Сервер", null)) {
                assertNotEquals(Tile.STATE_UNAVAILABLE, tileModelFor(state, name).state)
            }
        }
    }
}
