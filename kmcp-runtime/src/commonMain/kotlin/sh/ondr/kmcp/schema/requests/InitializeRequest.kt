package sh.ondr.kmcp.schema.requests

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.JsonRpcRequest
import sh.ondr.kmcp.schema.requests.params.InitializeParams

@Serializable
@SerialName("initialize")
data class InitializeRequest(
	override val id: String,
	override val method: String = "initialize",
	val params: InitializeParams,
) : JsonRpcRequest()