"""Minimale MIDI-parser: dumpt per track de noten (pitch, starttick, duur)."""
import struct, sys

PATH = "scripts/gezang011.mid"
data = open(PATH, "rb").read()

def rd_vlq(b, i):
    val = 0
    while True:
        c = b[i]; i += 1
        val = (val << 7) | (c & 0x7F)
        if not (c & 0x80):
            break
    return val, i

assert data[:4] == b"MThd", "Geen MIDI"
fmt, ntrk, div = struct.unpack(">HHH", data[8:14])
print(f"format={fmt} tracks={ntrk} division={div} (ticks per kwartnoot)")

pos = 14
NAMES = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]
for t in range(ntrk):
    assert data[pos:pos+4] == b"MTrk", f"Track {t} corrupt op {pos}"
    length = struct.unpack(">I", data[pos+4:pos+8])[0]
    end = pos + 8 + length
    i = pos + 8
    tick = 0
    status = None
    active = {}  # pitch -> start tick
    notes = []
    while i < end:
        dt, i = rd_vlq(data, i)
        tick += dt
        b0 = data[i]
        if b0 & 0x80:
            status = b0; i += 1
        # running status: status stays
        ev = status & 0xF0
        if ev == 0x90 or ev == 0x80:
            pitch = data[i]; vel = data[i+1]; i += 2
            if ev == 0x90 and vel > 0:
                active[pitch] = tick
            else:
                st = active.pop(pitch, None)
                if st is not None:
                    notes.append((st, pitch, tick - st))
        elif ev in (0xA0, 0xB0, 0xE0):
            i += 2
        elif ev in (0xC0, 0xD0):
            i += 1
        elif b0 == 0xFF:
            i += 1
            meta = data[i]; i += 1
            mlen, i = rd_vlq(data, i)
            i += mlen
        elif b0 in (0xF0, 0xF7):
            slen, i = rd_vlq(data, i)
            i += slen
        else:
            i += 1
    notes.sort()
    print(f"\n--- Track {t}: {len(notes)} noten ---")
    for st, p, dur in notes:
        print(f"  t={st:5d} dur={dur:4d} {NAMES[p%12]}{p//12-1} (midi {p})")
    pos = end
