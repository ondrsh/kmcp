package sh.ondr.kmcp.schema.prompts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.PaginatedResult

@Serializable
data class ListPromptsResult(
	val prompts: List<PromptInfo>,
	override val _meta: Map<String, JsonElement>? = null,
	override val nextCursor: String? = null,
) : PaginatedResult
