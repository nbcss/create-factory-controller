#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec2 texSize;

in vec2 targetCoord;
in vec4 vertexColor;
flat in vec2 spriteTopLeft;
flat in vec2 spriteBottomRight;

out vec4 fragColor;

void main() {
    vec2 spriteSize = spriteBottomRight - spriteTopLeft;
    vec2 texel = clamp(floor(mod(targetCoord, spriteSize)), vec2(0.0), spriteSize - 1.0) + spriteTopLeft;

    vec4 color = texture(Sampler0, (texel + 0.5) / vec2(textureSize(Sampler0, 0)));
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * vertexColor * ColorModulator;
}
