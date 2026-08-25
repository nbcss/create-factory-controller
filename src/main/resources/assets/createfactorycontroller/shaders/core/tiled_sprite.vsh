#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 SpriteMin; // UV1
in ivec2 SpriteMax; // UV2, Contraption Lights hooks variables named UV2 so it cannot be named that

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 targetCoord;
out vec4 vertexColor;
flat out vec2 spriteTopLeft;
flat out vec2 spriteBottomRight;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    targetCoord = UV0;
    vertexColor = Color;
    spriteTopLeft = SpriteMin;
    spriteBottomRight = SpriteMax;
}
