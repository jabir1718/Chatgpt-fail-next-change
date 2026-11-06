package com.creatorstudio.app.ui

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val ORB_BANDS = 32

@Composable
fun GpuOrbVisualizer(spectrum: List<Float>, beatPulse: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val surface = remember(context) { OrbGlSurface(context) }
    LaunchedEffect(spectrum, beatPulse) { surface.submitAudio(spectrum, beatPulse) }
    DisposableEffect(surface) {
        surface.onResume()
        onDispose { surface.onPause() }
    }
    AndroidView(factory = { surface }, modifier = modifier)
}

internal fun supportsOpenGles2(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
    return manager.deviceConfigurationInfo.reqGlEsVersion >= 0x20000
}

private class OrbGlSurface(context: Context) : GLSurfaceView(context) {
    private val orbRenderer = OrbGlRenderer()
    init {
        setEGLContextClientVersion(2)
        setRenderer(orbRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = false
    }
    fun submitAudio(values: List<Float>, pulse: Int) {
        val normalized = FloatArray(ORB_BANDS) { i ->
            values.getOrNull(i)?.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        }
        queueEvent { orbRenderer.updateAudio(normalized, pulse) }
    }
}

private class OrbGlRenderer : GLSurfaceView.Renderer {
    @Volatile private var bands = FloatArray(ORB_BANDS)
    @Volatile private var beat = 0f
    private var program = 0
    private var resolutionLocation = -1
    private var timeLocation = -1
    private var bandsLocation = -1
    private var beatLocation = -1
    private var startNs = 0L
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
        position(0)
    }

    fun updateAudio(values: FloatArray, pulse: Int) {
        bands = values.copyOf(ORB_BANDS)
        beat = if (pulse % 2 == 0) 0f else 1f
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.01f, 0.02f, 0.04f, 1f)
        val vertex = compile(GLES20.GL_VERTEX_SHADER, VERTEX)
        val fragment = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) error("3D Orb shader link failed: ${GLES20.glGetProgramInfoLog(program)}")
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        resolutionLocation = GLES20.glGetUniformLocation(program, "uResolution")
        timeLocation = GLES20.glGetUniformLocation(program, "uTime")
        bandsLocation = GLES20.glGetUniformLocation(program, "uBands")
        beatLocation = GLES20.glGetUniformLocation(program, "uBeat")
        startNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    override fun onDrawFrame(gl: GL10?) {
        if (program == 0) { GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT); return }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glEnableVertexAttribArray(0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glUniform2f(resolutionLocation, width.coerceAtLeast(1).toFloat(), height.coerceAtLeast(1).toFloat())
        GLES20.glUniform1f(timeLocation, (System.nanoTime() - startNs) / 1_000_000_000f)
        GLES20.glUniform1fv(bandsLocation, ORB_BANDS, bands, 0)
        GLES20.glUniform1f(beatLocation, beat)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(0)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) error("3D Orb shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
        return shader
    }

    private companion object {
        const val VERTEX = """
            attribute vec2 aPosition;
            void main() { gl_Position = vec4(aPosition, 0.0, 1.0); }
        """
        const val FRAGMENT = """
            precision highp float;
            uniform vec2 uResolution;
            uniform float uTime;
            uniform float uBands[32];
            uniform float uBeat;
            const float PI = 3.14159265359;
            float audioAt(float angle) {
                float wrapped = mod(angle + PI, 2.0 * PI) / (2.0 * PI);
                int index = int(clamp(floor(wrapped * 32.0), 0.0, 31.0));
                return uBands[index];
            }
            vec2 sphereHit(vec3 ro, vec3 rd, float radius) {
                float b = dot(ro, rd);
                float c = dot(ro, ro) - radius * radius;
                float h = b * b - c;
                if (h < 0.0) return vec2(-1.0);
                return vec2(-b - sqrt(h), -b + sqrt(h));
            }
            void main() {
                vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution) / min(uResolution.x, uResolution.y);
                float time = uTime * 0.22;
                vec3 ro = vec3(0.0, 0.0, 3.25);
                vec3 rd = normalize(vec3(uv, -1.75));
                vec2 hit = sphereHit(ro, rd, 0.88);
                vec3 color = vec3(0.008, 0.018, 0.038);
                float star = fract(sin(dot(floor(gl_FragCoord.xy * 0.42), vec2(12.9898, 78.233))) * 43758.5453);
                color += vec3(0.08, 0.22, 0.34) * step(0.997, star);
                if (hit.x > 0.0) {
                    vec3 p = ro + rd * hit.x;
                    float angle = atan(p.y, p.x) + time;
                    float audio = audioAt(angle);
                    float pulse = uBeat * 0.045;
                    float ripple = sin(angle * 9.0 + time * 3.0) * 0.018;
                    vec3 normal = normalize(p);
                    normal.xz = mat2(cos(time), -sin(time), sin(time), cos(time)) * normal.xz;
                    vec3 light = normalize(vec3(-0.5, 0.8, 1.2));
                    float diffuse = max(dot(normal, light), 0.0);
                    float rim = pow(1.0 - max(dot(normalize(-rd), normal), 0.0), 2.2);
                    float pattern = 0.5 + 0.5 * sin(angle * 24.0 + time * 2.0 + audio * 5.0 + ripple);
                    vec3 base = mix(vec3(0.04, 0.78, 0.68), vec3(0.48, 0.27, 0.98), pattern);
                    color = base * (0.16 + diffuse * 0.9) + vec3(0.12, 0.85, 0.98) * rim * (0.45 + audio);
                    color += vec3(0.7, 0.35, 1.0) * smoothstep(0.82, 1.0, audio) * 0.22;
                    color *= 1.0 + pulse * 2.0;
                    float halo = exp(-abs(length(uv) - (0.72 + audio * 0.1)) * 22.0) * 0.12;
                    color += vec3(0.08, 0.55, 0.95) * halo;
                }
                gl_FragColor = vec4(color, 1.0);
            }
        """
    }
}
