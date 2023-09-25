
package valkyrie.test.numberic

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import valkyrie.language.ValkyrieLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class FactorialTest {
    private var context: Context? = null
    private var factorial: Value? = null

    @BeforeEach
    fun initEngine() {
        context = Context.newBuilder().build()

        context!!.eval(
            ValkyrieLanguage.ID, """
function fac(n) {
  if (n <= 1) {
    return 1;
  }
  prev = fac(n - 1);
  return prev * n;
}
"""
        )

        factorial = context!!.getBindings(ValkyrieLanguage.ID).getMember("fac")
    }

    @AfterEach
    fun dispose() {
        context!!.close()
    }

    @Test
    fun factorialOf5() {
        val ret: Number = factorial!!.execute(5).`as`(Number::class.java)
        assertEquals(120, ret.toInt())
    }

    @Test
    fun factorialOf3() {
        val ret: Number = factorial!!.execute(3).`as`(Number::class.java)
        assertEquals(6, ret.toInt())
    }

    @Test
    fun factorialOf1() {
        val ret: Number = factorial!!.execute(1).`as`(Number::class.java)
        assertEquals(1, ret.toInt())
    }
}
