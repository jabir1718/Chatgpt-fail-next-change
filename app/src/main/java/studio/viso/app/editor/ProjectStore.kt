package com.creatorstudio.app.editor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Versioned, atomic project persistence.
 * Content URIs are stored as strings; callers must retain persistable read permission
 * or copy source media into app-managed storage before relying on long-term access.
 */
class ProjectStore(context: Context) {
    private val directory = File(context.filesDir, "creator-projects").apply { mkdirs() }

    @Synchronized
    @Throws(IOException::class)
    fun save(project: EditorProject) {
        val target = projectFile(project.id)
        val temporary = File(directory, "${project.id}.json.tmp")
        temporary.writeText(encode(project).toString(), Charsets.UTF_8)
        val backup = File(directory, "${project.id}.json.bak")
        if (backup.exists() && !backup.delete()) {
            temporary.delete()
            throw IOException("Could not clear previous project backup")
        }
        val hadPrevious = target.exists()
        if (hadPrevious && !target.renameTo(backup)) {
            temporary.delete()
            throw IOException("Could not preserve previous project file")
        }
        if (!temporary.renameTo(target)) {
            if (hadPrevious) backup.renameTo(target)
            temporary.delete()
            throw IOException("Could not commit project file")
        }
        if (backup.exists()) backup.delete()
    }

    @Synchronized
    @Throws(IOException::class)
    fun load(projectId: String): EditorProject {
        val target = projectFile(projectId)
        val backup = File(directory, "$projectId.json.bak")
        var targetFailure: Exception? = null

        if (target.isFile) {
            try {
                return decode(JSONObject(target.readText(Charsets.UTF_8)))
            } catch (error: Exception) {
                targetFailure = error
            }
        }

        if (backup.isFile) {
            try {
                val recovered = decode(JSONObject(backup.readText(Charsets.UTF_8)))
                backup.copyTo(target, overwrite = true)
                return recovered
            } catch (backupFailure: Exception) {
                targetFailure?.addSuppressed(backupFailure)
                throw IOException("Project and recovery backup are invalid: $projectId", targetFailure ?: backupFailure)
            }
        }

        if (targetFailure != null) {
            throw IOException("Project data is invalid and no recovery backup exists: $projectId", targetFailure)
        }
        throw IOException("Project not found: $projectId")
    }

    @Synchronized
    fun listProjects(): List<ProjectSummary> = directory.listFiles()
        ?.filter { it.isFile && it.extension == "json" }
        ?.mapNotNull { file ->
            runCatching {
                val json = JSONObject(file.readText(Charsets.UTF_8))
                ProjectSummary(
                    id = json.getString("id"),
                    name = json.optString("name", "Untitled Project"),
                    updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L)
                )
            }.getOrNull()
        }
        ?.sortedByDescending { it.updatedAtEpochMs }
        ?: emptyList()

    @Synchronized
    fun delete(projectId: String): Boolean {
        val file = projectFile(projectId)
        val backup = File(directory, "$projectId.json.bak")
        val temporary = File(directory, "$projectId.json.tmp")
        val targetDeleted = !file.exists() || file.delete()
        val backupDeleted = !backup.exists() || backup.delete()
        val temporaryDeleted = !temporary.exists() || temporary.delete()
        return targetDeleted && backupDeleted && temporaryDeleted
    }

    private fun projectFile(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid project ID" }
        return File(directory, "$id.json")
    }

    private fun encode(project: EditorProject): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("id", project.id)
        put("name", project.name)
        put("width", project.width)
        put("height", project.height)
        put("frameRate", project.frameRate)
        put("createdAtEpochMs", project.createdAtEpochMs)
        put("updatedAtEpochMs", project.updatedAtEpochMs)
        put("assets", JSONArray().apply { project.assets.forEach { put(assetJson(it)) } })
        put("tracks", JSONArray().apply { project.tracks.forEach { put(trackJson(it)) } })
        put("visualizer", JSONObject().apply {
            put("backgroundAssetId", project.visualizer.backgroundAssetId ?: JSONObject.NULL)
            put("foregroundAssetId", project.visualizer.foregroundAssetId ?: JSONObject.NULL)
            put("mode", project.visualizer.mode)
            put("foregroundScale", project.visualizer.foregroundScale)
            put("beatSensitivity", project.visualizer.beatSensitivity)
        })
    }

    private fun assetJson(asset: MediaAsset) = JSONObject().apply {
        put("id", asset.id); put("uri", asset.uri); put("displayName", asset.displayName)
        put("kind", asset.kind.name); put("durationMs", asset.durationMs ?: JSONObject.NULL)
        put("mimeType", asset.mimeType ?: JSONObject.NULL); put("importedAtEpochMs", asset.importedAtEpochMs)
    }

    private fun trackJson(track: TimelineTrack) = JSONObject().apply {
        put("id", track.id); put("name", track.name); put("kind", track.kind.name)
        put("muted", track.muted); put("locked", track.locked)
        put("gain", track.gain); put("solo", track.solo)
        put("clips", JSONArray().apply { track.clips.forEach { put(clipJson(it)) } })
    }

    private fun clipJson(clip: TimelineClip) = JSONObject().apply {
        put("id", clip.id); put("mediaId", clip.mediaId); put("kind", clip.kind.name)
        put("timelineStartMs", clip.timelineStartMs); put("durationMs", clip.durationMs)
        put("sourceStartMs", clip.sourceStartMs); put("sourceEndMs", clip.sourceEndMs)
        put("transform", JSONObject().apply {
            put("x", clip.transform.x); put("y", clip.transform.y)
            put("scaleX", clip.transform.scaleX); put("scaleY", clip.transform.scaleY)
            put("rotationDegrees", clip.transform.rotationDegrees); put("opacity", clip.transform.opacity)
        })
        put("audio", JSONObject().apply {
            put("volume", clip.audio.volume); put("muted", clip.audio.muted)
            put("pitchSemitones", clip.audio.pitchSemitones); put("playbackRate", clip.audio.playbackRate)
            put("fadeInMs", clip.audio.fadeInMs); put("fadeOutMs", clip.audio.fadeOutMs)
        })
        put("captions", JSONArray().apply { clip.captions.forEach { caption ->
            put(JSONObject().apply {
                put("id", caption.id); put("text", caption.text); put("startMs", caption.startMs)
                put("endMs", caption.endMs); put("languageTag", caption.languageTag ?: JSONObject.NULL)
            })
        } })
    }

    private fun decode(json: JSONObject): EditorProject {
        val version = json.optInt("schemaVersion", 1)
        require(version in 1..SCHEMA_VERSION) { "Unsupported project schema version: $version" }
        val assets = json.getJSONArray("assets").mapObjects { item ->
            MediaAsset(
                id = item.getString("id"), uri = item.getString("uri"),
                displayName = item.getString("displayName"), kind = MediaKind.valueOf(item.getString("kind")),
                durationMs = item.longOrNull("durationMs"), mimeType = item.stringOrNull("mimeType"),
                importedAtEpochMs = item.optLong("importedAtEpochMs", 0L)
            )
        }
        val tracks = json.getJSONArray("tracks").mapObjects { item ->
            TimelineTrack(
                id = item.getString("id"), name = item.getString("name"),
                kind = TrackKind.valueOf(item.getString("kind")), muted = item.optBoolean("muted"),
                locked = item.optBoolean("locked"),
                clips = item.getJSONArray("clips").mapObjects { clip -> decodeClip(clip) },
                gain = item.optDouble("gain", 1.0).toFloat().coerceIn(0f, 1f),
                solo = item.optBoolean("solo", false)
            )
        }
        val visualizerJson = json.optJSONObject("visualizer") ?: JSONObject()
        return EditorProject(
            id = json.getString("id"), name = json.optString("name", "Untitled Project"),
            width = json.optInt("width", 1080), height = json.optInt("height", 1920),
            frameRate = json.optInt("frameRate", 30),
            createdAtEpochMs = json.optLong("createdAtEpochMs", 0L),
            updatedAtEpochMs = json.optLong("updatedAtEpochMs", 0L),
            assets = assets, tracks = tracks,
            visualizer = VisualizerSettings(
                backgroundAssetId = visualizerJson.stringOrNull("backgroundAssetId"),
                foregroundAssetId = visualizerJson.stringOrNull("foregroundAssetId"),
                mode = visualizerJson.optString("mode", "Spectrum"),
                foregroundScale = visualizerJson.optDouble("foregroundScale", 1.0).toFloat(),
                beatSensitivity = visualizerJson.optDouble("beatSensitivity", 1.08).toFloat()
            )
        )
    }

    private fun decodeClip(item: JSONObject): TimelineClip {
        val transform = item.optJSONObject("transform") ?: JSONObject()
        val audio = item.optJSONObject("audio") ?: JSONObject()
        val captions = item.optJSONArray("captions") ?: JSONArray()
        return TimelineClip(
            id = item.getString("id"), mediaId = item.getString("mediaId"),
            kind = MediaKind.valueOf(item.getString("kind")),
            timelineStartMs = item.getLong("timelineStartMs"), durationMs = item.getLong("durationMs"),
            sourceStartMs = item.optLong("sourceStartMs", 0L), sourceEndMs = item.getLong("sourceEndMs"),
            transform = Transform(
                x = transform.optDouble("x", 0.0).toFloat(), y = transform.optDouble("y", 0.0).toFloat(),
                scaleX = transform.optDouble("scaleX", 1.0).toFloat(), scaleY = transform.optDouble("scaleY", 1.0).toFloat(),
                rotationDegrees = transform.optDouble("rotationDegrees", 0.0).toFloat(),
                opacity = transform.optDouble("opacity", 1.0).toFloat()
            ),
            audio = ClipAudioSettings(
                volume = audio.optDouble("volume", 1.0).toFloat().coerceIn(0f, 1f), muted = audio.optBoolean("muted"),
                pitchSemitones = audio.optDouble("pitchSemitones", 0.0).toFloat(),
                playbackRate = audio.optDouble("playbackRate", 1.0).toFloat(),
                fadeInMs = audio.optLong("fadeInMs"), fadeOutMs = audio.optLong("fadeOutMs")
            ),
            captions = captions.mapObjects { caption ->
                CaptionSegment(
                    id = caption.getString("id"), text = caption.getString("text"),
                    startMs = caption.getLong("startMs"), endMs = caption.getLong("endMs"),
                    languageTag = caption.stringOrNull("languageTag")
                )
            }
        )
    }

    private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> =
        (0 until length()).map { mapper(getJSONObject(it)) }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else optString(key)

    companion object { const val SCHEMA_VERSION = 3 }
}

data class ProjectSummary(val id: String, val name: String, val updatedAtEpochMs: Long)
