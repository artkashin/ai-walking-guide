package com.artkashin.aiwalkingguide.audio

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

@Composable
fun PersistentPlayer(state: PlaybackState,onPlay: ()->Unit,onPause: ()->Unit,onStop: ()->Unit,onSeek: (Long)->Unit) {
    var drag by remember(state.id) { mutableStateOf<Float?>(null) }
    Surface(tonalElevation=5.dp,modifier=Modifier.fillMaxWidth().testTag("persistent-player")) {
        Column(Modifier.padding(horizontal=16.dp,vertical=6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(state.title.ifBlank{"Плеер гида"},maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall)
            Text(state.phase.label+(if(state.listened)" · Прослушано" else ""),style=MaterialTheme.typography.bodySmall)
            if(state.phase!=PlaybackPhase.EMPTY) {
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    val playing=state.phase==PlaybackPhase.PLAYING
                    val ink=LocalContentColor.current
                    IconButton(onClick=if(playing)onPause else onPlay,enabled=state.phase!=PlaybackPhase.GENERATING && (state.phase!=PlaybackPhase.BUFFERING || state.durationMs>0),
                        modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{contentDescription=if(playing)"Пауза рассказа" else if(state.listened)"Повторить рассказ" else "Воспроизвести рассказ"}) {
                        Canvas(Modifier.size(24.dp)) {
                            if(playing) { drawRect(ink,Offset(size.width*.2f,size.height*.15f),Size(size.width*.2f,size.height*.7f));drawRect(ink,Offset(size.width*.6f,size.height*.15f),Size(size.width*.2f,size.height*.7f)) }
                            else drawPath(Path().apply{moveTo(size.width*.25f,size.height*.1f);lineTo(size.width*.9f,size.height*.5f);lineTo(size.width*.25f,size.height*.9f);close()},ink)
                        }
                    }
                    IconButton(onClick=onStop,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp).semantics{contentDescription="Остановить рассказ"}) {
                        Canvas(Modifier.size(24.dp)){drawRect(ink,Offset(size.width*.15f,size.height*.15f),Size(size.width*.7f,size.height*.7f))}
                    }
                }
                Text("${PlaybackTimeline.time((drag?.toLong()?:state.positionMs))} / ${PlaybackTimeline.time(state.durationMs)}",style=MaterialTheme.typography.bodySmall)
                Slider(value=(drag?:state.positionMs.toFloat()).coerceIn(0f,state.durationMs.coerceAtLeast(1).toFloat()),
                    valueRange=0f..state.durationMs.coerceAtLeast(1).toFloat(),enabled=state.canSeek,
                    onValueChange={drag=it},onValueChangeFinished={drag?.let{onSeek(it.toLong())};drag=null},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("narration-seek").semantics{contentDescription="Перемотка рассказа"})
            }
        }
    }
}
