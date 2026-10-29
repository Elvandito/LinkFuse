package dev.linkfuse.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.linkfuse.app.R
import dev.linkfuse.app.model.MergeStatus

/** Quick Settings toggle: tap to merge, tap again to pause. */
class MergeTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        when (MergeController.ui.value.status) {
            MergeStatus.MERGING -> MergeController.pause(this)
            MergeStatus.PAUSED -> MergeController.resume(this)
            else -> MergeController.start(this)
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val status = MergeController.ui.value.status
        tile.state = if (status == MergeStatus.MERGING) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(
            when (status) {
                MergeStatus.MERGING -> R.string.tile_merge
                MergeStatus.PAUSED -> R.string.tile_paused
                else -> R.string.tile_off
            }
        )
        tile.updateTile()
    }
}
