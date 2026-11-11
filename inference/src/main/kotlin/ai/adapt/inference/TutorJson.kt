package ai.adapt.inference

data class TutorDocument(val title: String,val explanation: String,val exercise: String) {
    fun displayText()="$title\n\n$explanation\n\nPractice: $exercise"
}

/** Strict, bounded parser for the flat teaching contract. No coercion, duplicate keys or JSON extensions. */
object TutorJson {
    fun parse(raw: String): TutorDocument = Parser(raw).parse()
    private class Parser(private val raw: String) {
        private var position=0
        private fun whitespace() { while(position<raw.length && raw[position] in " \t\r\n") position++ }
        private fun take(c: Char) { whitespace(); require(position<raw.length && raw[position++]==c) { "Invalid teaching JSON syntax" } }
        private fun string(max: Int): String {
            take('"')
            val out=StringBuilder()
            var closed=false
            while(position<raw.length) {
                val c=raw[position++]
                if(c=='"') { closed=true;break }
                require(c.code>=0x20) { "Unescaped control character" }
                if(c=='\\') {
                    require(position<raw.length)
                    when(val escaped=raw[position++]) {
                        '"','\\','/' -> out.append(escaped)
                        'b' -> out.append('\b'); 'f' -> out.append('\u000c'); 'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                        'u' -> { require(position+4<=raw.length); val code=raw.substring(position,position+4); require(code.all { it in "0123456789abcdefABCDEF" }); out.append(code.toInt(16).toChar());position+=4 }
                        else -> throw IllegalArgumentException("Invalid JSON escape")
                    }
                } else out.append(c)
                require(out.length<=max) { "Teaching field exceeds limit" }
            }
            require(closed)
            val value=out.toString()
            var i=0
            while(i<value.length) {
                if(value[i].isHighSurrogate()) { require(i+1<value.length && value[i+1].isLowSurrogate());i+=2 }
                else { require(!value[i].isLowSurrogate());i++ }
            }
            return value
        }
        fun parse(): TutorDocument {
            require(raw.length<=32_000) { "Teaching JSON exceeds input limit" }
            take('{')
            val fields=mutableMapOf<String,String>()
            val keys=mutableSetOf<String>()
            while(true) {
                val key=string(20)
                require(key in setOf("version","title","explanation","exercise") && keys.add(key)) { "Unexpected or duplicate teaching field" }
                take(':')
                if(key=="version") take('1') else {
                    val max=when(key) { "title" -> 120; "explanation" -> 6000; else -> 2000 }
                    val value=string(max)
                    require(value.isNotBlank()) { "Empty teaching field" }
                    fields[key]=value
                }
                whitespace()
                require(position<raw.length)
                if(raw[position]=='}') { position++;break }
                take(',')
            }
            whitespace()
            require(position==raw.length && keys==setOf("version","title","explanation","exercise")) { "Incomplete or trailing teaching JSON" }
            return TutorDocument(fields.getValue("title"),fields.getValue("explanation"),fields.getValue("exercise"))
        }
    }
}
