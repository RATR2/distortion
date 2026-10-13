#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform RippleConfig {
    float Width;
    float Strength;
    float Debug;
};

layout(location = 0) out vec4 fragColor;

// Must match assets/minecraft/shaders/core/text.fsh.
const int MARKER_SLOTS = 16;
const int MARKER_FIRST_ROW = 8;
const vec3 MARKER_SIGNATURE = vec3(127.0, 1.0, 126.0) / 255.0;

struct Ripple {
    vec2 center;
    float radius;
    vec3 color;
    float strength;
    float screenPerBlock;
};

float decode16(float hi, float lo) {
    return (round(hi * 255.0) + lo) / 255.0;
}

bool readRipple(int slot, out Ripple ripple) {
    int row = MARKER_FIRST_ROW + slot;
    vec3 signature = texelFetch(InSampler, ivec2(1, row), 0).rgb;
    if (any(greaterThan(abs(signature - MARKER_SIGNATURE), vec3(1.5 / 255.0)))) {
        return false;
    }
    vec3 a = texelFetch(InSampler, ivec2(2, row), 0).rgb;
    vec3 b = texelFetch(InSampler, ivec2(3, row), 0).rgb;
    vec3 c = texelFetch(InSampler, ivec2(4, row), 0).rgb;
    vec3 d = texelFetch(InSampler, ivec2(5, row), 0).rgb;
    ripple.center = vec2(decode16(a.r, a.g), decode16(a.b, b.r)) * 3.0 - 1.0;
    ripple.radius = decode16(b.g, b.b) * 4.0;
    ripple.color = c;
    ripple.strength = d.r;
    ripple.screenPerBlock = decode16(d.g, d.b) * 2.0;
    return true;
}

// Marker data pixels overwrite the scene in the corner; cover them with the column just right of the strip.
vec3 sceneAt(vec2 uv, vec2 size) {
    vec2 px = uv * size;
    if (px.x < 6.0 && px.y >= float(MARKER_FIRST_ROW) && px.y < float(MARKER_FIRST_ROW + MARKER_SLOTS)) {
        px.x = 6.5;
    }
    return texture(InSampler, px / size).rgb;
}

void main() {
    vec2 size = vec2(textureSize(InSampler, 0));
    vec2 aspect = vec2(size.x / size.y, 1.0);

    vec2 offset = vec2(0.0);
    vec3 tint = vec3(0.0);
    float tintWeight = 0.0;
    for (int slot = 0; slot < MARKER_SLOTS; slot++) {
        Ripple ripple;
        if (!readRipple(slot, ripple)) {
            continue;
        }
        // Width and Strength are in blocks, scaled to the screen at each marker's distance.
        vec2 toCenter = (texCoord - ripple.center) * aspect;
        float dist = length(toCenter);
        float thickness = max(Width * ripple.screenPerBlock, 0.0001);
        float ring = exp(-pow((dist - ripple.radius) / thickness, 2.0)) * ripple.strength;
        if (dist > 0.0001) {
            offset += toCenter / dist * ring * Strength * ripple.screenPerBlock;
        }
        tint += ripple.color * ring;
        tintWeight += ring;
    }

    vec3 color = sceneAt(texCoord - offset / aspect, size);
    if (tintWeight > 0.0) {
        color = mix(color, tint / tintWeight, min(tintWeight, 1.0) * 0.5);
    }

    if (Debug > 0.5) {
        vec2 px = texCoord * size;
        // Each live ripple: an outline at its current radius and a cross at its center, in its own color.
        for (int slot = 0; slot < MARKER_SLOTS; slot++) {
            Ripple ripple;
            if (!readRipple(slot, ripple)) {
                continue;
            }
            vec2 fromCenter = px - ripple.center * size;
            bool onOutline = abs(length(fromCenter) - ripple.radius * size.y) < 1.0;
            bool onCross = max(abs(fromCenter.x), abs(fromCenter.y)) < 8.0 && min(abs(fromCenter.x), abs(fromCenter.y)) < 1.0;
            if (onOutline || onCross) {
                color = ripple.color;
            }
        }
        // Raw data strip at x8 in the corner: one row per slot, starting purple when the slot is live.
        if (px.x < 7.0 * 8.0 && px.y < float(MARKER_SLOTS) * 8.0) {
            color = texelFetch(InSampler, ivec2(int(px.x / 8.0), MARKER_FIRST_ROW + int(px.y / 8.0)), 0).rgb;
        }
    }

    fragColor = vec4(color, 1.0);
}
