package com.neungi.domain.usecase

import com.neungi.domain.model.ApiResult
import com.neungi.domain.model.Photo
import com.neungi.domain.repository.AlbumsRepository
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject

class GetPhotosUseCase @Inject constructor(
    private val albumsRepository: AlbumsRepository
) {

    suspend fun getPhotos(albumId: String): ApiResult<List<Photo>> {
        return albumsRepository.getAlbumPhotos(albumId)
    }

    suspend fun submitPhoto(albumId: String, photos: List<MultipartBody.Part>, body: RequestBody): Flow<ApiResult<List<Photo>>> {
        return albumsRepository.postPhoto(albumId, photos, body)
    }

    suspend fun deletePhoto(albumId: String, photoId: String): ApiResult<Boolean> {
        return albumsRepository.deletePhoto(albumId, photoId)
    }
}