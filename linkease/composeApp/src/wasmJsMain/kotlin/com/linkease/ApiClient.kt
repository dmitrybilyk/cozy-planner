package com.linkease

import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response

private val json = Json { ignoreUnknownKeys = true }

class ApiException(val status: Int) : Exception("API request failed with status $status")

// Construct a plain JS object literal as RequestInit to avoid Kotlin/Wasm passing an opaque
// Wasm reference to the browser's native fetch() — which rejects anything that isn't a plain JS object.
@JsFun("""(method, body) => {
    const init = { method, credentials: 'same-origin' };
    if (body !== null) {
        init.headers = { 'Content-Type': 'application/json' };
        init.body = body;
    }
    return init;
}""")
private external fun makeInit(method: String, body: String?): RequestInit

private suspend fun fetchText(path: String, method: String, body: String? = null): String {
    val response = window.fetch(path, makeInit(method, body)).await<Response>()
    if (!response.ok) throw ApiException(response.status.toInt())
    return response.text().await<JsString>().toString()
}

suspend fun apiGetRaw(path: String): String = fetchText(path, "GET")

suspend fun <T> apiGet(path: String, serializer: KSerializer<T>): T =
    json.decodeFromString(serializer, fetchText(path, "GET"))

suspend fun <T> apiGetList(path: String, serializer: KSerializer<T>): List<T> =
    json.decodeFromString(ListSerializer(serializer), fetchText(path, "GET"))

suspend fun <T> apiPost(path: String, body: T, serializer: KSerializer<T>): T =
    json.decodeFromString(serializer, fetchText(path, "POST", json.encodeToString(serializer, body)))

// For write endpoints that don't follow the create-and-return-object convention
// (e.g. Telegram linking, which just succeeds or fails with an empty body).
suspend fun <T> apiPostRaw(path: String, body: T, serializer: KSerializer<T>): Boolean =
    try {
        fetchText(path, "POST", json.encodeToString(serializer, body))
        true
    } catch (e: ApiException) {
        false
    }

suspend fun <T> apiPut(path: String, body: T, serializer: KSerializer<T>) {
    fetchText(path, "PUT", json.encodeToString(serializer, body))
}

suspend fun apiDelete(path: String) {
    fetchText(path, "DELETE")
}
