package valkyrie.language

import com.oracle.truffle.api.TruffleFile
import com.oracle.truffle.api.TruffleFile.FileTypeDetector
import com.oracle.truffle.api.TruffleLanguage
import valkyrie.runtime.ValkyrieVM
import java.nio.charset.Charset




@TruffleLanguage.Registration(
    id = ValkyrieLanguage.ID,
    name = ValkyrieLanguage.DisplayName,
    defaultMimeType = ValkyrieLanguage.MIME,
    characterMimeTypes = [ValkyrieLanguage.MIME],
//    contextPolicy = ContextPolicy.SHARED,
    fileTypeDetectors = [ValkyrieFileDetector::class],
    website = "https://www.graalvm.org/graalvm-as-a-platform/implement-language/"
)
class ValkyrieLanguage : TruffleLanguage<ValkyrieVM>() {

    public override fun createContext(env: Env?): ValkyrieVM {
        return ValkyrieVM()
    }

    companion object {
        const val ID = "valkyrie";
        const val DisplayName = "Valkyrie Language"
        const val MIME = "Valkyrie";
    }

}

class ValkyrieFileDetector : FileTypeDetector {
    override fun findMimeType(file: TruffleFile?): String {
        return ValkyrieLanguage.MIME
    }

    override fun findEncoding(file: TruffleFile?): Charset {
        return Charset.defaultCharset()
    }
}
