package com.github.razorplay01.fp_pal.firstperson;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
//? if <1.21.11 {
//import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.rendertype.RenderType;
//? if >=1.21.11 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?}

/**
 * Thin wrapper over the client render buffer, so the first-person render code does not have to know
 * which pipeline the running game uses: up to 1.21.1 vanilla draws through {@code MultiBufferSource},
 * from 1.21.9 on it collects geometry through {@code SubmitNodeCollector}. Both are hidden behind
 * {@link #drawModelPart} and {@link #drawGeometry}.
 */
public final class FpRenderBuffer {

    //? if >=1.21.11 {
    private final SubmitNodeCollector collector;
    //?} else {
    //private final MultiBufferSource buffer;
    //?}

    /**
     * Geometry writer used by {@link #drawGeometry}; it must write all of its geometry in one call.
     */
    @FunctionalInterface
    public interface GeometryRenderer {
        void render(PoseStack poseStack, VertexConsumer consumer);
    }

    //? if >=1.21.11 {
    public FpRenderBuffer(SubmitNodeCollector collector) {
        this.collector = collector;
    }
    //?} else {
    /*public FpRenderBuffer(MultiBufferSource buffer) {
        this.buffer = buffer;
    }
    *///?}

    /**
     * Draws a single vanilla model part (the first-person arm and its sleeve) in the given render type.
     */
    public void drawModelPart(ModelPart part, PoseStack poseStack, RenderType renderType,
                              int packedLight, int packedOverlay) {
        //? if >=1.21.11 {
        this.collector.submitModelPart(part, poseStack, renderType, packedLight, packedOverlay, null);
        //?} else {
        //part.render(poseStack, this.buffer.getBuffer(renderType), packedLight, packedOverlay);
        //?}
    }

    /**
     * Draws custom geometry (the {@code fp_models} geo model) with the given render type. On the legacy
     * pipeline the batch is flushed at the end, because the shared first-person buffer only keeps the
     * most recently requested render type pending.
     */
    public void drawGeometry(PoseStack poseStack, RenderType renderType, GeometryRenderer renderer) {
        //? if >=1.21.11 {
        this.collector.submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
            // The callback runs when the collector is flushed, after the caller popped its pose, so the
            // captured Pose has to be replayed into a stack of its own.
            PoseStack captured = new PoseStack();
            captured.pushPose();
            captured.last().set(pose);
            renderer.render(captured, consumer);
        });
        //?} else {
        /*renderer.render(poseStack, this.buffer.getBuffer(renderType));
        if (this.buffer instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(renderType);
        }
        *///?}
    }
}
