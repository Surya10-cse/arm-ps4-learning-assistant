package ai.adapt.core

import java.io.File

private fun quote(value: String)=buildString {
    append('"')
    value.forEach { c -> when(c) {
        '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
        else -> if(c.code<32) append("\\u%04x".format(c.code)) else append(c)
    } }
    append('"')
}
fun main(args: Array<String>) {
    require(args.size==1)
    val skills=Curriculum.skills.joinToString(",") { s ->
        "{\"id\":${quote(s.id)},\"title\":${quote(s.title)},\"prerequisites\":[${s.prerequisites.joinToString(",",transform=::quote)}],\"minutes\":${s.minutes},\"difficulty\":${s.difficulty},\"relevance\":{${s.relevance.entries.joinToString(",") { quote(it.key.name)+":"+it.value }}},\"lesson\":${quote(s.lesson)}}"
    }
    val questions=Curriculum.questions.joinToString(",") { q ->
        "{\"id\":${quote(q.id)},\"skillId\":${quote(q.skillId)},\"prompt\":${quote(q.prompt)},\"options\":[${q.options.joinToString(",",transform=::quote)}],\"correctIndex\":${q.correct},\"explanation\":${quote(q.explanation)},\"misconception\":${quote(q.misconception)},\"difficulty\":${q.difficulty}}"
    }
    val out=File(args[0]);out.parentFile?.mkdirs()
    out.writeText("{\"version\":${Curriculum.VERSION},\"skills\":[$skills],\"questions\":[$questions]}",Charsets.UTF_8)
    println("Exported ${Curriculum.skills.size} competencies and ${Curriculum.questions.size} curated questions to $out")
}
