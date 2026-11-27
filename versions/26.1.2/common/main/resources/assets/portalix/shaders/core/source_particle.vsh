#version 330
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
out vec4 vertexColor;
out vec2 texCoord0;
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
#if SOURCE_LINEAR_OUTPUT == 1
    vertexColor = vec4(pow(Color.rgb, vec3(2.2)), Color.a);
#else
    vertexColor = Color;
#endif
    texCoord0 = UV0;
}
