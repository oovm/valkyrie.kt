package valkyrie.runtime

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.utilities.TriState
import valkyrie.language.ValkyrieLanguage

/**
 * The SL type for a `null` (i.e., undefined) value. In Truffle, it is generally discouraged
 * to use the Java `null` value to represent the guest language `null` value. It is not
 * possible to specialize on Java `null` (since you cannot ask it for the Java class), and
 * there is always the danger of a spurious [NullPointerException]. Representing the guest
 * language `null` as a singleton, as in [this class][.SINGLETON], is the recommended
 * practice.
 */
@ExportLibrary(InteropLibrary::class)
class ValkyrieNull
/**
 * Disallow instantiation from outside to ensure that the [.SINGLETON] is the only
 * instance.
 */
private constructor() : TruffleObject {
    /**
     * This method is, e.g., called when using the `null` value in a string concatenation. So
     * changing it has an effect on SL programs.
     */
    override fun toString(): String {
        return "null"
    }

    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }

    @get:ExportMessage
    val language: Class<out TruffleLanguage<*>?>
        get() = ValkyrieLanguage::class.java

    @get:ExportMessage
    val isNull: Boolean
        /**
         * [ValkyrieNull] values are interpreted as null values by other languages.
         */
        get() = true

    @ExportMessage
    fun hasMetaObject(): Boolean {
        return true
    }

    @get:ExportMessage
    val metaObject: Any
        get() = SLType.NULL

    @ExportMessage
    fun toDisplayString(@Suppress("unused") allowSideEffects: Boolean): Any {
        return "NULL"
    }

    companion object {
        /**
         * The canonical value to represent `null` in SL.
         */
        @JvmField
        val SINGLETON: ValkyrieNull = ValkyrieNull()
        private val IDENTITY_HASH = System.identityHashCode(SINGLETON)

        @JvmStatic
        @ExportMessage
        fun isIdenticalOrUndefined(@Suppress("unused") receiver: ValkyrieNull?, other: Any): TriState {/*
         * SLNull values are identical to other SLNull values.
         */
            return TriState.valueOf(SINGLETON == other)
        }

        @JvmStatic
        @ExportMessage
        fun identityHashCode(@Suppress("unused") receiver: ValkyrieNull?): Int {/*
         * We do not use 0, as we want consistency with System.identityHashCode(receiver).
         */
            return IDENTITY_HASH
        }
    }
}
