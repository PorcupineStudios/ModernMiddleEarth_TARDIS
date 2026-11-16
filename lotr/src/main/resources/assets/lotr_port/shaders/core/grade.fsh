#version 150

// Middle-earth colour grading (client/ColorGrade): per region, eased with the region looks.
uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Saturation;
uniform float Contrast;
uniform float Warmth;
uniform float Brightness;
uniform float Vignette;
uniform float Bloom;

in vec2 texCoord;
out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

void main() {
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    if (Bloom > 0.0) {
        // soft glow from the bright parts: 12 taps on two rings
        vec2 px = 1.0 / InSize;
        vec3 glow = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            float a = float(i) * 1.0472;
            vec2 d = vec2(cos(a), sin(a));
            vec3 s1 = texture(DiffuseSampler, texCoord + d * px * 4.0).rgb;
            vec3 s2 = texture(DiffuseSampler, texCoord + d.yx * px * 9.0).rgb;
            glow += max(s1 - 0.65, 0.0) + max(s2 - 0.65, 0.0);
        }
        c += glow / 12.0 * Bloom * 2.0;
    }
    float l = dot(c, LUMA);
    c = mix(vec3(l), c, Saturation);
    c = (c - 0.5) * Contrast + 0.5;
    // warm = golden highlights and amber shadows, cool = blue-grey
    c += Warmth * vec3(0.06, 0.02, -0.07) * (0.4 + l);
    c *= Brightness;
    float d = distance(texCoord, vec2(0.5));
    c *= 1.0 - Vignette * smoothstep(0.35, 0.85, d);
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
