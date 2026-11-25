package com.wander.android.data.sources.ytmusic

import com.wander.android.data.model.SourceType
import com.wander.android.data.model.UnifiedPlaylist
import kotlinx.serialization.json.JsonObject

/**
 * A playlist as its own page names it: the title and cover in its header.
 *
 * For a playlist the account does not keep — one found by searching — which is not in the library
 * listing the playlist screen otherwise reads its header from. Both header renderers in
 * circulation are swept for, newest first, as for an album (see `albumHeader`). Never editable:
 * a playlist the account keeps is answered from the library listing before this is asked.
 */
internal fun JsonObject.playlistHeader(playlistId: String): UnifiedPlaylist? =
    (renderers("musicResponsiveHeaderRenderer") + renderers("musicDetailHeaderRenderer"))
        .firstNotNullOfOrNull { header ->
            val title = header["title"].runText()?.takeIf { it.isNotBlank() } ?: return@firstNotNullOfOrNull null
            UnifiedPlaylist(
                id = playlistId,
                source = SourceType.YTMUSIC,
                name = title,
                coverArtUrl = header.path("thumbnail", "musicThumbnailRenderer", "thumbnail").bestThumbnail()
                    ?: header.path("thumbnail", "croppedSquareThumbnailRenderer", "thumbnail").bestThumbnail(),
                isEditable = false
            )
        }
