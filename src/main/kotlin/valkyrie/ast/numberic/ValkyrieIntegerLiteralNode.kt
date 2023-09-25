package valkyrie.ast.numberic

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.sl.nodes.SLExpressionNode
import valkyrie.runtime.numbers.ValkyrieInteger
import java.math.BigInteger

/**
 * Constant literal for a arbitrary-precision number that exceeds the range of
 * [SLLongLiteralNode].
 */
@NodeInfo(shortName = "const")
class ValkyrieIntegerLiteralNode(value: BigInteger?) : SLExpressionNode() {
    private val value = ValkyrieInteger(value!!)

    override fun executeGeneric(frame: VirtualFrame): ValkyrieInteger {
        return value
    }
}

