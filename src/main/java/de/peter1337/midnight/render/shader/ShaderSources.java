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
            
            // Uniforms for texture mode:
            uniform sampler2D uTexture;
            uniform vec2 texSize;
            uniform bool useTexture;
            
            // Uniforms for shape rendering (if not in texture mode)
            uniform vec2 resolution;
            uniform vec4 rect;
            uniform float radius;
            uniform float smoothing;
            uniform vec4 color;
            uniform vec4 outlineColor;
            uniform float outlineWidth;
            uniform float guiScale;
            uniform vec4 clipBounds;  // x, y, width, height
            uniform float clipRadius;
            
            // Bicubic kernel (Catmull-Rom)
            float cubic(float v) {
                v = abs(v);
                if(v <= 1.0) {
                    return 1.5 * v * v * v - 2.5 * v * v + 1.0;
                } else if(v < 2.0) {
                    return -0.5 * v * v * v + 2.5 * v * v - 4.0 * v + 2.0;
                } else {
                    return 0.0;
                }
            }
            
            // Bicubic texture sampling function.
            vec4 textureBicubic(sampler2D tex, vec2 uv, vec2 texSize) {
                vec2 pixelPos = uv * texSize - 0.5;
                vec2 frac = fract(pixelPos);
                vec2 start = floor(pixelPos);
                vec4 col = vec4(0.0);
                for (int j = -1; j <= 2; j++) {
                    for (int i = -1; i <= 2; i++) {
                        vec2 offset = vec2(float(i), float(j));
                        float weight = cubic(frac.x - float(i)) * cubic(frac.y - float(j));
                        col += texture(tex, (start + offset + 0.5) / texSize) * weight;
                    }
                }
                return col;
            }
            
            // Rounded box SDF for shape drawing:
            float roundedBoxSDF(vec2 CenterPos, vec2 size, float radius) {
                vec2 q = abs(CenterPos) - size + radius;
                return length(max(q, 0.0)) - radius;
            }
            
            bool isInsideClipBounds(vec2 pixelPos) {
                if (clipBounds.z == 0.0 || clipBounds.w == 0.0) {
                    return true;
                }
                vec2 pos = pixelPos / guiScale;
                vec2 relPos = pos - clipBounds.xy;
                vec2 halfSize = clipBounds.zw * 0.5;
                vec2 center = halfSize;
                float dist = roundedBoxSDF(relPos - center, halfSize, clipRadius);
                return dist <= 0.0;
            }
            
            void main() {
                // If useTexture is true, sample using bicubic filtering.
                if (useTexture) {
                    FragColor = textureBicubic(uTexture, TexCoord, texSize);
                    return;
                }
                
                // Otherwise, perform shape rendering (existing rounded rectangle drawing)
                if (!isInsideClipBounds(gl_FragCoord.xy)) {
                    discard;
                }
                
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
