package com.jayemceekay.shadowedhearts.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.renderer.GameRenderer
import org.lwjgl.opengl.GL11

internal fun GameRenderer.processTooltipBlur(partialTick: Float) {
    val depthTestEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
    // A blur pass with depth testing enabled can write depth in front of the tooltip glyphs.
    RenderSystem.disableDepthTest()
    try {
        processBlurEffect(partialTick)
    } finally {
        if (depthTestEnabled) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
    }
}
