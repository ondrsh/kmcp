package sh.ondr.kmcp.ksp

import com.google.devtools.ksp.processing.Dependencies

internal fun KmcpProcessor.generatePromptFiles() {
	generatePromptParamsFile()
	generatePromptHandlersFile()
}

private fun KmcpProcessor.generatePromptParamsFile() {
	val fileName = "KmcpGeneratedPromptParams"
	val allFiles = collectedPrompts.flatMap { it.originatingFiles }.distinct().toTypedArray()
	val file = codeGenerator.createNewFile(
		dependencies = Dependencies(aggregating = true, sources = allFiles),
		packageName = generatedPkg,
		fileName = fileName,
	)

	val code = buildString {
		appendLine("// Generated by KMCP")
		appendLine("package $generatedPkg")
		appendLine()
		appendLine("import kotlinx.serialization.Serializable")
		appendLine()

		for (helper in collectedPrompts) {
			val paramsClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}PromptParams"
			appendLine("@Serializable")
			append("data class $paramsClassName(\n")
			helper.params.forEachIndexed { index, p ->
				val comma = if (index == helper.params.size - 1) "" else ","
				if (!p.hasDefault && !p.isNullable) {
					append("    val ${p.name}: ${p.fqnType}$comma\n")
				} else {
					append("    val ${p.name}: ${p.fqnType}? = null$comma\n")
				}
			}
			appendLine(")")
			appendLine()
		}
	}

	file.write(code.toByteArray())
	file.close()
}

private fun KmcpProcessor.generatePromptHandlersFile() {
	val fileName = "KmcpGeneratedPromptHandlers"
	val allFiles = collectedPrompts.flatMap { it.originatingFiles }.distinct().toTypedArray()
	val file = codeGenerator.createNewFile(
		dependencies = Dependencies(aggregating = true, sources = allFiles),
		packageName = generatedPkg,
		fileName = fileName,
	)

	val code = buildString {
		appendLine("// Generated by KMCP")
		appendLine("package $generatedPkg")
		appendLine()
		appendLine("import kotlinx.serialization.json.JsonObject")
		appendLine("import kotlinx.serialization.json.decodeFromJsonElement")
		appendLine("import sh.ondr.kmcp.runtime.core.kmcpJson")
		appendLine("import sh.ondr.kmcp.schema.prompts.GetPromptResult")
		appendLine("import sh.ondr.kmcp.runtime.prompts.PromptHandler")
		appendLine("import sh.ondr.kmcp.runtime.error.MissingRequiredArgumentException")
		appendLine("import sh.ondr.kmcp.runtime.error.UnknownArgumentException")
		appendLine()

		for (helper in collectedPrompts) {
			val handlerClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}PromptHandler"
			val paramsClassName = "KmcpGenerated${helper.functionName.replaceFirstChar { it.uppercaseChar() }}PromptParams"

			// Gather parameter names
			val paramNames = helper.params.map { it.name }.joinToString { "\"$it\"" }

			appendLine("class $handlerClassName : PromptHandler {")
			appendLine("    private val knownParams = setOf($paramNames)")
			appendLine()
			appendLine("    override fun call(params: JsonObject): GetPromptResult {")
			appendLine("        val unknownKeys = params.keys - knownParams")
			appendLine("        if (unknownKeys.isNotEmpty()) {")
			appendLine(
				"            throw UnknownArgumentException(\"Unknown argument '\${unknownKeys.first()}' for prompt '${helper.functionName}'\")",
			)
			appendLine("        }")
			appendLine()
			val requiredParams = helper.params.filter { it.isRequired }.map { it.name }
			if (requiredParams.isNotEmpty()) {
				appendLine("        // Check required parameters")
				for (reqParam in requiredParams) {
					appendLine("        if (!params.containsKey(\"$reqParam\")) {")
					appendLine("            throw MissingRequiredArgumentException(\"Missing required argument '$reqParam'\")")
					appendLine("        }")
				}
			}
			appendLine()
			appendLine("        val obj = kmcpJson.decodeFromJsonElement($paramsClassName.serializer(), params)")
			appendLine("        return ${generatePromptInvocationCode(helper, 2)}")
			appendLine("    }")
			appendLine("}")
			appendLine()
		}
	}

	file.write(code.toByteArray())
	file.close()
}

private fun KmcpProcessor.generatePromptInvocationCode(
	helper: PromptHelper,
	level: Int,
): String {
	val defaultParams = helper.params.filter { it.hasDefault }
	val requiredParams = helper.params.filter { !it.hasDefault }
	return generatePromptOptionalChain(helper, requiredParams, defaultParams, level)
}

private fun KmcpProcessor.generatePromptOptionalChain(
	helper: PromptHelper,
	requiredParams: List<ParamInfo>,
	defaultParams: List<ParamInfo>,
	level: Int,
): String {
	if (defaultParams.isEmpty()) {
		return callPromptFunction(helper.fqName, requiredParams, emptyList(), level)
	}

	val firstOptional = defaultParams.first()
	val remaining = defaultParams.drop(1)
	val indent = " ".repeat(level * 4)

	return buildString {
		appendLine("${indent}if (params.containsKey(\"${firstOptional.name}\")) {")
		val ifBranch = generatePromptOptionalChain(helper, requiredParams + firstOptional, remaining, level + 1)
		appendLine(ifBranch)
		appendLine("$indent} else {")
		val elseBranch = generatePromptOptionalChain(helper, requiredParams, remaining, level + 1)
		appendLine(elseBranch)
		appendLine("$indent}")
	}
}

private fun KmcpProcessor.callPromptFunction(
	fqFunctionName: String,
	requiredParams: List<ParamInfo>,
	optionalParams: List<ParamInfo>,
	level: Int,
): String {
	val indent = " ".repeat(level * 4)
	val allParams = requiredParams + optionalParams
	val args = allParams.joinToString(",\n$indent    ") { param ->
		val suffix = if (param.hasDefault && !param.isNullable) "!!" else ""
		"${param.name} = obj.${param.name}$suffix"
	}

	return buildString {
		appendLine("$indent$fqFunctionName(")
		if (allParams.isNotEmpty()) {
			appendLine("$indent    $args")
		}
		append("$indent)")
	}
}
