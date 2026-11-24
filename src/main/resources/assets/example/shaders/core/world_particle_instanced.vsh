#version 150

#moj_import <fog.glsl>

in vec4 CenterRoll;
in vec2 HalfSize;
in vec4 Color;
in ivec2 UV2;
in vec4 UVBounds;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
uniform vec3 CameraRight;
uniform vec3 CameraUp;

out float vertexDistance;
out vec2 texCoord0;
out vec4 vertexColor;

void main() {
    bool right = gl_VertexID >= 2;
    bool top = gl_VertexID == 1 || gl_VertexID == 2;
    float x = right ? HalfSize.x : -HalfSize.x;
    float y = top ? HalfSize.y : -HalfSize.y;
    float c = cos(CenterRoll.w);
    float s = sin(CenterRoll.w);
    vec3 position = CenterRoll.xyz + CameraRight * (x * c - y * s) + CameraUp * (x * s + y * c);

    gl_Position = ProjMat * ModelViewMat * vec4(position, 1.0);
    vertexDistance = fog_distance(ModelViewMat, position, FogShape);
    texCoord0 = vec2(right ? UVBounds.z : UVBounds.x, top ? UVBounds.y : UVBounds.w);
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
}
