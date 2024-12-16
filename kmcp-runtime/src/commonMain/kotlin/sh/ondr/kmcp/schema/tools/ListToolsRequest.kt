package sh.ondr.kmcp.schema.tools

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest
import sh.ondr.kmcp.schema.core.Paginated

@Serializable
@SerialName("tools/list")
data class ListToolsRequest(
	override val id: String,
	override val method: String = "tools/list",
	val params: ListToolsParams? = null,
) : JsonRpcRequest(), Paginated {
	override val cursor: String? get() = params?.cursor

	@Serializable
	data class ListToolsParams(
		val cursor: String? = null,
	)
}