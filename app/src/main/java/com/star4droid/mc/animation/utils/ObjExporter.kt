package com.star4droid.mc.animation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.star4droid.mc.animation.character.CharacterFactory
import com.star4droid.mc.animation.engine.scene.SceneNode
import java.io.OutputStreamWriter

object ObjExporter {

    fun generateSteveObjAndMtl(): Pair<String, String> {
        val nodes = CharacterFactory.createCharacter("Steve")
        val objSb = StringBuilder()
        val mtlSb = StringBuilder()

        objSb.append("# Minecraft Steve 3D Model\n")
        objSb.append("mtllib steve.mtl\n\n")

        mtlSb.append("# Material for Steve\n")
        mtlSb.append("newmtl Steve_Material\n")
        mtlSb.append("Kd 1.0 1.0 1.0\n")
        mtlSb.append("map_Kd steve_skin.png\n\n")

        var vertexOffset = 1
        var uvOffset = 1
        var normalOffset = 1

        for (node in nodes) {
            val name = node.name.replace(" ", "_")
            objSb.append("g $name\n")
            objSb.append("usemtl Steve_Material\n")

            val pos = node.baseTransform.position
            val dim = node.boxDimensions
            val hx = dim.x * 0.5f
            val hy = dim.y * 0.5f
            val hz = dim.z * 0.5f

            // 8 vertices for cube box
            val verts = listOf(
                listOf(pos.x - hx, pos.y - hy, pos.z + hz),
                listOf(pos.x + hx, pos.y - hy, pos.z + hz),
                listOf(pos.x + hx, pos.y + hy, pos.z + hz),
                listOf(pos.x - hx, pos.y + hy, pos.z + hz),
                listOf(pos.x + hx, pos.y - hy, pos.z - hz),
                listOf(pos.x - hx, pos.y - hy, pos.z - hz),
                listOf(pos.x - hx, pos.y + hy, pos.z - hz),
                listOf(pos.x + hx, pos.y + hy, pos.z - hz)
            )

            for (v in verts) {
                objSb.append(String.format(java.util.Locale.US, "v %.4f %.4f %.4f\n", v[0], v[1], v[2]))
            }

            // Normals
            val normals = listOf(
                "0.0 0.0 1.0", "0.0 0.0 -1.0", "0.0 1.0 0.0",
                "0.0 -1.0 0.0", "1.0 0.0 0.0", "-1.0 0.0 0.0"
            )
            for (n in normals) {
                objSb.append("vn $n\n")
            }

            // Basic UV mapping
            val uvs = listOf(
                "0.0 1.0", "1.0 1.0", "1.0 0.0", "0.0 0.0"
            )
            for (uv in uvs) {
                objSb.append("vt $uv\n")
            }

            // Faces
            val vo = vertexOffset
            val uo = uvOffset
            val no = normalOffset

            // Front, Back, Top, Bottom, Right, Left
            objSb.append("f ${vo}/$uo/${no} ${vo+1}/${uo+1}/${no} ${vo+2}/${uo+2}/${no} ${vo+3}/${uo+3}/${no}\n")
            objSb.append("f ${vo+4}/${uo}/${no+1} ${vo+5}/${uo+1}/${no+1} ${vo+6}/${uo+2}/${no+1} ${vo+7}/${uo+3}/${no+1}\n")
            objSb.append("f ${vo+3}/${uo}/${no+2} ${vo+2}/${uo+1}/${no+2} ${vo+7}/${uo+2}/${no+2} ${vo+6}/${uo+3}/${no+2}\n")
            objSb.append("f ${vo+5}/${uo}/${no+3} ${vo+4}/${uo+1}/${no+3} ${vo+1}/${uo+2}/${no+3} ${vo}/${uo+3}/${no+3}\n")
            objSb.append("f ${vo+1}/${uo}/${no+4} ${vo+4}/${uo+1}/${no+4} ${vo+7}/${uo+2}/${no+4} ${vo+2}/${uo+3}/${no+4}\n")
            objSb.append("f ${vo+5}/${uo}/${no+5} ${vo}/${uo+1}/${no+5} ${vo+3}/${uo+2}/${no+5} ${vo+6}/${uo+3}/${no+5}\n")

            vertexOffset += 8
            uvOffset += 4
            normalOffset += 6
        }

        return Pair(objSb.toString(), mtlSb.toString())
    }

    fun exportToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(content)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
