@file:OptIn(InternalSerializationApi::class)

package sh.ondr.kmcp.runtime

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import sh.ondr.kmcp.runtime.core.KMCP
import sh.ondr.kmcp.runtime.core.MCP_VERSION
import sh.ondr.kmcp.runtime.error.MethodNotFoundException
import sh.ondr.kmcp.runtime.transport.Transport
import sh.ondr.kmcp.schema.capabilities.Implementation
import sh.ondr.kmcp.schema.capabilities.InitializeRequest.InitializeParams
import sh.ondr.kmcp.schema.capabilities.InitializeResult
import sh.ondr.kmcp.schema.capabilities.PromptsCapability
import sh.ondr.kmcp.schema.capabilities.ServerCapabilities
import sh.ondr.kmcp.schema.capabilities.ToolsCapability
import sh.ondr.kmcp.schema.prompts.GetPromptRequest.GetPromptParams
import sh.ondr.kmcp.schema.prompts.GetPromptResult
import sh.ondr.kmcp.schema.prompts.ListPromptsRequest.ListPromptsParams
import sh.ondr.kmcp.schema.prompts.ListPromptsResult
import sh.ondr.kmcp.schema.tools.CallToolRequest.CallToolParams
import sh.ondr.kmcp.schema.tools.CallToolResult
import sh.ondr.kmcp.schema.tools.ListToolsRequest.ListToolsParams
import sh.ondr.kmcp.schema.tools.ListToolsResult
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KFunction

/**
 * A Model Context Protocol (MCP) server implementation that handles
 * MCP requests from a connected client via a specified [Transport].
 *
 * Tools and prompts can be added at build time via [Builder.withTool], [Builder.withTools], [Builder.withPrompt], and [Builder.withPrompts].
 * They can also be added or removed dynamically at runtime if needed.
 */
class Server private constructor(
	private val transport: Transport,
	private val tools: MutableList<String>,
	private val prompts: MutableList<String>,
	private val logger: (String) -> Unit,
	private val dispatcher: CoroutineContext,
	private val serverName: String,
	private val serverVersion: String,
) : McpComponent(
		transport = transport,
		logger = logger,
		coroutineContext = dispatcher,
	) {
	/**
	 * Dynamically adds a new tool to the server at runtime.
	 *
	 * @param tool The @Tool-annotated function reference.
	 * @return `true` if the tool was added, `false` if it was already present.
	 */
	fun addTool(tool: KFunction<*>): Boolean =
		if (tool.name !in tools) {
			tools.add(tool.name)
			true
		} else {
			false
		}

	/**
	 * Dynamically removes a previously added tool by its @Tool-annotated function reference.
	 *
	 * @param tool The @Tool-annotated function reference that was previously added.
	 * @return `true` if the tool was removed, `false` if it was not found.
	 */
	fun removeTool(tool: KFunction<*>): Boolean {
		return tools.remove(tool.name)
	}

	/**
	 * Dynamically adds a new prompt to the server at runtime.
	 *
	 * @param prompt The @Prompt-annotated function reference.
	 * @return `true` if the prompt was added, `false` if it was already present.
	 */
	fun addPrompt(prompt: KFunction<*>): Boolean {
		return if (prompt.name !in prompts) {
			prompts.add(prompt.name)
			true
		} else {
			false
		}
	}

	/**
	 * Dynamically removes a previously added prompt by its @Prompt-annotated function reference.
	 *
	 * @param prompt The @Prompt-annotated function reference that was previously added.
	 * @return `true` if the prompt was removed, `false` if it was not found.
	 */
	fun removePrompt(prompt: KFunction<*>): Boolean {
		return prompts.remove(prompt.name)
	}

	// -----------------------------------------------------
	// Overridden Request Handlers for MCP Operations
	// -----------------------------------------------------

	override suspend fun handleInitializeRequest(params: InitializeParams): InitializeResult {
		return InitializeResult(
			protocolVersion = MCP_VERSION,
			capabilities = ServerCapabilities(
				tools = if (tools.isNotEmpty()) ToolsCapability() else null,
				prompts = if (prompts.isNotEmpty()) PromptsCapability() else null,
			),
			serverInfo = Implementation(serverName, serverVersion),
		)
	}

	override suspend fun handleListPromptsRequest(params: ListPromptsParams?): ListPromptsResult {
		val promptInfos = prompts.map { promptName ->
			KMCP.promptInfos[promptName]
				?: throw IllegalStateException("PromptInfo not found for prompt: $promptName")
		}
		return ListPromptsResult(prompts = promptInfos)
	}

	override suspend fun handleGetPromptRequest(params: GetPromptParams): GetPromptResult {
		val promptName = params.name
		val handler = KMCP.promptHandlers[promptName] ?: throw MethodNotFoundException("Handler for prompt $promptName not found")
		val jsonArgs = params
			.arguments
			?.mapValues { JsonPrimitive(it.value) }
			?.let { JsonObject(it) }
			?: JsonObject(emptyMap())

		return handler.call(jsonArgs)
	}

	override suspend fun handleCallToolRequest(params: CallToolParams): CallToolResult {
		val toolName = params.name
		val handler = KMCP.toolHandlers[toolName] ?: throw IllegalStateException("Handler for tool $toolName not found")
		val jsonArguments = JsonObject(params.arguments ?: emptyMap())
		return handler.call(jsonArguments)
	}

	override suspend fun handleListToolsRequest(params: ListToolsParams?): ListToolsResult {
		val toolInfos = tools.map { name ->
			KMCP.toolInfos[name] ?: throw IllegalStateException("ToolInfo not found for tool: $name")
		}
		return ListToolsResult(tools = toolInfos)
	}

	/**
	 * Builder for constructing a [Server] instance.
	 *
	 * All configuration is done via fluent API calls, and once [build] is called,
	 * you get a fully configured Server ready to be started with [Server.start].
	 *
	 * Example:
	 * ```kotlin
	 * val server = Server.Builder()
	 *     .withTransport(myTransport)
	 *     .withTool(::myToolFunction)
	 *     .withPrompt(::myPromptFunction)
	 *     .withServerInfo("MyServer", "1.2.3")
	 *     .withLogger { line -> println(line) }
	 *     .build()
	 * ```
	 */
	class Builder {
		private val builderTools = mutableSetOf<String>()
		private val builderPrompts = mutableSetOf<String>()
		private var builderTransport: Transport? = null
		private var builderLogger: (String) -> Unit = {}
		private var builderDispatcher: CoroutineContext = Dispatchers.Default
		private var builderServerName: String = "TestServer"
		private var builderServerVersion: String = "1.0.0"
		private var used = false

		/**
		 * Sets the [Transport] used by the server to communicate with clients.
		 * This must be called before [build], or an error is thrown.
		 */
		fun withTransport(transport: Transport) =
			apply {
				builderTransport = transport
			}

		/**
		 * Registers a tool by referencing its @Tool-annotated function.
		 */
		fun withTool(toolFunction: KFunction<*>) =
			apply {
				require(toolFunction.name !in builderTools) {
					"Tool with name ${toolFunction.name} already registered."
				}
				builderTools.add(toolFunction.name)
			}

		/**
		 * Registers multiple tools by referencing their @Tool-annotated functions.
		 */
		fun withTools(vararg toolFunctions: KFunction<*>) =
			apply {
				toolFunctions.forEach { withTool(it) }
			}

		/**
		 * Registers a prompt by referencing its @Prompt-annotated function.
		 */
		fun withPrompt(promptFunction: KFunction<*>) =
			apply {
				require(promptFunction.name !in builderPrompts) {
					"Prompt with name ${promptFunction.name} already registered."
				}
				builderPrompts.add(promptFunction.name)
			}

		/**
		 * Registers multiple prompts by referencing their @Prompt-annotated functions.
		 */
		fun withPrompts(vararg promptFunctions: KFunction<*>) =
			apply {
				promptFunctions.forEach { withPrompt(it) }
			}

		/**
		 * Sets the coroutine context (or dispatcher) for the server's internal coroutines.
		 *
		 * Defaults to [Dispatchers.Default] if not set.
		 */
		fun withDispatcher(dispatcher: CoroutineContext) =
			apply {
				builderDispatcher = dispatcher
			}

		/**
		 * Adds a logger callback for incoming/outgoing messages.
		 *
		 * Useful for debugging or auditing. If not set, no logging occurs.
		 */
		fun withLogger(logger: (String) -> Unit) =
			apply {
				builderLogger = logger
			}

		/**
		 * Sets the server's name and version, reported in the `initialize` response.
		 *
		 * Defaults to "TestServer" and "1.0.0" if not provided.
		 */
		fun withServerInfo(
			name: String,
			version: String,
		) = apply {
			builderServerName = name
			builderServerVersion = version
		}

		/**
		 * Builds the [Server] instance.
		 *
		 * @throws IllegalStateException if called more than once or if a required field is missing.
		 */
		fun build(): Server {
			check(!used) { "This Builder has already been used." }
			used = true

			val transport = builderTransport ?: error("Transport must be set before building.")
			return Server(
				transport = transport,
				tools = builderTools.toMutableList(),
				prompts = builderPrompts.toMutableList(),
				logger = builderLogger,
				dispatcher = builderDispatcher,
				serverName = builderServerName,
				serverVersion = builderServerVersion,
			)
		}
	}
}
