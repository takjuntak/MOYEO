package com.neungi.moyeo.views.plan.scheduleviewmodel

import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.neungi.data.entity.AddReceive
import com.neungi.data.entity.ManipulationEvent
import com.neungi.data.entity.Member
import com.neungi.data.entity.PathReceive
import com.neungi.data.entity.ScheduleEntity
import com.neungi.data.entity.ScheduleReceive
import com.neungi.data.entity.ServerEvent
import com.neungi.data.entity.ServerReceive
import com.neungi.domain.model.Operation
import com.neungi.domain.model.ScheduleData
import com.neungi.moyeo.views.plan.adapter.LocalDateTimeAdapter
import kotlinx.coroutines.CompletableDeferred
import okhttp3.*
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

class WebSocketManager @Inject constructor() {

    var id: String = ""
    var tripId: Int = 0
    private var webSocket: WebSocket? = null
    var isConnected = false

    // 각 이벤트에 대한 콜백
    var onServerEventReceived: ((ServerReceive) -> Unit)? = null
    var onRouteEventReceived: ((PathReceive) -> Unit)? = null
    var onScheduleEventReceived: ((ScheduleReceive) -> Unit)? = null
    var onAddEventReceived: ((ScheduleData) -> Unit)? = null
    var onEditEventReceived: ((ManipulationEvent) -> Unit)? = null
    var onMemberEventReceived: ((List<Member>) -> Unit)? = null

    private val client = OkHttpClient()
    private val webSocketListener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            Timber.tag("WebSocket").d("onOpen: Connected")

            isConnected = true
            sendStart()

        }


        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val gson = GsonBuilder()
                    .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
                    .setPrettyPrinting()
                    .serializeNulls()
                    .create()

                // 먼저 String으로 들어온 JSON이 유효한지 체크
                if (text == "SUCCESS") {
                    Timber.d(text)
                    return
                }

                val jsonElement = JsonParser.parseString(text)

                // Handle JSON Object
                if (jsonElement.isJsonObject) {
                    val jsonObject = jsonElement.asJsonObject
                    if (!jsonObject.has("paths")) {
                        Timber.d("received $text")
                    }

                    when {
                        jsonObject.has("status") -> {
                            val serverReceive = gson.fromJson(text, ServerReceive::class.java)
                            onServerEventReceived?.invoke(serverReceive)
                        }

                        jsonObject.has("title") -> {
                            val scheduleReceive = gson.fromJson(text, ScheduleReceive::class.java)
                            onScheduleEventReceived?.invoke(scheduleReceive)
                        }

                        jsonObject.has("paths") -> {
                            val routeReceive = gson.fromJson(text, PathReceive::class.java)
                            onRouteEventReceived?.invoke(routeReceive)
                        }

                        jsonObject.has("placeName") -> {
                            val add = gson.fromJson(text, ScheduleData::class.java)
                            onAddEventReceived?.invoke(add)
                        }

                        jsonObject.has("dayOrder") -> {
                            val editedItem = gson.fromJson(text, ManipulationEvent::class.java)
                            onEditEventReceived?.invoke(editedItem)
                        }

                    }
                }
                else if (jsonElement.isJsonArray) {
                    val members = gson.fromJson<List<Member>>(text, object : TypeToken<List<Member>>() {}.type)
                    Timber.d("members "+members.toString())
                    onMemberEventReceived?.invoke(members)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse message: $text")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            isConnected = false
            Timber.e(t, "WebSocket Error: ${t.message}")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            isConnected = false
            Timber.tag("WebSocket").d("onClosed: Code=$code, Reason=$reason")
        }
    }

    private fun sendStart() {
        Timber.d(tripId.toString())
        sendMessage(
            ServerEvent(
                operationId = "123",
                tripId = tripId,
                operation = Operation(
                    action = "START",
                    scheduleId = id.toInt(), //userId
                    positionPath = -1
                ),
                timestamp = 1
            )
        )
    }

    fun connect(url: String) {
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, webSocketListener)

    }

    fun sendMessage(message: Any) {
        if (isConnected) {
            val gson = Gson()
            val jsonMessage = gson.toJson(message)
            Timber.d("Sending message: $jsonMessage")
            webSocket?.send(jsonMessage)
        } else {
            Timber.d("WebSocket is not connected.")
        }
    }

}
