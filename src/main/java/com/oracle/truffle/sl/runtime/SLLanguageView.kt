package com.oracle.truffle.sl.runtime

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop
import valkyrie.language.SLLanguage

/**
 * Language views are needed in order to allow tools to have a consistent perspective on primitive
 * or foreign values from the perspective of this language. The interop interpretation for primitive
 * values like Integer or String is not language specific by default. Therefore this language view
 * calls routines to print such values the SimpleLanguage way. It is important to note that language
 * views are not passed as normal values through the interpreter execution. It is designed only as a
 * temporary helper for tools.
 *
 *
 * There is more information in [TruffleLanguage.getLanguageView]
 */
@ExportLibrary(value = InteropLibrary::class, delegateTo = "delegate")
class SLLanguageView internal constructor(@JvmField val delegate: Any) : TruffleObject {
    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }

    @get:ExportMessage
    val language: Class<out TruffleLanguage<*>?>
        /*
     * Language views must always associate with the language they were created for. This allows
     * tooling to take a primitive or foreign value and create a value of simple language of it.
     */ get() = SLLanguage::class.java

    @ExportMessage
    @ExplodeLoop
    fun hasMetaObject(@CachedLibrary("this.delegate") interop: InteropLibrary?): Boolean {
        /*
         * We use the isInstance method to find out whether one of the builtin simple language types
         * apply. If yes, then we can provide a meta object in getMetaObject. The interop contract
         * requires to be precise.
         *
         * Since language views are only created for primitive values and values of other languages,
         * values from simple language itself directly implement has/getMetaObject. For example
         * SLFunction is already associated with the SLLanguage and therefore the language view will
         * not be used.
         */
        for (type in SLType.PRECEDENCE) {
            if (type.isInstance(delegate, interop!!)) {
                return true
            }
        }
        return false
    }

    @ExportMessage
    @ExplodeLoop
    @Throws(UnsupportedMessageException::class)
    fun getMetaObject(@CachedLibrary("this.delegate") interop: InteropLibrary?): Any {
        /*
         * We do the same as in hasMetaObject but actually return the type this time.
         */
        for (type in SLType.PRECEDENCE) {
            if (type.isInstance(delegate, interop!!)) {
                return type
            }
        }
        throw UnsupportedMessageException.create()
    }

    @ExportMessage
    @ExplodeLoop
    fun toDisplayString(
        @Suppress("unused") allowSideEffects: Boolean,
        @CachedLibrary("this.delegate") interop: InteropLibrary,
    ): Any {
        for (type in SLType.PRECEDENCE) {
            if (type.isInstance(delegate, interop)) {
                try {
                    /*
                     * The type is a partial evaluation constant here as we use @ExplodeLoop. So
                     * this if-else cascade should fold after partial evaluation.
                     */
                    return if (type == SLType.NUMBER) {
                        longToString(interop.asLong(delegate))
                    } else if (type == SLType.BOOLEAN) {
                        interop.asBoolean(delegate).toString()
                    } else if (type == SLType.STRING) {
                        interop.asString(delegate)
                    } else {
                        /* We use the type name as fallback for any other type */
                        type.getName()
                    }
                } catch (e: UnsupportedMessageException) {
                    throw CompilerDirectives.shouldNotReachHere(e)
                }
            }
        }
        return "Unsupported"
    }

    companion object {
        /*
     * Long.toString is not safe for partial evaluation and therefore needs to be called behind a
     * boundary.
     */
        @CompilerDirectives.TruffleBoundary
        private fun longToString(l: Long): String {
            return l.toString()
        }

        fun create(value: Any): Any {
            assert(isPrimitiveOrFromOtherLanguage(value))
            return SLLanguageView(value)
        }

        /*
     * Language views are intended to be used only for primitives and other language values.
     */
        private fun isPrimitiveOrFromOtherLanguage(value: Any): Boolean {
            val interop = InteropLibrary.getFactory().getUncached(value)
            try {
                return !interop.hasLanguage(value) || interop.getLanguage(value) != SLLanguage::class.java
            } catch (e: UnsupportedMessageException) {
                throw CompilerDirectives.shouldNotReachHere(e)
            }
        }

        /**
         * Returns a language view for primitive or foreign values. Returns the same value for values
         * that are already originating from SimpleLanguage. This is useful to view values from the
         * perspective of simple language in slow paths, for example, printing values in error messages.
         */
        @JvmStatic
        @CompilerDirectives.TruffleBoundary
        fun forValue(value: Any?): Any? {
            if (value == null) {
                return null
            }
            val lib = InteropLibrary.getFactory().getUncached(value)
            try {
                return if (lib.hasLanguage(value) && lib.getLanguage(value) == SLLanguage::class.java) {
                    value
                } else {
                    create(value)
                }
            } catch (e: UnsupportedMessageException) {
                throw CompilerDirectives.shouldNotReachHere(e)
            }
        }
    }
}
