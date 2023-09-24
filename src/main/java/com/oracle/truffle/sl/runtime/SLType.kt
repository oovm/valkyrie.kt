package com.oracle.truffle.sl.runtime

import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.sl.runtime.SLType.TypeCheck
import valkyrie.language.ValkyrieLanguage

/**
 * The builtin type definitions for SimpleLanguage. SL has no custom types, so it is not possible
 * for a guest program to create new instances of SLType.
 *
 *
 * The isInstance type checks are declared using an functional interface and are expressed using the
 * interoperability libraries. The advantage of this is type checks automatically work for foreign
 * values or primitive values like byte or short.
 *
 *
 * The class implements the interop contracts for [InteropLibrary.isMetaObject] and
 * [InteropLibrary.isMetaInstance]. The latter allows other languages and
 * tools to perform type checks using types of simple language.
 *
 *
 * In order to assign types to guest language values, SL values implement
 * [InteropLibrary.getMetaObject]. The interop contracts for primitive values cannot
 * be overriden, so in order to assign meta-objects to primitive values, the primitive values are
 * assigned using language views. See [ValkyrieLanguage.getLanguageView].
 */
@ExportLibrary(InteropLibrary::class)
class SLType /*
     * We don't allow dynamic instances of SLType. Real languages might want to expose this for
     * types that are user defined.
     */ private constructor(private val name: String, private val isInstance: TypeCheck) : TruffleObject {
    /**
     * Checks whether this type is of a certain instance. If used on fast-paths it is required to
     * cast [SLType] to a constant.
     */
    fun isInstance(value: Any?, interop: InteropLibrary): Boolean {
        CompilerAsserts.partialEvaluationConstant<Any>(this)
        return isInstance.check(interop, value)
    }

    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }

    @get:ExportMessage
    val language: Class<out TruffleLanguage<*>?>
        get() = ValkyrieLanguage::class.java

    @get:ExportMessage
    val isMetaObject: Boolean
        /*
     * All SLTypes are declared as interop meta-objects. Other example for meta-objects are Java
     * classes, or JavaScript prototypes.
     */ get() = true

    /*
     * SL does not have the notion of a qualified or simple name, so we return the same type name
     * for both.
     */
    @ExportMessage(name = "getMetaQualifiedName")
    @ExportMessage(name = "getMetaSimpleName")
    fun getName(): Any {
        return name
    }

    @ExportMessage(name = "toDisplayString")
    fun toDisplayString(@Suppress("unused") allowSideEffects: Boolean): Any {
        return name
    }

    override fun toString(): String {
        return "SLType[$name]"
    }

    /*
     * The interop message isMetaInstance might be used from other languages or by the {@link
     * SLIsInstanceBuiltin isInstance} builtin. It checks whether a given value, which might be a
     * primitive, foreign or SL value is of a given SL type. This allows other languages to make
     * their instanceOf interopable with foreign values.
     */
    @ExportMessage
    internal object IsMetaInstance {
        /*
         * We assume that the same type is checked at a source location. Therefore we use an inline
         * cache to specialize for observed types to be constant. The limit of "3" specifies that we
         * specialize for 3 different types until we rewrite to the doGeneric case. The limit in
         * this example is somewhat arbitrary and should be determined using careful tuning with
         * real world benchmarks.
         */
        @JvmStatic
        @Specialization(guards = ["type == cachedType"], limit = "3")
        fun doCached(
            @Suppress("unused") type: SLType?, value: Any?,
            @Cached("type") cachedType: SLType,
            @CachedLibrary("value") valueLib: InteropLibrary,
        ): Boolean {
            return cachedType.isInstance.check(valueLib, value)
        }

        @JvmStatic
        @CompilerDirectives.TruffleBoundary
        @Specialization(replaces = ["doCached"])
        fun doGeneric(type: SLType, value: Any?): Boolean {
            return type.isInstance.check(InteropLibrary.getFactory().getUncached(), value)
        }
    }

    /*
     * A convenience interface for type checks. Alternatively this could have been solved using
     * subtypes of SLType.
     */
    internal fun interface TypeCheck {
        fun check(lib: InteropLibrary, value: Any?): Boolean
    }

    companion object {
        /*
     * These are the sets of builtin types in simple languages. In case of simple language the types
     * nicely match those of the types in InteropLibrary. This might not be the case and more
     * additional checks need to be performed (similar to number checking for SLBigNumber).
     */
        @JvmField
        val NUMBER: SLType =
            SLType("Number", TypeCheck { l: InteropLibrary, v: Any? -> l.fitsInLong(v) || v is ValkyrieInteger })
        val NULL: SLType = SLType("NULL", TypeCheck { l: InteropLibrary, v: Any? -> l.isNull(v) })

        @JvmField
        val STRING: SLType = SLType("String", TypeCheck { l: InteropLibrary, v: Any? -> l.isString(v) })

        @JvmField
        val BOOLEAN: SLType = SLType("Boolean", TypeCheck { l: InteropLibrary, v: Any? -> l.isBoolean(v) })

        @JvmField
        val OBJECT: SLType = SLType("Object", TypeCheck { l: InteropLibrary, v: Any? -> l.hasMembers(v) })

        @JvmField
        val FUNCTION: SLType = SLType("Function", TypeCheck { l: InteropLibrary, v: Any? -> l.isExecutable(v) })

        /*
     * This array is used when all types need to be checked in a certain order. While most interop
     * types like number or string are exclusive, others traits like members might not be. For
     * example, an object might be a function. In SimpleLanguage we decided to make functions,
     * functions and not objects.
     */
        @JvmField
        @CompilerDirectives.CompilationFinal(dimensions = 1)
        val PRECEDENCE: Array<SLType> = arrayOf(NULL, NUMBER, STRING, BOOLEAN, FUNCTION, OBJECT)
    }
}
