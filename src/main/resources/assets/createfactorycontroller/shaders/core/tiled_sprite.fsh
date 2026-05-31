#version 150

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform vec2 texSize;

in vec2 targetCoord;
flat in vec2 spriteTopLeft;
flat in vec2 spriteBottomRight;

out vec4 fragColor;

void main() {
    vec2 texCoord0 = mod(targetCoord, spriteBottomRight - spriteTopLeft) + spriteTopLeft;

    vec4 color = texture(Sampler0, texCoord0 / textureSize(Sampler0, 0));
    if (color.a == 0.0) {
        discard;
    }
    fragColor = color * ColorModulator;
}
