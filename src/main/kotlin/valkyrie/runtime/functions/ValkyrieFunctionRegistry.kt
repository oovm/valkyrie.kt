package valkyrie.runtime.functions

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.strings.TruffleString
import com.oracle.truffle.sl.parser.SimpleLanguageParser
import valkyrie.language.ValkyrieLanguage
import java.util.*

/**
 * Manages the mapping from function names to [function objects][ValkyrieFunction].
 */
class ValkyrieFunctionRegistry(private val language: ValkyrieLanguage) {
    internal val functionsObject = FunctionsObject()
    private val registeredFunctions: MutableMap<Map<TruffleString, RootCallTarget>, Void?> = IdentityHashMap()

    /**
     * Returns the canonical [ValkyrieFunction] object for the given name. If it does not exist yet,
     * it is created.
     */
    @CompilerDirectives.TruffleBoundary
    fun lookup(name: TruffleString, createIfNotPresent: Boolean): ValkyrieFunction? {
        var result = functionsObject.functions[name]
        if (result == null && createIfNotPresent) {
            result = ValkyrieFunction(language, name)
            functionsObject.functions[name] = result;
        }
        return result
    }

    /**
     * Associates the [ValkyrieFunction] with the given name with the given implementation root
     * node. If the function did not exist before, it defines the function. If the function existed
     * before, it redefines the function and the old implementation is discarded.
     */
    fun register(name: TruffleString, callTarget: RootCallTarget?): ValkyrieFunction {
        var result = functionsObject.functions[name]
        if (result == null) {
            result = ValkyrieFunction(name, callTarget)
            functionsObject.functions[name] = result
        } else {
            result.callTarget = callTarget
        }
        return result
    }

    /**
     * Registers a map of functions. The once registered map must not change in order to allow to
     * cache the registration for the entire map. If the map is changed after registration the
     * functions might not get registered.
     */
    @CompilerDirectives.TruffleBoundary
    fun register(newFunctions: Map<TruffleString, RootCallTarget>) {
        if (registeredFunctions.containsKey(newFunctions)) {
            return
        }
        for ((key, value) in newFunctions) {
            register(key, value)
        }
        registeredFunctions[newFunctions] = null
    }

    fun register(newFunctions: Source?) {
        register(SimpleLanguageParser.parseSL(language, newFunctions))
    }

    fun getFunction(name: TruffleString?): ValkyrieFunction? {
        return functionsObject.functions[name]
    }

    val functions: List<ValkyrieFunction>
        /**
         * Returns the sorted list of all functions, for printing purposes only.
         */
        get() {
            val result: List<ValkyrieFunction> = ArrayList(functionsObject.functions.values)
            Collections.sort(result) { f1, f2 ->
                assert(ValkyrieLanguage.STRING_ENCODING == TruffleString.Encoding.UTF_16) { "SLLanguage.ENCODING changed, string comparison method must be adjusted accordingly!" }
                f1.name.compareCharsUTF16Uncached(f2.name)
            }
            return result
        }

    fun getFunctionsObject(): TruffleObject {
        return functionsObject
    }
}
