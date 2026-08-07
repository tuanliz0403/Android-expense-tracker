package com.example.spendtracker.domain.parser

data class CsvTransaction(
    val merchant: String,
    val amountCents: Long,
    val timestamp: Long,
    val source: String
)

data class CsvParseResult(
    val transactions: List<CsvTransaction>,
    val invalidRows: Int
)

object TransactionCsv {
    const val HEADER = "Merchant,Amount AUD,Transaction time,Source"

    fun parse(csv: String): CsvParseResult {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty() || lines.first().removePrefix("\uFEFF").trim() != HEADER) {
            throw IllegalArgumentException("This is not an Expense Tracker transaction CSV file.")
        }
        var invalid = 0
        val transactions = lines.drop(1).mapNotNull { line ->
            val fields = parseLine(line)
            val amount = fields.getOrNull(1)?.let(CurrencyParser::parseCents)
            val timestamp = fields.getOrNull(2)?.trim()?.toLongOrNull()?.takeIf { it > 0 }
            val merchant = fields.getOrNull(0)?.trim().orEmpty()
            if (fields.size != 4 || merchant.isBlank() || amount == null || timestamp == null) {
                invalid++
                null
            } else CsvTransaction(merchant.take(120), amount, timestamp, fields[3].trim().ifBlank { "CSV_IMPORT" })
        }
        return CsvParseResult(transactions, invalid)
    }

    private fun parseLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                    field.append('"'); index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { result += field.toString(); field.clear() }
                else -> field.append(char)
            }
            index++
        }
        if (quoted) return emptyList()
        result += field.toString()
        return result
    }
}
