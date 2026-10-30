package com.artkashin.aiwalkingguide

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.artkashin.aiwalkingguide.tour.PhoneLocation
import com.artkashin.aiwalkingguide.tour.Position
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class PhoneLocationTest {
    @Test fun replayRejectsOldFixAndDeliversFreshGpsWithoutNetwork() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use{input->input.readBytes()}} }
        shell("pm grant ${context.packageName} android.permission.ACCESS_COARSE_LOCATION")
        shell("pm grant ${context.packageName} android.permission.ACCESS_FINE_LOCATION")
        shell("appops set ${context.packageName} android:mock_location allow")
        val manager=context.getSystemService(LocationManager::class.java)
        val result=AtomicReference<Position>();val received=CountDownLatch(1)
        val source=PhoneLocation(context,{result.set(it);received.countDown()},{})
        try {
            @Suppress("DEPRECATION")
            manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,1,1)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true)
            instrumentation.runOnMainSync{source.start()}
            fun fix(age: Long)=Location(LocationManager.GPS_PROVIDER).apply {
                latitude=51.508;longitude=-0.128;accuracy=10f;time=System.currentTimeMillis()-age
                elapsedRealtimeNanos=SystemClock.elapsedRealtimeNanos()-age*1_000_000
            }
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,fix(60000))
            assertFalse("Stale replay must not trigger narration",received.await(1,TimeUnit.SECONDS))
            manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,fix(0))
            assertTrue("Fresh replay fix must reach the guide",received.await(15,TimeUnit.SECONDS))
            assertEquals(51.508,result.get().lat,0.00001)
        } finally {
            instrumentation.runOnMainSync{source.stop()}
            try { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }catch(_: Exception){}
            shell("appops set ${context.packageName} android:mock_location default")
        }
    }
}
