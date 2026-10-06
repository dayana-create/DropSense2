package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private var esp32Url = "http://192.168.1.50"
    private val isDiscovering = AtomicBoolean(false)

    private lateinit var txtLevel: TextView
    private lateinit var txtPump: TextView
    private lateinit var txtMode: TextView
    private lateinit var progressLevel: LinearProgressIndicator
    
    private lateinit var viewLoading: View
    private lateinit var tvLoadingMessage: TextView
    private lateinit var viewError: View
    private lateinit var viewEmpty: View
    private lateinit var mainContent: View

    private val executor = Executors.newSingleThreadScheduledExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        txtLevel = findViewById(R.id.txtLevel)
        txtPump = findViewById(R.id.txtPump)
        txtMode = findViewById(R.id.txtMode)
        progressLevel = findViewById(R.id.progressLevel)
        
        viewLoading = findViewById(R.id.viewLoading)
        tvLoadingMessage = findViewById(R.id.tvLoadingMessage)
        viewError = findViewById(R.id.viewError)
        viewEmpty = findViewById(R.id.viewEmpty)
        mainContent = findViewById(R.id.mainContent)

        findViewById<MaterialButton>(R.id.btnOn).setOnClickListener { sendCommand("/api/pump?state=on") }
        findViewById<MaterialButton>(R.id.btnOff).setOnClickListener { sendCommand("/api/pump?state=off") }
        findViewById<MaterialButton>(R.id.btnAuto).setOnClickListener { sendCommand("/api/mode?mode=auto") }
        findViewById<MaterialButton>(R.id.btnRetry).setOnClickListener { 
            showLoading("Intentando reconectar...")
            getStatus() 
        }

        showLoading("Conectando con DropSense...")
        executor.scheduleWithFixedDelay({ 
            if (!isDiscovering.get()) {
                getStatus() 
            }
        }, 0, 3, TimeUnit.SECONDS)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLoading(message: String) {
        runOnUiThread {
            tvLoadingMessage.text = message
            viewLoading.visibility = View.VISIBLE
            viewError.visibility = View.GONE
            viewEmpty.visibility = View.GONE
        }
    }

    private fun showError() {
        runOnUiThread {
            viewLoading.visibility = View.GONE
            viewError.visibility = View.VISIBLE
            mainContent.visibility = View.GONE
        }
    }

    private fun showContent() {
        runOnUiThread {
            viewLoading.visibility = View.GONE
            viewError.visibility = View.GONE
            mainContent.visibility = View.VISIBLE
        }
    }

    private fun getStatus() {
        Thread {
            try {
                val url = URL("$esp32Url/api/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()
                    val json = JSONObject(response)
                    runOnUiThread {
                        updateInterface(json.getInt("level"), json.getBoolean("pump"), json.getString("mode"))
                        showContent()
                    }
                } else {
                    startDiscovery()
                }
                connection.disconnect()
            } catch (e: Exception) {
                startDiscovery()
            }
        }.start()
    }

    private fun startDiscovery() {
        if (isDiscovering.getAndSet(true)) return

        showLoading("Buscando dispositivo en la red...")
        
        Thread {
            try {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val ipAddress = wifiManager.connectionInfo.ipAddress
                if (ipAddress != 0) {
                    val baseIp = Formatter.formatIpAddress(ipAddress).substringBeforeLast(".")
                    
                    var found = false
                    for (i in 1..254) {
                        if (found) break
                        val testIp = "$baseIp.$i"
                        try {
                            val url = URL("http://$testIp/api/status")
                            val conn = url.openConnection() as HttpURLConnection
                            conn.connectTimeout = 150
                            conn.readTimeout = 150
                            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                                esp32Url = "http://$testIp"
                                found = true
                                runOnUiThread { showContent() }
                            }
                            conn.disconnect()
                        } catch (e: Exception) {}
                    }
                    if (!found) showError()
                } else {
                    showError()
                }
            } catch (e: Exception) {
                showError()
            } finally {
                isDiscovering.set(false)
            }
        }.start()
    }

    private fun sendCommand(endpoint: String) {
        Thread {
            try {
                val url = URL("$esp32Url$endpoint")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 2000
                connection.responseCode
                connection.disconnect()
                getStatus()
            } catch (e: Exception) {}
        }.start()
    }

    private fun updateInterface(level: Int, pump: Boolean, mode: String) {
        txtLevel.text = "$level%"
        progressLevel.setProgress(level.coerceIn(0, 100), true)
        txtPump.text = if (pump) "ON" else "OFF"
        txtMode.text = mode.uppercase()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}