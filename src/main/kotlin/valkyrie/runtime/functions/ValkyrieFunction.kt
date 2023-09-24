package valkyrie.runtime.functions

import com.oracle.truffle.api.*
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.Fallback
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.IndirectCallNode
import com.oracle.truffle.api.source.SourceSection
import com.oracle.truffle.api.strings.TruffleString
import com.oracle.truffle.api.utilities.CyclicAssumption
import com.oracle.truffle.api.utilities.TriState
import valkyrie.language.ValkyrieLanguage
import valkyrie.runtime.SLType
import java.util.logging.Level

/**
 * Represents a SL function. On the Truffle level, a callable element is represented by a
 * [call target][RootCallTarget]. This class encapsulates a call target, and adds version
 * support: functions in SL can be redefined, i.e. changed at run time. When a function is
 * redefined, the call target managed by this function object is changed (and [.callTarget] is
 * therefore not a final field).
 *
 *
 * Function redefinition is expected to be rare, therefore optimized call nodes want to speculate
 * that the call target is stable. This is possible with the help of a Truffle [Assumption]: a
 * call node can keep the call target returned by [.getCallTarget] cached until the
 * assumption returned by [.getCallTargetStable] is valid.
 *
 *
 * The [.callTarget] can be `null`. To ensure that only one [ValkyrieFunction] instance
 * per name exists, the [ValkyrieFunctionRegistry] creates an instance also when performing name
 * lookup. A function that has been looked up, i.e., used, but not defined, has a call target that
 * encapsulates a [SLUndefinedFunctionRootNode].
 */
@ExportLibrary(InteropLibrary::class)
class ValkyrieFunction(@JvmField val name: TruffleString, callTarget: RootCallTarget?) : TruffleObject {
    /**
     * The current implementation of this function.
     */
    var callTarget: RootCallTarget? = null
        set(callTarget) {
            val wasNull = this.callTarget == null
            field = callTarget
            /*
         * We have a new call target. Invalidate all code that speculated that the old call target
         * was stable.
         */LOG.log(Level.FINE, "Installed call target for: {0}", name)
            if (!wasNull) {
                callTargetStable.invalidate()
            }
        }

    /**
     * Manages the assumption that the [.callTarget] is stable. We use the utility class
     * [CyclicAssumption], which automatically creates a new [Assumption] when the old
     * one gets invalidated.
     */
    private val callTargetStable = CyclicAssumption(name.toJavaStringUncached())

    constructor(language: ValkyrieLanguage, name: TruffleString) : this(
        name,
        language.getOrCreateUndefinedFunction(name)
    )

    init {
        this.callTarget = callTarget
    }

    fun getCallTargetStable(): Assumption {
        return callTargetStable.getAssumption()
    }

    /**
     * This method is, e.g., called when using a function literal in a string concatenation. So
     * changing it has an effect on SL programs.
     */
    override fun toString(): String {
        return name.toJavaStringUncached()
    }

    @ExportMessage
    fun hasLanguage(): Boolean {
        return true
    }

    @get:ExportMessage
    val language: Class<out TruffleLanguage<*>?>
        get() = ValkyrieLanguage::class.java

    @get:CompilerDirectives.TruffleBoundary
    @get:ExportMessage
    val sourceLocation: SourceSection
        /**
         * [ValkyrieFunction] instances are always visible as executable to other languages.
         */
        get() = callTarget!!.rootNode.sourceSection

    @ExportMessage
    fun hasSourceLocation(): Boolean {
        return true
    }

    @get:ExportMessage
    val isExecutable: Boolean
        /**
         * [ValkyrieFunction] instances are always visible as executable to other languages.
         */
        get() = true

    @ExportMessage
    fun hasMetaObject(): Boolean {
        return true
    }

    @get:ExportMessage
    val metaObject: Any
        get() = SLType.FUNCTION

    @ExportMessage
    @Suppress("unused")
    internal object IsIdenticalOrUndefined {
        @JvmStatic
        @Specialization
        fun doSLFunction(receiver: ValkyrieFunction, other: ValkyrieFunction): TriState {
            /*
             * SLFunctions are potentially identical to other SLFunctions.
             */
            return if (receiver == other) TriState.TRUE else TriState.FALSE
        }

        @JvmStatic
        @Fallback
        fun doOther(receiver: ValkyrieFunction?, other: Any?): TriState {
            return TriState.UNDEFINED
        }
    }

    @ExportMessage
    fun toDisplayString(@Suppress("unused") allowSideEffects: Boolean): Any {
        return name
    }

    /**
     * We allow languages to execute this function. We implement the interop execute message that
     * forwards to a function dispatch.
     *
     *
     * Since invocations are potentially expensive (result in an indirect call, which is expensive
     * by itself but also limits function inlining which can hinder other optimisations) if the node
     * turns megamorphic (i.e. cache limit is exceeded) we annotate it with {@ReportPolymorphism}.
     * This ensures that the runtime is notified when this node turns polymorphic. This, in turn,
     * may, under certain conditions, cause the runtime to attempt to make node monomorphic again by
     * duplicating the entire AST containing that node and specialising it for a particular call
     * site.
     */
    @ReportPolymorphism
    @ExportMessage
    internal object Execute {
        /**
         * Inline cached specialization of the dispatch.
         *
         *
         *
         * Since SL is a quite simple language, the benefit of the inline cache seems small: after
         * checking that the actual function to be executed is the same as the cachedFuntion, we can
         * safely execute the cached call target. You can reasonably argue that caching the call
         * target is overkill, since we could just retrieve it via `function.getCallTarget()`.
         * However, caching the call target and using a [DirectCallNode] allows Truffle to
         * perform method inlining. In addition, in a more complex language the lookup of the call
         * target is usually much more complicated than in SL.
         *
         *
         *
         *
         * `limit = "INLINE_CACHE_SIZE"` Specifies the limit number of inline cache
         * specialization instantiations.
         *
         *
         *
         * `guards = "function.getCallTarget() == cachedTarget"` The inline cache check. Note
         * that cachedTarget is a final field so that the compiler can optimize the check.
         *
         *
         *
         * `assumptions = "callTargetStable"` Support for function redefinition: When a
         * function is redefined, the call target maintained by the SLFunction object is changed. To
         * avoid a check for that, we use an Assumption that is invalidated by the SLFunction when
         * the change is performed. Since checking an assumption is a no-op in compiled code, the
         * assumption check performed by the DSL does not add any overhead during optimized
         * execution.
         *
         *
         * @param function         the dynamically provided function
         * @param arguments        the arguments to the function
         * @param callTargetStable The assumption object assuming the function was not redefined.
         * @param cachedTarget     The call target we aim to invoke
         * @param callNode         the [DirectCallNode] specifically created for the
         * [CallTarget] in cachedFunction.
         * @see Cached
         *
         * @see Specialization
         */
        @JvmStatic
        @Specialization(
            limit = "INLINE_CACHE_SIZE",
            guards = ["function.getCallTarget() == cachedTarget"],
            assumptions = ["callTargetStable"]
        )
        @Suppress("unused")
        fun doDirect(
            function: ValkyrieFunction?, arguments: Array<Any?>,
            @Cached("function.getCallTargetStable()") callTargetStable: Assumption?,
            @Cached("function.getCallTarget()") cachedTarget: RootCallTarget?,
            @Cached("create(cachedTarget)") callNode: DirectCallNode,
        ): Any {
            /* Inline cache hit, we are safe to execute the cached call target. */

            val returnValue = callNode.call(*arguments)
            return returnValue
        }

        /**
         * Slow-path code for a call, used when the polymorphic inline cache exceeded its maximum
         * size specified in `INLINE_CACHE_SIZE`. Such calls are not optimized any
         * further, e.g., no method inlining is performed.
         */
        @JvmStatic
        @Specialization(replaces = ["doDirect"])
        fun doIndirect(
            function: ValkyrieFunction, arguments: Array<Any?>,
            @Cached callNode: IndirectCallNode,
        ): Any {
            /*
             * SL has a quite simple call lookup: just ask the function for the current call target,
             * and call it.
             */
            return callNode.call(function.callTarget, *arguments)
        }
    }

    companion object {
        const val INLINE_CACHE_SIZE: Int = 2

        private val LOG: TruffleLogger = TruffleLogger.getLogger(ValkyrieLanguage.ID, ValkyrieFunction::class.java)

        @JvmStatic
        @ExportMessage
        @CompilerDirectives.TruffleBoundary
        fun identityHashCode(receiver: ValkyrieFunction?): Int {
            return System.identityHashCode(receiver)
        }
    }
}
