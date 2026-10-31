package com.artkashin.aiwalkingguide

import com.artkashin.aiwalkingguide.voice.*
import org.junit.Assert.*
import org.junit.Test

class WakeStateMachineTest {
    @Test fun sessionWakeCommandTimeoutAndDuplicateGate() {
        val machine=WakeStateMachine()
        assertFalse(machine.wake(10000))
        machine.start(10000)
        assertEquals(VoicePhase.COOLDOWN,machine.tick(11999))
        assertTrue(machine.wake(12000))
        assertFalse(machine.wake(12001))
        assertEquals("расскажи подробнее",machine.command(" расскажи подробнее ",13000))
        assertNull(machine.command("повтор",13001))
        assertFalse(machine.wake(20000))
        machine.responseDone(20000)
        assertTrue(machine.wake(22000))
        assertEquals(VoicePhase.COOLDOWN,machine.tick(37000))
        assertNull(machine.command("слишком поздно",37001))
        machine.stop()
        assertEquals(VoicePhase.OFF,machine.tick(100000))
        assertFalse(machine.wake(100000))
    }
    @Test fun guideSpeechAndCallsMuteUntilCooldownThenAllowFreshWake() {
        val machine=WakeStateMachine()
        machine.start(0);assertTrue(machine.wake(2000))
        machine.setOutput(true,2100)
        assertEquals(VoicePhase.OUTPUT_MUTED,machine.tick(10000))
        assertNull(machine.command("эхо рассказа",10000))
        assertFalse(machine.wake(10000))
        machine.responseDone(10000)
        assertEquals(VoicePhase.OUTPUT_MUTED,machine.tick(10001))
        machine.setOutput(false,11000)
        assertFalse(machine.wake(12999))
        assertTrue(machine.wake(13000))
        machine.stop();machine.setOutput(true,13001);machine.start(14000)
        assertEquals(VoicePhase.OUTPUT_MUTED,machine.tick(20000))
    }
    @Test fun localCommandsStayLocalAndNavigationQuestionsGoToGuide() {
        assertEquals(LocalVoiceCommand.PAUSE,VoiceCommands.classify("Поставь на паузу!"))
        assertEquals(LocalVoiceCommand.STOP_AUDIO,VoiceCommands.classify("Стоп"))
        assertEquals(LocalVoiceCommand.RESUME,VoiceCommands.classify("Продолжи рассказ"))
        assertEquals(LocalVoiceCommand.END_TOUR,VoiceCommands.classify("Заверши прогулку"))
        assertEquals(LocalVoiceCommand.QUESTION,VoiceCommands.classify("Куда дальше?"))
        assertEquals(LocalVoiceCommand.QUESTION,VoiceCommands.classify("Расскажи об этом здании"))
        assertTrue(VoiceCommands.containsWake("Окей, гид! Куда дальше?","окей гид"))
        assertFalse(VoiceCommands.containsWake("окей гидра","окей гид"))
        assertEquals("куда дальше",VoiceCommands.stripWake("Окей, гид! Куда дальше?","окей гид"))
    }
    @Test fun temporaryPcmRingIsBoundedOrderedAndZeroedOnStop() {
        val ring=PcmRing(4)
        ring.add(shortArrayOf(1,2,3),3);ring.add(shortArrayOf(4,5,6),3)
        assertArrayEquals(shortArrayOf(3,4,5,6),ring.snapshot())
        ring.clear();assertArrayEquals(shortArrayOf(),ring.snapshot())
        ring.add(shortArrayOf(9),1);assertArrayEquals(shortArrayOf(9),ring.snapshot())
    }
}
