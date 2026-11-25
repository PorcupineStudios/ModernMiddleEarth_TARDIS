#version 150

// Middle-earth colour grading and the Ring's wraith-world (client/ColorGrade): per region, eased with the region
// looks, plus the worn ring's look (client/RingSight). Every effect is off at its neutral value.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Saturation;
uniform float Contrast;
uniform float Warmth;
uniform float Brightness;
uniform float Vignette;
uniform float Bloom;
uniform float Grain;      // film noise
uniform float Aberration; // colour fringes toward the edges
uniform float Wobble;     // heat shimmer
uniform float Blur;       // edges smeared, as the world seen through the Ring
uniform vec2 Smear;       // camera-turn smear: uv offset along the turn (0 = still)
uniform vec4 Tint;        // rgb, amount
uniform vec4 Eye;         // x: -1 left .. 1 right, y: facing it -1..1, z: 1 = may pulse, w: strength
uniform vec3 EyeGlow;
uniform float Time;       // seconds

in vec2 texCoord;
out vec4 fragColor;

const float CONTRAST_PIVOT = 0.2;
const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

// noise without a visible pattern (a fract(p * constant) hash repeats as a pixel grid, which read as pixelation)
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 tap(vec2 uv, vec2 fringe) {
    if (Aberration <= 0.0) {
        return texture(DiffuseSampler, uv).rgb;
    }
    return vec3(texture(DiffuseSampler, uv + fringe).r, texture(DiffuseSampler, uv).g,
            texture(DiffuseSampler, uv - fringe).b);
}

void main() {
    vec2 px = 1.0 / InSize;
    vec2 uv = texCoord;
    vec2 fromMid = uv - 0.5;
    float edge = smoothstep(0.15, 0.75, length(fromMid * vec2(InSize.x / InSize.y, 1.0)) * 0.8);
    if (Wobble > 0.0) {
        // slow rising shimmer, stronger low on the screen (hot ground) and toward the edges
        float k = Wobble * (0.4 + 0.6 * (1.0 - uv.y) + 0.5 * edge);
        uv += k * vec2(sin(uv.y * 38.0 + Time * 2.3) * 0.0025 + sin(uv.y * 91.0 - Time * 3.1) * 0.0011,
                cos(uv.x * 29.0 + Time * 1.7) * 0.0015);
    }
    vec2 fringe = fromMid * Aberration * 0.012 * (0.3 + edge);
    vec3 c = tap(uv, fringe);
    if (Blur > 0.0) {
        // 8 taps on a ring that widens toward the edges, mixed in by distance from the middle
        vec3 sum = vec3(0.0);
        float r = Blur * (2.0 + 10.0 * edge);
        for (int i = 0; i < 8; i++) {
            float a = float(i) * 0.7854 + Time * 0.15;
            sum += tap(uv + vec2(cos(a), sin(a)) * px * r, fringe);
        }
        c = mix(c, sum / 8.0, clamp(Blur * (0.35 + 0.65 * edge), 0.0, 1.0));
    }
    if (Smear.x != 0.0 || Smear.y != 0.0) {
        // smear along a sharp turn: 9 taps across the turn's path, the middle weighted most so edges stay readable
        vec3 sum = vec3(0.0);
        float wsum = 0.0;
        for (int i = 0; i < 9; i++) {
            float t = float(i) / 8.0 - 0.5;
            float wt = 1.0 - abs(t);
            sum += tap(uv + Smear * t, fringe) * wt;
            wsum += wt;
        }
        c = mix(c, sum / wsum, clamp(length(Smear * InSize) / 6.0, 0.0, 1.0));
    }
    if (Bloom > 0.0) {
        // soft glow from the bright parts: 12 taps on two rings
        vec3 glow = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            float a = float(i) * 1.0472;
            vec2 d = vec2(cos(a), sin(a));
            vec3 s1 = texture(DiffuseSampler, uv + d * px * 4.0).rgb;
            vec3 s2 = texture(DiffuseSampler, uv + d.yx * px * 9.0).rgb;
            glow += max(s1 - 0.65, 0.0) + max(s2 - 0.65, 0.0);
        }
        c += glow / 12.0 * Bloom * 2.0;
    }
    float l = dot(c, LUMA);
    c = mix(vec3(l), c, Saturation);
    // contrast pivots on a dark grey, not mid-grey: around 0.5 it crushed already dark lands (Mordor) to black
    c = (c - CONTRAST_PIVOT) * Contrast + CONTRAST_PIVOT;
    // warm = golden highlights and amber shadows, cool = blue-grey
    c += Warmth * vec3(0.06, 0.02, -0.07) * (0.4 + l);
    c *= Brightness;
    if (Tint.a > 0.0) {
        // tint by luminance (a coloured light over the scene, not a flat wash)
        float tl = dot(c, LUMA);
        c = mix(c, Tint.rgb * (0.25 + tl * 1.2), Tint.a);
    }
    float d = distance(texCoord, vec2(0.5));
    c *= 1.0 - Vignette * smoothstep(0.35, 0.85, d);
    if (Eye.w > 0.0) {
        // the dark leans toward the Eye: shade the screen's side that faces it
        float side = clamp(fromMid.x * 2.0 * Eye.x, 0.0, 1.0);
        float behind = clamp(-Eye.y, 0.0, 1.0);
        c *= 1.0 - Eye.w * 0.55 * smoothstep(0.0, 1.0, side) * (0.5 + 0.5 * behind);
        // facing it: a slow pulse of fire light low in the middle of the view
        float facing = smoothstep(0.75, 1.0, Eye.y);
        float pulse = Eye.z > 0.5 ? 0.6 + 0.4 * sin(Time * 1.3) : 0.8;
        float spot = exp(-dot(fromMid * vec2(2.2, 4.0), fromMid * vec2(2.2, 4.0)) * 3.0);
        c += EyeGlow * spot * facing * pulse * Eye.w * 0.35;
    }
    if (Grain > 0.0) {
        // fine film grain, strongest in the mid-tones (in the darks it looked like a checkerboard)
        vec2 cell = floor(texCoord * InSize);
        float frame = floor(Time * 24.0);
        float n = hash(cell + vec2(frame * 37.0, frame * 17.0)) + hash(cell.yx * 1.37 + frame * 11.0) - 1.0;
        float mid = 4.0 * l * (1.0 - l);
        c += n * Grain * 0.05 * (0.35 + mid);
    }
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
