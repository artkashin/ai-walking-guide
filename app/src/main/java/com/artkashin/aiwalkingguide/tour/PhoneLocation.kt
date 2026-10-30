package com.artkashin.aiwalkingguide.tour

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import android.os.SystemClock

class PhoneLocation(context: Context,private val update: (Position)->Unit,private val problem: (String)->Unit): LocationListener {
    private val app=context.applicationContext
    private val manager=app.getSystemService(LocationManager::class.java)
    fun start() {
        stop()
        val fine=ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        val coarse=ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(!fine && !coarse) { problem("Разрешите геолокацию, чтобы искать места рядом.");return }
        try {
            val providers=manager.getProviders(true).filter{it==LocationManager.NETWORK_PROVIDER || (fine && it==LocationManager.GPS_PROVIDER)}
            if(providers.isEmpty()) { problem("Включите геолокацию в настройках телефона.");return }
            providers.forEach{manager.requestLocationUpdates(it,5000,8f,this,Looper.getMainLooper())}
        } catch(_: SecurityException) { problem("Разрешение геолокации отозвано.") }
    }
    fun stop() { manager.removeUpdates(this) }
    override fun onLocationChanged(location: Location) {
        // No last-known or stale fixes are used to trigger a story.
        val age=(SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos)/1_000_000
        if(age !in 0..30000 || !location.hasAccuracy())return
        if(location.isFromMockProvider) problem("Используется тестовый источник геолокации.")
        update(Position(location.latitude,location.longitude,location.accuracy))
    }
    override fun onProviderDisabled(provider: String) { problem("Источник геолокации отключён. Ожидаем новую позицию.") }
    override fun onProviderEnabled(provider: String)=Unit
    @Deprecated("Legacy API") override fun onStatusChanged(provider: String?,status: Int,extras: Bundle?)=Unit
}
