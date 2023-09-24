package valkyrie.runtime.functions

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.Bind
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.profiles.InlinedBranchProfile
import com.oracle.truffle.api.strings.TruffleString
import valkyrie.language.ValkyrieLanguage
import valkyrie.runtime.SLType
import valkyrie.runtime.ValkyrieString.fromJavaString

@ExportLibrary(InteropLibrary::class)
internal class FunctionsObject : TruffleObject {
    @JvmField
    val functions: MutableMap<TruffleString, ValkyrieFunction> = HashMap()

    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }

    @get:ExportMessage
    val language: Class<out TruffleLanguage<*>?>
        get() = ValkyrieLanguage::class.java

    @ExportMessage
    fun hasMembers(): Boolean {
        return true
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @Throws(
        UnknownIdentifierException::class
    )
    fun readMember(member: String?): Any {
        val value: Any? = functions[fromJavaString(member)]
        if (value != null) {
            return value
        }
        throw UnknownIdentifierException.create(member)
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun isMemberReadable(member: String?): Boolean {
        return functions.containsKey(fromJavaString(member))
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun getMembers(@Suppress("unused") includeInternal: Boolean): Any {
        return FunctionNamesObject(functions.keys.toTypedArray())
    }

    @ExportMessage
    fun hasMetaObject(): Boolean {
        return true
    }

    @get:ExportMessage
    val metaObject: Any
        get() = SLType.OBJECT

    @get:ExportMessage
    val isScope: Boolean
        get() = true

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    fun toDisplayString(@Suppress("unused") allowSideEffects: Boolean): Any {
        return "global"
    }

    @ExportLibrary(InteropLibrary::class)
    internal class FunctionNamesObject(private val names: Array<Any>) : TruffleObject {
        @ExportMessage
        fun hasArrayElements(): Boolean {
            return true
        }

        @ExportMessage
        fun isArrayElementReadable(index: Long): Boolean {
            return index >= 0 && index < names.size
        }

        @get:ExportMessage
        val arraySize: Long
            get() = names.size.toLong()

        @ExportMessage
        @Throws(InvalidArrayIndexException::class)
        fun readArrayElement(index: Long, @Bind("\$node") node: Node?, @Cached error: InlinedBranchProfile): Any {
            if (!isArrayElementReadable(index)) {
                error.enter(node)
                throw InvalidArrayIndexException.create(index)
            }
            return names[index.toInt()]
        }
    }

    companion object {
        fun isInstance(obj: TruffleObject?): Boolean {
            return obj is FunctionsObject
        }
    }
}
