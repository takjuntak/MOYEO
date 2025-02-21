package com.neungi.data.mapper

import com.neungi.data.entity.AlbumEntity
import com.neungi.data.util.CommonUtils.formatDateTime
import com.neungi.domain.model.PhotoAlbum

object AlbumsMapper {

    operator fun invoke(albumEntities: List<AlbumEntity>): List<PhotoAlbum> {
        val newPhotoAlbums = mutableListOf<PhotoAlbum>()

        albumEntities.forEach { albumEntity ->
            newPhotoAlbums.add(
                PhotoAlbum(
                    id = albumEntity.id.toString(),
                    tripId = albumEntity.tripId.toString(),
                    title = albumEntity.tripTitle,
                    imageUrl = albumEntity.repImage ?: "",
                    startDate = formatDateTime(albumEntity.startDate),
                    endDate = formatDateTime(albumEntity.endDate)
                )
            )
        }

        return newPhotoAlbums.toList()
    }
}