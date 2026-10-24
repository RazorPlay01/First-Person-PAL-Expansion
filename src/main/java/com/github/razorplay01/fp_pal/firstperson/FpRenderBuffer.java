package com.github.razorplay01.fp_pal.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
//? if <1.21.11 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.RenderType;
//? if >=1.21.11 {
//import net.minecraft.client.renderer.SubmitNodeCollector;
//?}

/**
 * Thin wrapper over the client render buffer, so the first-person render code does not have to know
 * which pipeline the running game uses.
 * <p>
 * Up to 1.21.1 vanilla draws through {@code MultiBufferSource}: geometry is written into the
 * {@link VertexConsumer} of a {@link RenderType} and only reaches the screen when the batch is
 * flushed. From 1.21.9 on (so in 1.21.11 and 26.x) vanilla <i>collects</i> geometry instead:
 * {@code SubmitNodeCollector} takes the draw calls and the renderer submits them later, which means a
 * {@link VertexConsumer} only exists inside the {@code submitCustomGeometry} callback.
 * <p>
 * Both pipelines are hidden behind {@link #drawModelPart} and {@link #drawGeometry}: the shared code
 * describes <i>what</i> to draw, this class decides <i>how</i> to submit it. That keeps the vanilla
 * arm mesh ({@link FpArmRenderUtil}), the animated geo model ({@link FpModelRenderer}) and the
 * held-item transforms free of preprocessor blocks.
 */
public final class FpRenderBuffer {

    //? if >=1.21.11 {
    //private final SubmitNodeCollector collector;
    //?} else {
    private final MultiBufferSource buffer;
    //?}

    /**
     * Geometry writer used by {@link #drawGeometry}: it receives the pose stack to draw in and the
     * vertex consumer to write to, and must write all of its geometry in one call.
     */
    @FunctionalInterface
    public interface GeometryRenderer {
        void render(PoseStack poseStack, VertexConsumer consumer);
    }

    //? if >=1.21.11 {
    /*public FpRenderBuffer(SubmitNodeCollector collector) {
        this.collector = collector;
    }
    *///?} else {
    public FpRenderBuffer(MultiBufferSource buffer) {
        this.buffer = buffer;
    }
    //?}

    /**
     * Draws a single vanilla model part (the first-person arm and its sleeve) in the given render type.
     * <p>
     * Both pipelines end up calling {@link ModelPart#render(PoseStack, VertexConsumer, int, int)}, so
     * the model part state set up by the caller is respected exactly the same way.
     */
    public void drawModelPart(ModelPart part, PoseStack poseStack, RenderType renderType,
                              int packedLight, int packedOverlay) {
        //? if >=1.21.11 {
        //this.collector.submitModelPart(part, poseStack, renderType, packedLight, packedOverlay, null);
        //?} else {
        part.render(poseStack, this.buffer.getBuffer(renderType), packedLight, packedOverlay);
        //?}
    }

    /**
     * Draws custom geometry (the {@code fp_models} geo model) with the given render type.
     * <p>
     * On the legacy pipeline the geometry is written straight into the render type's vertex consumer.
     * On the collector pipeline the whole geometry has to be written inside the submit callback, so
     * the renderer is invoked there instead. {@code MultiBufferSource.BufferSource} keeps only the most
     * recently requested render type in its shared first-person buffer, which is why the legacy branch
     * flushes the batch at the end: without it the geo model's builder stays pending and the model is
     * never drawn.
     */
    public void drawGeometry(PoseStack poseStack, RenderType renderType, GeometryRenderer renderer) {
        //? if >=1.21.11 {
        //this.collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> renderer.render(poseStack, consumer));
        //?} else {
        renderer.render(poseStack, this.buffer.getBuffer(renderType));
        if (this.buffer instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(renderType);
        }
        //?}
    }
}
