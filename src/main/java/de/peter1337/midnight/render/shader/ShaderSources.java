package de.peter1337.midnight.render.shader;

public class ShaderSources {
    public static final String VERTEX_SHADER_SOURCE = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            layout (location = 1) in vec2 aTexCoord;
            out vec2 TexCoord;
            uniform mat4 transform;
            uniform float guiScale;
            void main() {
                vec4 scaledPos = transform * vec4(aPos * guiScale, 1.0);
                gl_Position = scaledPos;
                TexCoord = aTexCoord;
            }
            """;

    public static final String FRAGMENT_SHADER_SOURCE = """
            #version 330 core
            in vec2 TexCoord;
            out vec4 FragColor;
            
            // Existing uniforms
            uniform vec2 resolution;
            uniform vec4 rect;
            uniform float radius;
            uniform float smoothing;
            uniform vec4 color;
            uniform vec4 outlineColor;
            uniform float outlineWidth;
            uniform float guiScale;
            
            // Clipping uniforms
            uniform vec4 clipBounds;  // x, y, width, height
            uniform float clipRadius;
            
            float roundedBoxSDF(vec2 CenterPos, vec2 size, float radius) {
                vec2 q = abs(CenterPos) - size + radius;
                return length(max(q, 0.0)) - radius;
            }
            
            bool isInsideClipBounds(vec2 pixelPos) {
                // Early out if clipping is disabled
                if (clipBounds.z == 0.0 || clipBounds.w == 0.0) {
                    return true;
                }
                
                // Convert pixel position to match GUI space
                vec2 pos = pixelPos / guiScale;
                
                // Calculate relative position from clip bounds
                vec2 relPos = pos - clipBounds.xy;
                vec2 halfSize = clipBounds.zw * 0.5;
                vec2 center = halfSize;
                
                // Use the same SDF function for consistent rounding
                float dist = roundedBoxSDF(relPos - center, halfSize, clipRadius);
                
                return dist <= 0.0;
            }
            
            void main() {
                // Check clipping first
                if (!isInsideClipBounds(gl_FragCoord.xy)) {
                    discard;
                }
                
                // Original shape rendering
                vec2 pixelPos = gl_FragCoord.xy / guiScale;
                vec2 pos = pixelPos - rect.xy;
                vec2 size = rect.zw * 0.5;
                vec2 center = size;
                
                float dist = roundedBoxSDF(pos - center, size, radius);
                
                float alpha = 1.0 - smoothstep(0.0, smoothing, dist);
                float outlineDist = abs(dist) - outlineWidth;
                float outlineAlpha = 1.0 - smoothstep(0.0, smoothing, outlineDist);
                
                vec4 mainColor = vec4(color.rgb, color.a * alpha);
                vec4 outline = vec4(outlineColor.rgb, outlineColor.a * outlineAlpha);
                
                FragColor = mix(mainColor, outline, outlineAlpha * outlineColor.a);
            }
            """;
}