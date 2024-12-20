package sh.ondr.kmcp.runtime.prompts

import sh.ondr.kmcp.schema.content.PromptContent
import sh.ondr.kmcp.schema.content.TextContent
import sh.ondr.kmcp.schema.core.Role
import sh.ondr.kmcp.schema.prompts.GetPromptResult
import sh.ondr.kmcp.schema.prompts.PromptMessage

// A builder for constructing a GetPromptResult easily
fun buildPrompt(
	description: String? = null,
	block: PromptBuilder.() -> Unit,
): GetPromptResult {
	val builder = PromptBuilder()
	builder.block()
	return GetPromptResult(
		description = description,
		messages = builder.buildMessages(),
	)
}

class PromptBuilder {
	private val messages = mutableListOf<PromptMessage>()

	fun user(message: String) {
		messages += PromptMessage(
			role = Role.USER,
			content = TextContent(message),
		)
	}

	fun user(messageBlock: () -> String) {
		user(messageBlock())
	}

	fun user(content: PromptContent) {
		messages += PromptMessage(
			role = Role.USER,
			content = content,
		)
	}

	fun assistant(message: String) {
		messages += PromptMessage(
			role = Role.ASSISTANT,
			content = TextContent(message),
		)
	}

	fun assistant(messageBlock: () -> String) {
		assistant(messageBlock())
	}

	fun assistant(content: PromptContent) {
		messages += PromptMessage(
			role = Role.ASSISTANT,
			content = content,
		)
	}

	fun buildMessages(): List<PromptMessage> = messages
}