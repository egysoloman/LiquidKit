package io.liquidkit

import androidx.compose.ui.graphics.Color
import io.liquidkit.skip.skipLiquidKitHeroItems
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidKitSharedTransitionTest {
    @Test
    fun skipHeroItemsBridgeResolvesSymbolsAndFallbacks() {
        val items = skipLiquidKitHeroItems(
            ids = listOf("refraction", "morph"),
            titles = listOf("光学折射", "融球形变"),
            subtitles = listOf("AGSL 实时透镜", "SDF 液态边缘"),
            badges = listOf("iOS 27", "AGSL"),
            systemImages = listOf("drop.halffull", "non.existent.symbol"),
            tints = listOf(Color(0xFF0A84FF), Color(0xFF00C7BE)),
            summaries = listOf("A", "B"),
        )
        assertEquals(2, items.size)
        assertEquals(LiquidKitSymbol.Drop, items[0].symbol)
        assertEquals(LiquidKitSymbol.Sparkles, items[1].symbol)
        assertEquals("融球形变", items[1].title)
    }

    @Test
    fun sharedPlayerTrackPreservesMetadataAndClampsValues() {
        val track = LiquidKitSharedPlayerTrack(
            id = "track-1",
            title = "Refractive Echo",
            artist = "LiquidStudio",
            bpm = 120,
            tint = Color(0xFF00C7BE),
            isPlaying = false,
            progress = 0.65f,
        )
        assertEquals("track-1", track.id)
        assertEquals(120, track.bpm)
        assertEquals(false, track.isPlaying)
        assertEquals(0.65f, track.progress, 0.001f)
    }
}
