package br.tec.wrcoder.meucondominio.core.logging

object PiiSanitizer {
    private val tokenParam = Regex("""([?&](?:token|access_token|refresh_token)=)[^&\s]+""", RegexOption.IGNORE_CASE)
    private val bearerHeader = Regex("""Bearer\s+[A-Za-z0-9._\-]+""")

    fun scrub(input: String): String =
        input.replace(tokenParam, "$1<redacted>").replace(bearerHeader, "Bearer <redacted>")
}
