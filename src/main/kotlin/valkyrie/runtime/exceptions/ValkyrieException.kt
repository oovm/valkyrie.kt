package valkyrie.runtime.exceptions

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.exception.AbstractTruffleException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.nodes.Node
import valkyrie.language.ValkyrieLanguage.Companion.lookupNodeInfo
import valkyrie.runtime.SLLanguageView.Companion.forValue

/**
 * SL does not need a sophisticated error checking and reporting mechanism, so all unexpected
 * conditions just abort execution. This exception class is used when we abort from within the SL
 * implementation.
 */
open class ValkyrieException @CompilerDirectives.TruffleBoundary constructor(message: String?, location: Node?) :
    AbstractTruffleException(message, location) {
    companion object {
        private const val serialVersionUID = -6799734410727348507L
        private val UNCACHED_LIB: InteropLibrary = InteropLibrary.getFactory().getUncached()

        /**
         * Provides a user-readable message for run-time type errors. SL is strongly typed, i.e., there
         * are no automatic type conversions of values.
         */
        @JvmStatic
        @CompilerDirectives.TruffleBoundary
        fun typeError(operation: Node?, vararg values: Any?): ValkyrieException {
            val result = StringBuilder()
            result.append("Type error")

            if (operation != null) {
                val ss = operation.getEncapsulatingSourceSection()
                if (ss != null && ss.isAvailable) {
                    result.append(" at ").append(ss.source.name).append(" line ").append(ss.startLine).append(" col ")
                        .append(ss.startColumn)
                }
            }

            result.append(": operation")
            if (operation != null) {
                val nodeInfo = lookupNodeInfo(operation.javaClass)
                if (nodeInfo != null) {
                    result.append(" \"").append(nodeInfo.shortName).append("\"")
                }
            }

            result.append(" not defined for")

            var sep = " "
            for (i in values.indices) {
                /*
             * For primitive or foreign values we request a language view so the values are printed
             * from the perspective of simple language and not another language. Since this is a
             * rather rarely invoked exceptional method, we can just create the language view for
             * primitive values and then conveniently request the meta-object and display strings.
             * Using the language view for core builtins like the typeOf builtin might not be a good
             * idea for performance reasons.
             */
                val value = forValue(values[i])
                result.append(sep)
                sep = ", "
                if (value == null) {
                    result.append("ANY")
                } else {
                    val valueLib = InteropLibrary.getFactory().getUncached(value)
                    if (valueLib.hasMetaObject(value) && !valueLib.isNull(value)) {
                        var qualifiedName: String?
                        try {
                            qualifiedName =
                                UNCACHED_LIB.asString(UNCACHED_LIB.getMetaQualifiedName(valueLib.getMetaObject(value)))
                        } catch (e: UnsupportedMessageException) {
                            throw CompilerDirectives.shouldNotReachHere(e)
                        }
                        result.append(qualifiedName)
                        result.append(" ")
                    }
                    if (valueLib.isString(value)) {
                        result.append("\"")
                    }
                    result.append(valueLib.toDisplayString(value))
                    if (valueLib.isString(value)) {
                        result.append("\"")
                    }
                }
            }
            return ValkyrieException(result.toString(), operation)
        }
    }
}
