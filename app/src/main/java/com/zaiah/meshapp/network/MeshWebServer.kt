package com.zaiah.meshapp.network

import com.zaiah.meshapp.MeshApp
import fi.iki.elharo.nanohttpd.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject

class MeshWebServer(port: Int = 8080) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // Simple routing
        return when (uri) {
            "/api/status" -> serveStatus()
            "/api/nodes" -> serveNodes()
            "/api/routes" -> serveRoutes()
            "/" -> serveDashboard()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }
    }

    private fun serveStatus(): Response {
        val json = JSONObject()
        json.put("isGateway", MeshApp.instance.isGateway)
        json.put("nodeId", "TODO: Get Local ID")
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
                </style>
            </head>
            <body>
                <h1>🌐 Mesh Dashboard</h1>
                
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
                    async function fetchData() {
                        try {
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
                    setInterval(fetchData, 5000);
                </script>
            </body>
            </html>
        """.trimIndent()
        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }
}
