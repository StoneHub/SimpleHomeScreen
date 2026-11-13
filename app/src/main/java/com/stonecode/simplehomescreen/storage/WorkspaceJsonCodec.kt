package com.stonecode.simplehomescreen.storage

internal class WorkspaceJsonCodec {
    data class LegacyWidget(val id: Int, val provider: String?)

    sealed interface DecodeResult {
        data class Current(val workspace: Workspace) : DecodeResult
        data class LegacyV0(val widgets: List<LegacyWidget>) : DecodeResult
        data class Unsupported(val version: Int) : DecodeResult
        data class Invalid(val message: String) : DecodeResult
    }

    fun encode(workspace: Workspace): String {
        val items = workspace.dashboardItems.joinToString(",") { item ->
            when (item) {
                is DashboardItem.AppShortcut -> objectJson(
                    "type" to string("app"),
                    "id" to string(item.id),
                    "componentName" to string(item.appId.componentName),
                    "userSerial" to item.appId.userSerial.toString(),
                    "position" to positionJson(item.position),
                    "span" to spanJson(item.span)
                )

                is DashboardItem.Widget -> objectJson(
                    "type" to string("widget"),
                    "id" to string(item.id),
                    "appWidgetId" to item.appWidgetId.toString(),
                    "provider" to (item.provider?.let(::string) ?: "null"),
                    "position" to positionJson(item.position),
                    "span" to spanJson(item.span)
                )
            }
        }
        val overrides = workspace.categoryOverrides.entries.joinToString(",") { (appId, category) ->
            objectJson(
                "componentName" to string(appId.componentName),
                "userSerial" to appId.userSerial.toString(),
                "categoryId" to string(category)
            )
        }
        return objectJson(
            "schemaVersion" to workspace.schemaVersion.toString(),
            "dashboardItems" to "[$items]",
            "categoryOverrides" to "[$overrides]"
        )
    }

    fun decode(text: String): DecodeResult {
        val root = try {
            JsonParser(text).parse() as? JsonValue.ObjectValue
                ?: return DecodeResult.Invalid("Workspace root must be an object")
        } catch (error: JsonParseException) {
            return DecodeResult.Invalid(error.message ?: "Invalid JSON")
        }

        val schema = root.long("schemaVersion")
        if (schema == null) return decodeV0(root)
        if (schema != Workspace.CURRENT_SCHEMA_VERSION.toLong()) {
            return DecodeResult.Unsupported(schema.toInt())
        }

        return try {
            val items = root.array("dashboardItems")?.values.orEmpty().map { decodeItem(it) }
            val overrides = root.array("categoryOverrides")?.values.orEmpty().associate { value ->
                val entry = value.requireObject("category override")
                StoredAppId(
                    componentName = entry.requireString("componentName"),
                    userSerial = entry.requireLong("userSerial")
                ) to entry.requireString("categoryId")
            }
            DecodeResult.Current(Workspace(dashboardItems = items, categoryOverrides = overrides))
        } catch (error: IllegalArgumentException) {
            DecodeResult.Invalid(error.message ?: "Invalid workspace fields")
        }
    }

    private fun decodeV0(root: JsonValue.ObjectValue): DecodeResult {
        val widgetsValue = root.fields["widgets"] ?: return DecodeResult.Invalid(
            "Missing schemaVersion and legacy widgets"
        )
        val widgets = widgetsValue as? JsonValue.ArrayValue
            ?: return DecodeResult.Invalid("Legacy widgets must be an array")
        return try {
            DecodeResult.LegacyV0(widgets.values.mapNotNull { value ->
                val entry = value.requireObject("legacy widget")
                val id = entry.requireLong("id").toInt()
                if (id < 0) null else LegacyWidget(id, entry.string("provider")?.ifBlank { null })
            })
        } catch (error: IllegalArgumentException) {
            DecodeResult.Invalid(error.message ?: "Invalid legacy workspace")
        }
    }

    private fun decodeItem(value: JsonValue): DashboardItem {
        val item = value.requireObject("dashboard item")
        val id = item.requireString("id")
        val position = decodePosition(item.fields["position"])
        val span = decodeSpan(item.fields["span"])
        return when (item.requireString("type")) {
            "app" -> DashboardItem.AppShortcut(
                id = id,
                appId = StoredAppId(
                    item.requireString("componentName"),
                    item.requireLong("userSerial")
                ),
                position = position,
                span = span
            )

            "widget" -> DashboardItem.Widget(
                id = id,
                appWidgetId = item.requireLong("appWidgetId").toInt(),
                provider = item.string("provider"),
                position = position,
                span = span
            )

            else -> throw IllegalArgumentException("Unsupported dashboard item type")
        }
    }

    private fun decodePosition(value: JsonValue?): GridPosition {
        val objectValue = value.requireObject("position")
        return GridPosition(
            page = objectValue.requireLong("page").toInt(),
            column = objectValue.requireLong("column").toInt(),
            row = objectValue.requireLong("row").toInt()
        ).also {
            require(it.page >= 0 && it.column >= 0 && it.row >= 0) { "Negative grid position" }
        }
    }

    private fun decodeSpan(value: JsonValue?): GridSpan {
        val objectValue = value.requireObject("span")
        return GridSpan(
            columns = objectValue.requireLong("columns").toInt(),
            rows = objectValue.requireLong("rows").toInt()
        ).also { require(it.columns > 0 && it.rows > 0) { "Grid span must be positive" } }
    }

    private fun positionJson(position: GridPosition) = objectJson(
        "page" to position.page.toString(),
        "column" to position.column.toString(),
        "row" to position.row.toString()
    )

    private fun spanJson(span: GridSpan) = objectJson(
        "columns" to span.columns.toString(),
        "rows" to span.rows.toString()
    )

    private fun objectJson(vararg fields: Pair<String, String>): String = fields.joinToString(
        separator = ",",
        prefix = "{",
        postfix = "}"
    ) { (key, value) -> "${string(key)}:$value" }

    private fun string(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}

private sealed interface JsonValue {
    data class ObjectValue(val fields: Map<String, JsonValue>) : JsonValue {
        fun array(key: String) = fields[key] as? ArrayValue
        fun string(key: String) = (fields[key] as? StringValue)?.value
        fun long(key: String) = (fields[key] as? NumberValue)?.raw?.toLongOrNull()
        fun requireString(key: String) = string(key)
            ?: throw IllegalArgumentException("Missing or invalid $key")
        fun requireLong(key: String) = long(key)
            ?: throw IllegalArgumentException("Missing or invalid $key")
    }

    data class ArrayValue(val values: List<JsonValue>) : JsonValue
    data class StringValue(val value: String) : JsonValue
    data class NumberValue(val raw: String) : JsonValue
    data class BooleanValue(val value: Boolean) : JsonValue
    data object NullValue : JsonValue
}

private fun JsonValue?.requireObject(label: String) = this as? JsonValue.ObjectValue
    ?: throw IllegalArgumentException("Missing or invalid $label")

private class JsonParseException(message: String) : IllegalArgumentException(message)

private class JsonParser(private val source: String) {
    private var index = 0

    fun parse(): JsonValue {
        val value = readValue()
        skipWhitespace()
        if (index != source.length) fail("Unexpected trailing content")
        return value
    }

    private fun readValue(): JsonValue {
        skipWhitespace()
        return when (peek()) {
            '{' -> readObject()
            '[' -> readArray()
            '"' -> JsonValue.StringValue(readString())
            't' -> readLiteral("true", JsonValue.BooleanValue(true))
            'f' -> readLiteral("false", JsonValue.BooleanValue(false))
            'n' -> readLiteral("null", JsonValue.NullValue)
            '-', in '0'..'9' -> readNumber()
            else -> fail("Expected JSON value")
        }
    }

    private fun readObject(): JsonValue.ObjectValue {
        expect('{')
        skipWhitespace()
        if (consume('}')) return JsonValue.ObjectValue(emptyMap())
        val fields = linkedMapOf<String, JsonValue>()
        while (true) {
            skipWhitespace()
            if (peek() != '"') fail("Expected object key")
            val key = readString()
            skipWhitespace()
            expect(':')
            fields[key] = readValue()
            skipWhitespace()
            if (consume('}')) break
            expect(',')
        }
        return JsonValue.ObjectValue(fields)
    }

    private fun readArray(): JsonValue.ArrayValue {
        expect('[')
        skipWhitespace()
        if (consume(']')) return JsonValue.ArrayValue(emptyList())
        val values = mutableListOf<JsonValue>()
        while (true) {
            values += readValue()
            skipWhitespace()
            if (consume(']')) break
            expect(',')
        }
        return JsonValue.ArrayValue(values)
    }

    private fun readString(): String {
        expect('"')
        return buildString {
            while (true) {
                if (index >= source.length) fail("Unterminated string")
                when (val character = source[index++]) {
                    '"' -> return@buildString
                    '\\' -> {
                        if (index >= source.length) fail("Unterminated escape")
                        append(
                            when (val escaped = source[index++]) {
                                '"', '\\', '/' -> escaped
                                'b' -> '\b'
                                'f' -> '\u000C'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> readUnicode()
                                else -> fail("Invalid escape")
                            }
                        )
                    }

                    else -> append(character)
                }
            }
        }
    }

    private fun readUnicode(): Char {
        if (index + 4 > source.length) fail("Invalid unicode escape")
        val hex = source.substring(index, index + 4)
        index += 4
        return hex.toIntOrNull(16)?.toChar() ?: fail("Invalid unicode escape")
    }

    private fun readNumber(): JsonValue.NumberValue {
        val start = index
        if (peek() == '-') index++
        while (peek() in '0'..'9') index++
        if (peek() == '.') {
            index++
            while (peek() in '0'..'9') index++
        }
        if (peek() == 'e' || peek() == 'E') {
            index++
            if (peek() == '+' || peek() == '-') index++
            while (peek() in '0'..'9') index++
        }
        return JsonValue.NumberValue(source.substring(start, index))
    }

    private fun <T : JsonValue> readLiteral(literal: String, value: T): T {
        if (!source.startsWith(literal, index)) fail("Invalid literal")
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (peek()?.isWhitespace() == true) index++
    }

    private fun consume(expected: Char): Boolean {
        if (peek() != expected) return false
        index++
        return true
    }

    private fun expect(expected: Char) {
        if (!consume(expected)) fail("Expected '$expected'")
    }

    private fun peek(): Char? = source.getOrNull(index)

    private fun fail(message: String): Nothing = throw JsonParseException("$message at offset $index")
}
