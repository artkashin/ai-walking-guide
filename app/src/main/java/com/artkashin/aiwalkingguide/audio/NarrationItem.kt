package com.artkashin.aiwalkingguide.audio

import com.artkashin.aiwalkingguide.tour.Poi

data class NarrationItem(val id: String,val poi: Poi?,val text: String,val kind: String="POI",val listened: Boolean=false,val title: String=poi?.title?:"Ответ гида")
