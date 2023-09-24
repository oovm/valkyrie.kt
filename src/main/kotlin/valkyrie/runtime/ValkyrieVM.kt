package valkyrie.runtime

import com.oracle.truffle.api.`object`.Shape

@Suppress("FunctionName", "PrivatePropertyName")
class ValkyrieVM {
    private val initialObjectShape: Shape? = null
    private val _cache_list = Shape.newBuilder().build()

    fun make_list(elements: MutableList<Any>): ValkyrieList {
        return ValkyrieList(_cache_list, elements)
    }
}

