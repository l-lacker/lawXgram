package org.telegram.tlrpc.schema

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TlSchemaJson(
    @param:Json(name = "constructors")
    val constructors: List<JsonTlConstructor>,

    @param:Json(name = "methods")
    val methods: List<JsonTlMethod>,
) {
    sealed class JsonTlObject {
        abstract val magic: String
        abstract val params: List<JsonTlConstructorParam>
        abstract val name: String
        abstract val type: String
        abstract val layer: Int?
    }

    @JsonClass(generateAdapter = true)
    data class JsonTlMethod(
        @param:Json(name = "id")
        override val magic: String,

        @param:Json(name = "method")
        override val name: String,

        @param:Json(name = "params")
        override val params: List<JsonTlConstructorParam>,

        @param:Json(name = "type")
        override val type: String,

        @param:Json(name = "layer")
        override val layer: Int? = null
    ): JsonTlObject()

    @JsonClass(generateAdapter = true)
    data class JsonTlConstructor(
        @param:Json(name = "id")
        override val magic: String,

        @param:Json(name = "predicate")
        override val name: String,

        @param:Json(name = "params")
        override val params: List<JsonTlConstructorParam>,

        @param:Json(name = "type")
        override val type: String,

        @param:Json(name = "layer")
        override val layer: Int? = null
    ): JsonTlObject()

    @JsonClass(generateAdapter = true)
    data class JsonTlConstructorParam(
        @param:Json(name = "name")
        val name: String,

        @param:Json(name = "type")
        val type: String
    )
}
