package valkyrie.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.`object`.DynamicObject
import com.oracle.truffle.api.`object`.Shape


/** Runtime value type of valkyrie */
@ExportLibrary(InteropLibrary::class)
open class ValkyrieValue(shape: Shape) : DynamicObject(shape), TruffleObject {
    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }
}