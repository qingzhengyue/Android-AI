package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

/**
 * 阶段三 (任务 3.1 & 3.2)
 * PC 独立 Web 后台与 TurboWarp 可视化大屏服务
 * 在本地 8080 端口启动 HTTP 服务，供 PC 浏览器或局域网访问教师管理与作品大屏
 */
class WebPortalServer(private val context: Context) {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    suspend fun startServer(port: Int = 8080) = withContext(Dispatchers.IO) {
        if (isRunning) return@withContext
        try {
            serverSocket = ServerSocket(port)
            isRunning = true
            Log.d("WebPortalServer", "PC Web 后台已成功启动: http://127.0.0.1:$port/portal")

            while (isRunning) {
                val clientSocket = serverSocket?.accept() ?: break
                handleClientRequest(clientSocket)
            }
        } catch (e: Exception) {
            Log.e("WebPortalServer", "启动 PC Web 端口 $port 异常: ${e.message}")
        }
    }

    private fun handleClientRequest(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)

                val line = reader.readLine() ?: return@Thread
                if (line.startsWith("GET")) {
                    val htmlContent = getPortalHtml()
                    writer.println("HTTP/1.1 200 OK")
                    writer.println("Content-Type: text/html; charset=UTF-8")
                    writer.println("Content-Length: " + htmlContent.toByteArray(Charsets.UTF_8).size)
                    writer.println("Connection: close")
                    writer.println()
                    writer.println(htmlContent)
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("WebPortalServer", "处理 PC 浏览器请求异常: ${e.message}")
            }
        }.start()
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getPortalHtml(): String {
        return try {
            context.assets.open("web_portal.html").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head><meta charset="UTF-8"><title>星梭智学 PC 端</title></head>
            <body style="font-family: sans-serif; padding: 20px;">
              <h2>星梭智学 PC 电脑版已启动</h2>
              <p>请直接访问 Web 客户端首页。</p>
            </body>
            </html>
            """.trimIndent()
        }
    }
}
