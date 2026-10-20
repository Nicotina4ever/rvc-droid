package com.rvc.droid.ui.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
class ProjectManagerTest {
    @Test
    fun saveProjectPersistsVideoAndRecordings() {
        val tempDir = Files.createTempDirectory("rvc-project-test").toFile()
        val projectFile = File(tempDir, "test.rvcp")

        val projectState = ProjectState(
            name = "Test Project",
            videoPath = "/storage/emulated/0/Movies/sample.mp4",
            recordings = listOf(RecordingClip(name = "take1", path = "/storage/emulated/0/take1.wav", durationMs = 2500L))
        )

        assertTrue(ProjectManager.saveProject(projectState, projectFile))

        val loaded = ProjectManager.loadProject(projectFile)
        assertNotNull(loaded)
        assertEquals("/storage/emulated/0/Movies/sample.mp4", loaded?.videoPath)
        assertEquals(1, loaded?.recordings?.size)
        assertEquals("take1", loaded?.recordings?.firstOrNull()?.name)
    }

    @Test
    fun saveProjectPersistsMultipleClipsOnSameTrack() {
        val tempDir = Files.createTempDirectory("rvc-multiclip-test").toFile()
        val projectFile = File(tempDir, "multi.rvcp")
        val first = MixerTrackState(
            trackIndex = 0,
            clipPath = "/audio/first.wav",
            clipName = "first",
            durationMs = 1000L
        )
        val second = MixerTrackState(
            trackIndex = 0,
            clipPath = "/audio/second.wav",
            clipName = "second",
            durationMs = 2000L,
            offsetMs = 1000L
        )

        assertTrue(ProjectManager.saveProject(ProjectState(mixerTracks = listOf(first, second)), projectFile))

        val loaded = ProjectManager.loadProject(projectFile)
        assertEquals(2, loaded?.mixerTracks?.size)
        assertEquals(0, loaded?.mixerTracks?.get(0)?.trackIndex)
        assertEquals(0, loaded?.mixerTracks?.get(1)?.trackIndex)
        assertEquals(first.clipId, loaded?.mixerTracks?.get(0)?.clipId)
        assertEquals(second.clipId, loaded?.mixerTracks?.get(1)?.clipId)
    }
}
