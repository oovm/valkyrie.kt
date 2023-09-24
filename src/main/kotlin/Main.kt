import valkyrie.language.ValkyrieLanguage

fun main(args: Array<String>) {
    println("Hello World!")

    val vm = ValkyrieLanguage().createContext(null)
    val list = vm.make_list(mutableListOf())

    // Try adding program arguments via Run/Debug configuration.
    // Learn more about running applications: https://www.jetbrains.com/help/idea/running-applications.html.
    println("Program arguments: ${list.length}")
}

