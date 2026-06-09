package com.livetvpro.app.data.repository

import com.livetvpro.app.data.local.dao.PlaylistDao
import com.livetvpro.app.data.local.entity.PlaylistEntity
import com.livetvpro.app.data.models.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    fun getAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { entities ->
            entities.map { it.toPlaylist() }
        }
    }

    suspend fun getPlaylistById(playlistId: String): Playlist? {
        return playlistDao.getPlaylistById(playlistId)?.toPlaylist()
    }

    suspend fun addPlaylist(title: String, url: String = "", isFile: Boolean = false, filePath: String = ""): Playlist {
        val existing = playlistDao.getAllPlaylists().first()
        val nextPosition = if (existing.isEmpty()) 0 else existing.maxOf { it.position } + 1
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            title = title,
            url = url,
            isFile = isFile,
            filePath = filePath,
            position = nextPosition,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(playlist.toEntity())
        return playlist
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        val updatedPlaylist = playlist.copy(updatedAt = System.currentTimeMillis())
        playlistDao.updatePlaylist(updatedPlaylist.toEntity())
    }

    suspend fun updatePositions(playlists: List<Playlist>) {
        val entities = playlists.mapIndexed { index, playlist ->
            playlist.toEntity().copy(position = index)
        }
        playlistDao.updatePositions(entities)
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist.toEntity())
    }

    suspend fun deletePlaylistById(playlistId: String) {
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun deleteAllPlaylists() {
        playlistDao.deleteAllPlaylists()
    }

    private fun PlaylistEntity.toPlaylist() = Playlist(
        id = id,
        title = title,
        url = url,
        isFile = isFile,
        filePath = filePath,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Playlist.toEntity() = PlaylistEntity(
        id = id,
        title = title,
        url = url,
        isFile = isFile,
        filePath = filePath,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
