package com.example.myapplication2

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ShopListActivity : AppCompatActivity() {

    private lateinit var recyclerViewShops: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var textViewNoShops: TextView
    private lateinit var buttonBack: Button
    private lateinit var buttonRefresh: Button
    private lateinit var textViewCountdown: TextView
    private lateinit var adapter: ShopAdapter
    private val shopList = mutableListOf<Shop>()

    private val locationPermissionRequestCode = 1002
    private lateinit var firestore: FirebaseFirestore
    private val detectedSsids = mutableSetOf<String>()

    private lateinit var wifiManager: WifiManager
    private var lastScanTime: Long = 0

    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
            if (success) {
                handleScanResults()
            } else {
                Log.e("WiFiScan", getString(R.string.scan_failed))
                showNoWifiDetected()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_shop_list)

        recyclerViewShops = findViewById(R.id.recyclerViewShops)
        progressBar = findViewById(R.id.progressBar)
        textViewNoShops = findViewById(R.id.textViewNoShops)
        buttonBack = findViewById(R.id.buttonBack)
        buttonRefresh = findViewById(R.id.buttonRefresh)
        textViewCountdown = findViewById(R.id.textViewCountdown)

        buttonBack.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        recyclerViewShops.layoutManager = LinearLayoutManager(this)
        adapter = ShopAdapter(shopList) { selectedShop ->
            Log.d("ShopListActivity", "shopName to send: ${selectedShop.name}")
            val intent = Intent(this, MenuActivity::class.java)
            intent.putExtra("shopName", selectedShop.name)
            startActivity(intent)
        }
        recyclerViewShops.adapter = adapter

        firestore = FirebaseFirestore.getInstance()
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        buttonRefresh.setOnClickListener {
            refreshWifiScan()
            disableRefreshButtonWithCountdown()
        }

        refreshWifiScan()
    }

    private fun disableRefreshButtonWithCountdown() {
        buttonRefresh.isEnabled = false
        textViewCountdown.visibility = View.VISIBLE

        val timer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                textViewCountdown.text = getString(R.string.countdown_text, secondsRemaining)
            }

            override fun onFinish() {
                buttonRefresh.isEnabled = true
                textViewCountdown.visibility = View.GONE
            }
        }
        timer.start()
    }

    private fun refreshWifiScan() {
        progressBar.visibility = View.VISIBLE
        recyclerViewShops.visibility = View.GONE
        textViewNoShops.visibility = View.GONE
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionRequestCode
            )
        } else {
            startWifiScan()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWifiScan() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTime < 10000) {
            Toast.makeText(this, getString(R.string.try_again_later), Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            return
        }
        lastScanTime = currentTime

        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, getString(R.string.wifi_disabled), Toast.LENGTH_SHORT).show()
            showNoWifiDetected()
            return
        }
        progressBar.visibility = View.VISIBLE
        recyclerViewShops.visibility = View.GONE
        textViewNoShops.visibility = View.GONE

        try {
            val success = wifiManager.startScan()
            Log.d("WiFiScan", "startScan() 호출됨 → 성공 여부: $success")
            if (!success) {
                showNoWifiDetected()
            }
        } catch (e: SecurityException) {
            Log.e("WiFiScan", getString(R.string.scan_permission_error), e)
            showNoWifiDetected()
        }
    }

    private fun handleScanResults() {
        detectedSsids.clear()
        val scanResults = wifiManager.scanResults

        for (result in scanResults) {
            val ssid = result.SSID
            val rssi = result.level
            Log.d("WiFiScan", getString(R.string.wifi_info_log, ssid, rssi))

            if (rssi > -60 && ssid.isNotBlank()) {
                detectedSsids.add(ssid)
            }
        }

        if (detectedSsids.isNotEmpty()) {
            loadShopsMatchingNearbyWifi()
        } else {
            showNoWifiDetected()
        }
    }

    private fun loadShopsMatchingNearbyWifi() {
        firestore.collection("shops")
            .get()
            .addOnSuccessListener { result ->
                shopList.clear()

                for (document in result) {
                    val name = document.getString("name") ?: continue
                    val address = document.getString("address") ?: getString(R.string.no_address)
                    val ssid = document.getString("ssid") ?: continue

                    if (detectedSsids.contains(ssid)) {
                        shopList.add(Shop(name, address))
                        Log.d("ShopListActivity", getString(R.string.detected_shop, name, ssid))
                    }
                }

                adapter.notifyDataSetChanged()
                progressBar.visibility = View.GONE

                if (shopList.isEmpty()) {
                    recyclerViewShops.visibility = View.GONE
                    textViewNoShops.visibility = View.VISIBLE
                } else {
                    recyclerViewShops.visibility = View.VISIBLE
                    textViewNoShops.visibility = View.GONE
                }
            }
            .addOnFailureListener { e ->
                Log.e("ShopListActivity", getString(R.string.load_shop_failed), e)
                progressBar.visibility = View.GONE
                textViewNoShops.visibility = View.VISIBLE
                recyclerViewShops.visibility = View.GONE
            }
    }

    private fun showNoWifiDetected() {
        Toast.makeText(this, getString(R.string.no_wifi_detected), Toast.LENGTH_SHORT).show()
        progressBar.visibility = View.GONE
        recyclerViewShops.visibility = View.GONE
        textViewNoShops.visibility = View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationPermissionRequestCode &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startWifiScan()
        } else {
            Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            textViewNoShops.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(wifiScanReceiver)
    }
}
