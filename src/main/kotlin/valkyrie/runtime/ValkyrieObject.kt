package valkyrie.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.CachedLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.`object`.DynamicObjectLibrary
import com.oracle.truffle.api.`object`.Shape

@ExportLibrary(InteropLibrary::class)
class ValkyrieObject(shape: Shape) : ValkyrieValue(shape) {

    @ExportMessage
    fun hasMembers(): Boolean {
        return true
    }

    @ExportMessage
    @Throws(UnknownIdentifierException::class)
    fun readMember(
        name: String?,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ): Any {
        val result = objectLibrary.getOrDefault(this, name, null)
            ?: /* Property does not exist. */
            throw UnknownIdentifierException.create(name)
        return result
    }

    @ExportMessage
    fun writeMember(
        name: String?, value: Any?,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ) {
        objectLibrary.put(this, name, value)
    }

    @ExportMessage
    fun isMemberReadable(
        member: String?,
        @CachedLibrary("this") objectLibrary: DynamicObjectLibrary,
    ): Boolean {
        return objectLibrary.containsKey(this, member)
    }
}