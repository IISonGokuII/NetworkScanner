package com.mycompany.networkscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.mycompany.networkscanner.fragments.*

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var toggle: ActionBarDrawerToggle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.open_drawer, R.string.close_drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        toggle.drawerArrowDrawable.color = ContextCompat.getColor(this, R.color.on_primary)

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> loadFragment(HomeFragment(), getString(R.string.home))
                R.id.nav_device_info -> loadFragment(DeviceInfoFragment(), getString(R.string.device_info))
                R.id.nav_wifi_scanner -> loadFragment(WifiScannerFragment(), getString(R.string.wifi_scanner))
                R.id.nav_port_scanner -> loadFragment(PortScannerFragment(), getString(R.string.port_scanner))
                R.id.nav_ping -> loadFragment(PingFragment(), getString(R.string.ping_tool))
                R.id.nav_traceroute -> loadFragment(TracerouteFragment(), getString(R.string.traceroute))
                R.id.nav_dns -> loadFragment(DnsLookupFragment(), getString(R.string.dns_lookup))
                R.id.nav_subnet -> loadFragment(SubnetScannerFragment(), getString(R.string.subnet_scanner))
                R.id.nav_wol -> loadFragment(WolFragment(), getString(R.string.wol))
                R.id.nav_speed -> loadFragment(SpeedTestFragment(), getString(R.string.speed_test))
                R.id.nav_http -> loadFragment(HttpHeaderFragment(), getString(R.string.http_headers))
                R.id.nav_whois -> loadFragment(WhoisFragment(), getString(R.string.whois_lookup))
            }
            drawerLayout.closeDrawers()
            true
        }

        requestPermissions()

        if (savedInstanceState == null) {
            loadFragment(HomeFragment(), getString(R.string.app_name))
            navView.setCheckedItem(R.id.nav_home)
        }
    }

    fun loadFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        supportActionBar?.title = title
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }
}
