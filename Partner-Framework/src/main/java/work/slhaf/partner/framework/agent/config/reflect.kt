package work.slhaf.partner.framework.agent.config

import net.bytebuddy.jar.asm.*
import java.lang.Byte
import java.lang.Double
import java.lang.Float
import java.lang.Long
import java.lang.Short
import java.lang.reflect.Field
import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Metadata
import kotlin.String
import kotlin.reflect.KProperty1

internal fun Class<*>.isKotlinClass(): Boolean {
    return getAnnotation(Metadata::class.java) != null
}

internal fun resolveDisplayType(type: Class<*>): String {
    if (type.isArray) {
        return "${resolveDisplayType(type.componentType)}[]"
    }
    return when (type) {
        Integer.TYPE, Integer::class.java -> "Int"
        Long.TYPE, Long::class.java -> "Long"
        java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> "Boolean"
        Double.TYPE, Double::class.java -> "Double"
        Float.TYPE, Float::class.java -> "Float"
        Short.TYPE, Short::class.java -> "Short"
        Byte.TYPE, Byte::class.java -> "Byte"
        Character.TYPE, Character::class.java -> "Char"
        String::class.java -> "String"
        else -> type.simpleName
    }
}

internal fun resolveNullableInfo(
    ownerType: Class<out Config>,
    field: Field,
    kotlinProperty: KProperty1<out Any, *>?
): NullableInfo {
    if (ownerType.isKotlinClass()) {
        if (kotlinProperty != null) {
            return NullableInfo(kotlinProperty.returnType.isMarkedNullable, null)
        }
        return NullableInfo(false, "inferred because Kotlin property metadata was not found")
    }
    val annotationNames = resolveJavaFieldAnnotationNames(ownerType, field)
    if (annotationNames.any { it.endsWith(".Nullable") || it == "Nullable" }) {
        return NullableInfo(true, null)
    }
    if (annotationNames.any { it.endsWith(".NotNull") || it == "NotNull" }) {
        return NullableInfo(false, null)
    }
    return NullableInfo(false, "inferred from missing nullability annotation, may be unreliable")
}

private fun resolveJavaFieldAnnotationNames(ownerType: Class<out Config>, field: Field): Set<String> {
    val annotationNames = linkedSetOf<String>()
    annotationNames += (field.annotations.asSequence() + field.annotatedType.annotations.asSequence())
        .map { it.annotationClass.java.name }
        .toSet()
    val resourcePath = "${ownerType.name.replace('.', '/')}.class"
    val classStream = (ownerType.classLoader?.getResourceAsStream(resourcePath)
        ?: ClassLoader.getSystemResourceAsStream(resourcePath))
        ?: return annotationNames
    classStream.use { input ->
        ClassReader(input).accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visitField(
                access: Int,
                name: String,
                descriptor: String?,
                signature: String?,
                value: Any?
            ): FieldVisitor? {
                if (name != field.name) {
                    return null
                }
                return object : FieldVisitor(Opcodes.ASM9) {
                    override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                        annotationNames += Type.getType(descriptor).className
                        return null
                    }

                    override fun visitTypeAnnotation(
                        typeRef: Int,
                        typePath: TypePath?,
                        descriptor: String,
                        visible: Boolean
                    ): AnnotationVisitor? {
                        annotationNames += Type.getType(descriptor).className
                        return null
                    }
                }
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }
    return annotationNames
}

internal data class NullableInfo(
    val nullable: Boolean,
    val note: String?
)

