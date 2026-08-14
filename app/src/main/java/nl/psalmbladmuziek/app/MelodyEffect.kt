package nl.psalmbladmuziek.app

/**
 * Effect-registers die over de klinkende mix heen werken en zelf geen pijpstem zijn.
 * Losgekoppeld van [MelodyTimbre] zodat effecten en klinkende stemmen niet door elkaar lopen.
 */
enum class MelodyEffect {
    /** Amplitude-tremulant: winddruk-/volume-undulatie over de actieve mix. */
    TREMULANT
}
