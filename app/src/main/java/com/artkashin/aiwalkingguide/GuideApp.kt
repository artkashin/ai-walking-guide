package com.artkashin.aiwalkingguide

import android.app.Application
import com.artkashin.aiwalkingguide.tour.TourController

class GuideApp: Application() {
    val guide by lazy { TourController(this) }
}
