package com.drdisagree.xmorphclock.engine.render

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class GlassRenderer {

    private val shader = RuntimeShader(LIQUID)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private val masks = LinkedHashMap<String, GlassMask>(MASK_CACHE_SIZE, 0.75f, true)

    fun draw(canvas: Canvas, path: Path, key: String, image: Shader, alpha: Int, tint: Int, tintAmount: Float, bevel: Float) {
        path.computeBounds(bounds, true)
        if (bounds.isEmpty) return
        val edge = max(1f, min(bevel, min(bounds.width(), bounds.height()) * REFRACTION_RATIO))
        val base = edge * REFRACTION_DEPTH
        val mask = mask(path, key, edge)
        shader.setInputShader(IMAGE, image)
        shader.setFloatUniform(ORIGIN, bounds.left, bounds.top)
        shader.setFloatUniform(SIZE, bounds.width(), bounds.height())
        shader.setFloatUniform(RADII, 0f, 0f, 0f, 0f)
        shader.setFloatUniform(REFRACTION, base)
        shader.setFloatUniform(EDGE, edge)
        shader.setFloatUniform(DISPERSION, DISPERSION_AMOUNT)
        shader.setFloatUniform(RIM, RIM_AMOUNT)
        shader.setFloatUniform(SATURATION, 1f)
        shader.setFloatUniform(DIM, Color.red(tint) / 255f, Color.green(tint) / 255f, Color.blue(tint) / 255f, tintAmount)
        shader.setInputShader(MASK, mask.shader)
        shader.setFloatUniform(USE_MASK, 1f)
        shader.setFloatUniform(MASK_ORIGIN, bounds.left + mask.originX / mask.scale, bounds.top + mask.originY / mask.scale)
        shader.setFloatUniform(MASK_SCALE, mask.scale)
        shader.setFloatUniform(MASK_RANGE, mask.range / mask.scale)
        paint.shader = shader
        paint.alpha = alpha
        canvas.drawPath(path, paint)
        paint.shader = null
    }

    private fun mask(path: Path, key: String, edge: Float): GlassMask {
        val bucketWidth = max(MASK_SIZE_STEP, (bounds.width() / MASK_SIZE_STEP).roundToInt() * MASK_SIZE_STEP)
        val scale = bucketWidth / max(1f, bounds.width())
        val bucketHeight = max(1, (bounds.height() * scale).roundToInt())
        val blurRadius = max(1, (edge * scale * MASK_BLUR).roundToInt())
        val cacheKey = "$key|$bucketWidth|$bucketHeight|$blurRadius"
        masks[cacheKey]?.let { return it }
        val padding = MASK_PADDING + blurRadius * 3
        val sharp = createBitmap(bucketWidth + padding * 2, bucketHeight + padding * 2, Bitmap.Config.ALPHA_8)
        Canvas(sharp).apply {
            translate(padding.toFloat(), padding.toFloat())
            scale(scale, scale)
            translate(-bounds.left, -bounds.top)
            drawPath(path, maskPaint)
        }
        val sigma = sqrt(blurRadius * (blurRadius + 1f))
        val blurPaint = Paint().apply {
            maskFilter = BlurMaskFilter(max(0.5f, (sigma - 0.5f) / BLUR_SIGMA_SCALE), BlurMaskFilter.Blur.NORMAL)
        }
        val offset = IntArray(2)
        val blurred = sharp.extractAlpha(blurPaint, offset)
        sharp.recycle()
        val mask = GlassMask(
            BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            },
            offset[0] - padding.toFloat(),
            offset[1] - padding.toFloat(),
            scale,
            blurRadius * MASK_RAMP
        )
        if (masks.size >= MASK_CACHE_SIZE) masks.remove(masks.keys.first())
        masks[cacheKey] = mask
        return mask
    }

    private companion object {
        const val IMAGE = "image"
        const val ORIGIN = "origin"
        const val SIZE = "size"
        const val RADII = "radii"
        const val REFRACTION = "refraction"
        const val EDGE = "edge"
        const val DISPERSION = "dispersion"
        const val RIM = "rim"
        const val SATURATION = "saturation"
        const val DIM = "dim"
        const val MASK = "mask"
        const val USE_MASK = "useMask"
        const val MASK_ORIGIN = "maskOrigin"
        const val MASK_SCALE = "maskScale"
        const val MASK_RANGE = "maskRange"
        const val REFRACTION_RATIO = 0.18f
        const val REFRACTION_DEPTH = 1.2f
        const val DISPERSION_AMOUNT = 0.06f
        const val RIM_AMOUNT = 1f
        const val MASK_BLUR = 0.3f
        const val MASK_RAMP = 1.7f
        const val MASK_PADDING = 8
        const val MASK_CACHE_SIZE = 24
        const val MASK_SIZE_STEP = 8
        const val BLUR_SIGMA_SCALE = 0.57735f

        const val LIQUID = """
        uniform shader image;
        uniform float2 origin;
        uniform float2 size;
        uniform float4 radii;
        uniform float refraction;
        uniform float edge;
        uniform float dispersion;
        uniform float rim;
        uniform float saturation;
        uniform shader mask;
        uniform float useMask;
        uniform float2 maskOrigin;
        uniform float maskScale;
        uniform float maskRange;
        uniform float4 dim;

        float roundedBox(float2 p, float2 halfSize, float r) {
            float2 q = abs(p) - halfSize + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        float cornerRadius(float2 p) {
            return p.y > 0.0 ? (p.x > 0.0 ? radii.z : radii.w) : (p.x > 0.0 ? radii.y : radii.x);
        }

        float boxDistance(float2 p, float2 halfSize) {
            return roundedBox(p, halfSize, cornerRadius(p));
        }

        float maskDistance(float2 coord) {
            float m = mask.eval((coord - maskOrigin) * maskScale).a;
            return (0.5 - m) * 2.0 * maskRange;
        }

        half4 main(float2 coord) {
            float2 halfSize = size * 0.5;
            float2 p = coord - origin - halfSize;
            float2 e = float2(1.0, 0.0);
            float d;
            float2 normal;
            float width = edge;
            float strength = 1.0;

            if (useMask > 0.5) {
                d = maskDistance(coord);
                float2 m = float2(2.0, 0.0);
                normal = float2(
                    maskDistance(coord + m.xy) - maskDistance(coord - m.xy),
                    maskDistance(coord + m.yx) - maskDistance(coord - m.yx)
                );
                width = min(edge, maskRange * 0.9);
                strength = smoothstep(0.05, 0.4, length(normal));
            } else {
                d = boxDistance(p, halfSize);
                normal = float2(
                    boxDistance(p + e.xy, halfSize) - boxDistance(p - e.xy, halfSize),
                    boxDistance(p + e.yx, halfSize) - boxDistance(p - e.yx, halfSize)
                );
            }
            normal = normal / max(length(normal), 0.0001);

            float band = clamp(1.0 + d / max(width, 1.0), 0.0, 1.0);
            float bend = band * band * band * strength;
            float2 offset = -normal * refraction * bend;

            half4 color;
            color.r = image.eval(coord + offset * (1.0 + dispersion)).r;
            color.g = image.eval(coord + offset).g;
            color.b = image.eval(coord + offset * (1.0 - dispersion)).b;
            color.a = 1.0;
            half luma = dot(color.rgb, half3(0.2126, 0.7152, 0.0722));
            color.rgb = clamp(mix(half3(luma), color.rgb, half(saturation)), 0.0, 1.0);
            color.rgb = mix(color.rgb, half3(dim.rgb), half(dim.a));

            float rimBand = smoothstep(-2.5, 0.0, d);
            float light = clamp(dot(normal, normalize(float2(-0.5, -0.85))), 0.0, 1.0);
            float shade = clamp(dot(normal, normalize(float2(0.5, 0.85))), 0.0, 1.0);
            color.rgb += half3(rim * rimBand * (0.18 + 0.5 * light));
            color.rgb += half3(rim * bend * 0.12 * light);
            color.rgb -= half3(rim * bend * 0.06 * shade);
            color.rgb += half3(0.03);
            return color;
        }
    """
    }
}
