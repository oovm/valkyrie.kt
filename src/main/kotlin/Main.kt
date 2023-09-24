import org.graalvm.polyglot.Context
import org.graalvm.polyglot.PolyglotException
import org.graalvm.polyglot.Source
import java.io.*
import kotlin.system.exitProcess

object Main {
    private const val SL = "sl"

    /**
     * The main entry point.
     */
    @Throws(IOException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val source: Source
        val options: MutableMap<String, String> = HashMap()
        options["engine.WarnInterpreterOnly"] = "false";
        var file: String? = null
        for (arg in args) {
            if (parseOption(options, arg)) {
                continue
            } else {
                if (file == null) {
                    file = arg
                }
            }
        }

        source = if (file == null) {

            Source.newBuilder(SL, InputStreamReader(System.`in`), "<stdin>").build()

        } else {
            Source.newBuilder(SL, File(file)).build()
        }

        exitProcess(executeSource(source, System.`in`, System.out, options))
    }

    private fun executeSource(source: Source, `in`: InputStream, out: PrintStream, options: Map<String, String>): Int {
        val context: Context
        val err = System.err
        try {
            context = Context.newBuilder(SL).`in`(`in`).out(out).options(options).build()
        } catch (e: IllegalArgumentException) {
            err.println(e.message)
            return 1
        }
        out.println("== running on " + context.engine)

        try {
            val result = context.eval(source)
            if (context.getBindings(SL).getMember("main") == null) {
                err.println("No function main() defined in SL source file.")
                return 1
            }
            if (!result.isNull) {
                out.println(result.toString())
            }
            return 0
        } catch (ex: PolyglotException) {
            if (ex.isInternalError) {
                // for internal errors we print the full stack trace
                ex.printStackTrace()
            } else {
                err.println(ex.message)
            }
            return 1
        } finally {
            context.close()
        }
    }

    private fun parseOption(options: MutableMap<String, String>, arg: String): Boolean {
        if (arg.length <= 2 || !arg.startsWith("--")) {
            return false
        }
        val eqIdx = arg.indexOf('=')
        val key: String
        var value: String?
        if (eqIdx < 0) {
            key = arg.substring(2)
            value = null
        } else {
            key = arg.substring(2, eqIdx)
            value = arg.substring(eqIdx + 1)
        }

        if (value == null) {
            value = "true"
        }
        val index = key.indexOf('.')
        var group = key
        if (index >= 0) {
            group = group.substring(0, index)
        }
        options[key] = value
        return true
    }
}