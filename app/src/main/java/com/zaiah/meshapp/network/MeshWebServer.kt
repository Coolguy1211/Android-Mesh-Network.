package com.zaiah.meshapp.network

import com.zaiah.meshapp.MeshApp
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import com.zaiah.meshapp.network.models.MeshPacket

class MeshWebServer(port: Int = 8080) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // Simple routing
        return when (uri) {
            "/api/status" -> serveStatus()
            "/api/nodes" -> serveNodes()
            "/api/routes" -> serveRoutes()
            "/api/chat" -> serveChat()
            "/api/send" -> handleSendChat(session)
            "/" -> serveDashboard()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }

    private fun serveChat(): Response {
        val jsonArray = JSONArray()
        MeshApp.instance.chatMessages.forEach {
            jsonArray.put(it)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
    }

    private fun handleSendChat(session: IHTTPSession): Response {
        try {
            session.parseBody(HashMap())
            val msg = session.parameters["msg"]?.get(0) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing msg parameter")
            
            val formattedMsg = "[Web UI]: $msg"
            val chatMsg = com.zaiah.meshapp.network.models.ChatMessage(
                senderId = "WebUI",
                message = msg,
                isSentByMe = false // Mark as remote since it came from web
            )
            MeshApp.instance.chatMessages.add(chatMsg)
            MeshApp.instance.chatListener?.invoke(chatMsg)
            
            MeshApp.instance.meshManager.sendToNode(
                "BROADCAST",
                formattedMsg.toByteArray(Charset.forName("UTF-8")),
                MeshPacket.PacketType.TEXT
            )
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Sent")
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message)
        }
    }

    private fun serveStatus(): Response {
        val json = JSONObject()
        json.put("isGateway", MeshApp.instance.isGateway)
        json.put("neighborCount", MeshApp.instance.neighbors.size)
        json.put("routeCount", MeshApp.instance.routes.size)
        return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
    }

    private fun serveNodes(): Response {
        val jsonArray = JSONArray()
        MeshApp.instance.neighbors.forEach {
            jsonArray.put(it)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
    }

    private fun serveRoutes(): Response {
        val jsonArray = JSONArray()
        MeshApp.instance.routes.values.forEach {
            val routeObj = JSONObject()
            routeObj.put("destination", it.destinationId)
            routeObj.put("nextHop", it.nextHopId)
            routeObj.put("hops", it.hopCount)
            routeObj.put("role", it.role.name)
            routeObj.put("stale", it.isStale)
            jsonArray.put(routeObj)
        }
        return newFixedLengthResponse(Response.Status.OK, "application/json", jsonArray.toString())
    }

    private fun serveDashboard(): Response {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Mesh Network Dashboard</title>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>
                    body { font-family: sans-serif; padding: 20px; max-width: 800px; margin: auto; background-color: #f4f4f9; }
                    .card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1); margin-bottom: 20px; }
                    h1, h2 { color: #333; }
                    pre { background: #eee; padding: 10px; border-radius: 4px; overflow-x: auto; }
                    table { width: 100%; border-collapse: collapse; margin-top: 10px; }
                    th, td { padding: 10px; border-bottom: 1px solid #ddd; text-align: left; }
                    th { background-color: #f8f8f8; }
                    #chat-box { height: 200px; overflow-y: scroll; background: #eee; padding: 10px; border-radius: 4px; margin-bottom: 10px; }
                    .chat-input { display: flex; gap: 10px; }
                    .chat-input input { flex-grow: 1; padding: 10px; border: 1px solid #ccc; border-radius: 4px; }
                    .chat-input button { padding: 10px 20px; background: #1565C0; color: white; border: none; border-radius: 4px; cursor: pointer; }
                </style>
            </head>
            <body>
                <h1>🌐 Mesh Dashboard</h1>
                
                <div class="card">
                    <h2>💬 Live Chat</h2>
                    <div id="chat-box"></div>
                    <div class="chat-input">
                        <input type="text" id="chat-msg" placeholder="Message the mesh..." onkeypress="if(event.key === 'Enter') sendChat()">
                        <button onclick="sendChat()">Send</button>
                    </div>
                </div>

                <div class="card">
                    <h2>Status</h2>
                    <pre id="status-box">Loading...</pre>
                </div>

                <div class="card">
                    <h2>Routes</h2>
                    <table id="routes-table">
                        <tr><th>Destination</th><th>Next Hop</th><th>Hops</th><th>Role</th></tr>
                    </table>
                </div>

                <script>
                    async function sendChat() {
                        const input = document.getElementById('chat-msg');
                        const msg = input.value;
                        if (!msg) return;
                        input.value = '';
                        await fetch('/api/send', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                            body: 'msg=' + encodeURIComponent(msg)
                        });
                        fetchData();
                    }

                    async function fetchData() {
                        try {
                            const chatRes = await fetch('/api/chat');
                            const chatJson = await chatRes.json();
                            const chatBox = document.getElementById('chat-box');
                            chatBox.innerHTML = chatJson.join('<br>');
                            chatBox.scrollTop = chatBox.scrollHeight;

                            const statusRes = await fetch('/api/status');
                            const statusJson = await statusRes.json();
                            document.getElementById('status-box').textContent = JSON.stringify(statusJson, null, 2);

                            const routesRes = await fetch('/api/routes');
                            const routesJson = await routesRes.json();
                            const table = document.getElementById('routes-table');
                            table.innerHTML = '<tr><th>Destination</th><th>Next Hop</th><th>Hops</th><th>Role</th></tr>';
                            routesJson.forEach(route => {
                                const row = `<tr>
                                    <td>${"$"}{route.destination}</td>
                                    <td>${"$"}{route.nextHop}</td>
                                    <td>${"$"}{route.hops}</td>
                                    <td>${"$"}{route.role}</td>
                                </tr>`;
                                table.innerHTML += row;
                            });
                        } catch(e) {
                            console.error("Error fetching data", e);
                        }
                    }
                    fetchData();
                    setInterval(fetchData, 2000);
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
