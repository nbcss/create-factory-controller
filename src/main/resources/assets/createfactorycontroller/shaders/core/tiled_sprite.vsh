#version 150

in vec3 Position;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 targetCoord;
flat out vec2 spriteTopLeft;
flat out vec2 spriteBottomRight;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    targetCoord = UV0;
    spriteTopLeft = UV1;
    spriteBottomRight = UV2;
}
