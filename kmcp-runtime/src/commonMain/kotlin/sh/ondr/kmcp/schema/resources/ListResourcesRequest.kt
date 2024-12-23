package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.Paginated

@Serializable
@SerialName("resources/list")
data class ListResourcesRequest(
	override val id: String,
	val params: ListResourcesParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor

	@Serializable
	data class ListResourcesParams(
		val cursor: String? = null,
		val _meta: Map<String, JsonElement>? = null,
	)
}
