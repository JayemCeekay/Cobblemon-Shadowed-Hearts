#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec2 Direction;
uniform float BlurRadius;
uniform float HaloRadius;
uniform vec2 HaloPhase;

in vec2 texCoord0;
out vec4 fragColor;

float featherSource(vec4 density) {
    // Broad smoke drives coverage. Narrow wisps lift the edge on the same frame.
    return min(density.r + density.b * 0.42, 1.7);
}

void main() {
    ivec2 size = textureSize(Sampler0, 0);
    ivec2 pixel = clamp(ivec2(texCoord0 * vec2(size)), ivec2(0), size - 1);
    ivec2 axis = Direction.x > 0.0 ? ivec2(1, 0) : ivec2(0, 1);
    vec4 center = texelFetch(Sampler0, pixel, 0);
    ivec2 nearPixels[2];
    vec4 nearColors[2];
    vec4 farColors[2];
    vec4 neighborhood = center;
    for (int i = 0; i < 2; i++) {
        ivec2 delta = (2 * i - 1) * axis;
        nearPixels[i] = clamp(pixel + delta * int(ceil(BlurRadius)), ivec2(0), size - 1);
        nearColors[i] = texelFetch(Sampler0, nearPixels[i], 0);
        vec2 uv = (vec2(pixel) + 0.5 + vec2(delta) * HaloRadius) / vec2(size);
        farColors[i] = texture(Sampler0, uv);
        neighborhood += nearColors[i] + farColors[i];
    }
    // Black is the clear color of the density buffer. Most of the viewport
    // takes this path without sampling scene depth or evaluating material motion.
    if (!any(greaterThan(neighborhood, vec4(0.0)))) {
        fragColor = vec4(0.0);
        return;
    }
    float depth = texelFetch(Sampler1, pixel, 0).r;
    vec4 sum = center * 6.0;
    float weight = 6.0;
    float halo = 0.0;

    // Each filter draw already runs along one axis. Use the two otherwise
    // duplicated neighbor taps for a wider lobe driven by the moving smoke.
    for (int i = 0; i < 2; i++) {
        int side = 2 * i - 1;
        ivec2 stepToSource = side * axis;
        vec4 nearSmoke = nearColors[i];
        float nearDepth = texelFetch(Sampler1, nearPixels[i], 0).r;
        // Use the same texel for depth and color. Reject deeper neighbors on
        // foreground geometry, even at long viewing distances.
        float nearWeight = step(nearDepth, depth + 0.000005) * step(abs(nearDepth - depth), 0.002);
        sum += nearSmoke * nearWeight;
        weight += nearWeight;

        // Linear sampling between texels 2 and 3 forms a continuous skirt even
        // for a one-pixel source. At a depth edge filter each donor separately.
        ivec2 middle = clamp(pixel + 2 * stepToSource, ivec2(0), size - 1);
        ivec2 outer = clamp(pixel + 3 * stepToSource, ivec2(0), size - 1);
        float innerDepth = texelFetch(Sampler1, middle, 0).r;
        float outerDepth = texelFetch(Sampler1, outer, 0).r;
        vec4 farSmoke = farColors[i];
        if (max(innerDepth, outerDepth) > depth + 0.000005) {
            farSmoke = mix(texelFetch(Sampler0, middle, 0) * step(innerDepth, depth + 0.000005),
                           texelFetch(Sampler0, outer, 0) * step(outerDepth, depth + 0.000005), HaloRadius - 2.0);
        }
        float wispShare = clamp(farSmoke.b / max(farSmoke.r + farSmoke.b, 0.001), 0.0, 1.0);
        // Haze and wisps travel with offset phases. A changing donor changes both
        // the shape and direction of the lobe; empty areas have no source.
        float sway = axis.x == 1 ? mix(HaloPhase.x, HaloPhase.y, wispShare)
                                 : mix(HaloPhase.y, -HaloPhase.x, wispShare);
        halo += featherSource(farSmoke) * (1.0 + float(side) * 0.28 * sway);
    }

    vec4 smoke = sum / weight;
    float feather = max(halo * 0.5 - featherSource(smoke) * 0.70, 0.0) * 0.52;
    // Only broad violet and a little wisp density travel into the halo; bright
    // flecks remain at their source and batch tint stays in the composite.
    fragColor = smoke + vec4(feather, 0.0, feather * 0.18, feather * 0.46);
}
