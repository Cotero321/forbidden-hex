#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;
    vertexDistance = FogShape == 0 ? length(viewPosition.xyz)
        : max(length(viewPosition.xz), abs(viewPosition.y));
    vertexColor = Color;
    texCoord0 = UV0;
}
