package valkyrie.runtime

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.`object`.Shape

@ExportLibrary(InteropLibrary::class)
class ValkyrieList(shape: Shape, private val elements: MutableList<Any>) : ValkyrieValue(shape) {
    @ExportMessage
    fun hasArrayElements(): Boolean {
        return true
    }


    @get:ExportMessage
    val length: Long
        get() = elements.size.toLong()
}

