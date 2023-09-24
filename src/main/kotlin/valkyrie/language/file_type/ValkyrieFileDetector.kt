package valkyrie.language.file_type

import com.oracle.truffle.api.TruffleFile
import valkyrie.language.ValkyrieLanguage
import java.nio.charset.Charset

class ValkyrieFileDetector : TruffleFile.FileTypeDetector {
    override fun findMimeType(file: TruffleFile): String? {
        val name = file.name
        if (name != null) {
            if (name.endsWith(".vk") || name.endsWith(".valkyrie")) {
                return ValkyrieLanguage.MIME_TYPE
            }
        }
        return null
    }

    override fun findEncoding(file: TruffleFile): Charset? {
        return null
    }
}
