package com.example.spysim

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URL
import java.util.UUID

data class UploadResult(val statusCode: Int?, val message: String, val destination: String)

object MockApiClient {
    private const val TOKEN = "TEST_BOT_TOKEN"
    private const val CHAT_ID = "TEST_CHAT_ID"
    private const val CONNECT_TIMEOUT_MS = 3000
    private const val READ_TIMEOUT_MS = 5000

    private fun validateBaseUrl(raw: String): URL {
        val url = URL(raw.trim())
        require(url.protocol == "http") { "Only HTTP to the local lab server is supported" }
        require(url.userInfo == null && url.query == null && url.ref == null) {
            "URL must not contain credentials, query, or fragment"
        }
        require(url.path.isEmpty() || url.path == "/") { "Enter only the server origin, without a path" }
        val host = url.host
        val address = when {
            host.equals("localhost", true) -> InetAddress.getByName("127.0.0.1")
            host.matches(Regex("[0-9.]+")) -> InetAddress.getByName(host)
            else -> throw IllegalArgumentException("Use a private IPv4 address or localhost")
        }
        require(address is Inet4Address) { "IPv4 address required" }
        val b = address.address.map { it.toInt() and 0xff }
        require(b[0] == 10 || (b[0] == 172 && b[1] in 16..31) ||
            (b[0] == 192 && b[1] == 168) || b[0] == 127) {
            "Destination must be loopback or a private IPv4 address"
        }
        return url
    }

    fun validateAddress(raw: String) { validateBaseUrl(raw) }

    fun upload(baseUrl: String, image: File, index: Int): UploadResult {
        val origin = validateBaseUrl(baseUrl)
        val endpoint = URL(origin, "/bot$TOKEN/sendPhoto")
        val destination = "${endpoint.host}:${endpoint.port}${endpoint.path}"
        EventLog.add("UPLOAD_ATTEMPT", "image=$index method=POST destination=$destination")
        val boundary = "LabBoundary" + UUID.randomUUID().toString().replace("-", "")
        var connection: HttpURLConnection? = null
        return try {
            connection = endpoint.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json")
            connection.outputStream.use { output ->
                fun textPart(name: String, value: String) {
                    output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                    output.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(Charsets.UTF_8))
                    output.write(value.toByteArray(Charsets.UTF_8))
                    output.write("\r\n".toByteArray(Charsets.UTF_8))
                }
                textPart("chat_id", CHAT_ID)
                output.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
                output.write(("Content-Disposition: form-data; name=\"photo\"; filename=\"${image.name}\"\r\n").toByteArray(Charsets.UTF_8))
                output.write("Content-Type: image/jpeg\r\n\r\n".toByteArray(Charsets.UTF_8))
                image.inputStream().use { it.copyTo(output) }
                output.write("\r\n".toByteArray(Charsets.UTF_8))
                output.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty().take(500)
            val success = status == 200 && body.contains("\"ok\":true")
            val message = if (success) "SUCCESS HTTP $status" else "FAILED HTTP $status: $body"
            EventLog.add(if (success) "UPLOAD_SUCCESS" else "UPLOAD_FAILURE",
                "image=$index status=$status destination=$destination")
            UploadResult(status, message, destination)
        } catch (e: Exception) {
            val message = "${e.javaClass.simpleName}: ${e.message ?: "connection failed"}"
            EventLog.add("CONNECTION_ERROR", "image=$index destination=$destination error=$message")
            UploadResult(null, message, destination)
        } finally { connection?.disconnect() }
    }
}
