package com.karelisio.arpege.theory.training

import com.karelisio.arpege.theory.Clef
import com.karelisio.arpege.theory.Note
import com.karelisio.arpege.theory.Pitch
import kotlin.random.Random

/** Lecture de notes : niveaux 1 = sur la portée, 2 = lignes supplémentaires, 3 = altérations. */
object SolfegeTrainer {
    const val LEVELS = 3

    fun stepRange(level: Int): IntRange = if (level >= 2) -4..12 else 0..8

    fun question(clef: Clef, level: Int, random: Random, previous: Pitch? = null): Pitch {
        while (true) {
            val range = stepRange(level)
            val step = random.nextInt(range.first, range.last + 1)
            val natural = clef.pitchAt(step)
            val acc = if (level >= 3 && random.nextInt(3) == 0) (if (random.nextBoolean()) 1 else -1) else 0
            val p = Pitch(Note(natural.note.letter, acc), natural.octave)
            if (p != previous) return p
        }
    }

    /** Réponse par nom de note : lettre et altération exactes (octave non demandée). */
    fun checkName(target: Pitch, answer: Note): Boolean = target.note == answer

    /** Réponse au clavier : même classe de hauteur (enharmonie acceptée). */
    fun checkKey(target: Pitch, midi: Int): Boolean = Math.floorMod(midi - target.midi, 12) == 0
}
