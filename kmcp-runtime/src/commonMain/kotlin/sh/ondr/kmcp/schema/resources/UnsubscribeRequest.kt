package sh.ondr.kmcp.schema.resources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.ondr.kmcp.schema.core.JsonRpcRequest

@Serializable
@SerialName("resources/unsubscribe")
data class UnsubscribeRequest(
	override val id: String,
	override val method: String = "resources/unsubscribe",
	val params: UnsubscribeParams,
) : JsonRpcRequest() {
	@Serializable
	data class UnsubscribeParams(
		val uri: String,
	)
}