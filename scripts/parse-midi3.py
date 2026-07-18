"""Isoleer de bovenstem (sopraan/melodie) van track 1 uit de MIDI, per frase."""
import struct
from collections import defaultdict

PATH = "scripts/gezang011.mid"
data = open(PATH, "rb").read()
N = len(data)

def rd_vlq(b, i):
    val = 0
    while i < len(b):
        c = b[i]; i += 1
        val = (val << 7) | (c & 0x7F)
        if not (c & 0x80):
            break
    return val, i

fmt, ntrk, div = struct.unpack(">HHH", data[8:14])
NAMES = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"]

def track_notes(track_index):
    pos = 14
    for t in range(ntrk):
        if data[pos:pos+4] != b"MTrk":
            break
        length = struct.unpack(">I", data[pos+4:pos+8])[0]
        end = min(pos + 8 + length, N)
        if t == track_index:
            i = pos + 8; tick = 0; status = None; active = {}; notes = []
            while i < end:
                dt, i = rd_vlq(data, i); tick += dt
                if i >= end: break
                b0 = data[i]
                if b0 & 0x80:
                    status = b0; i += 1
                ev = (status or 0) & 0xF0
                if b0 == 0xFF:
                    i += 1
                    if i >= end: break
                    i += 1; mlen, i = rd_vlq(data, i); i += mlen
                elif b0 in (0xF0, 0xF7):
                    slen, i = rd_vlq(data, i); i += slen
                elif ev in (0x90, 0x80):
                    if i+1 >= end: break
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
                else:
                    i += 1
            return notes
        pos = end
    return []

for tr in range(ntrk):
    notes = track_notes(tr)
    if not notes:
        continue
    by_tick = defaultdict(list)
    for st, p, dur in notes:
        by_tick[st].append((p, dur))
    top = [(st,) + max(by_tick[st]) for st in sorted(by_tick)]
    phrases, cur, prev_end = [], [], None
    for st, p, dur in top:
        if prev_end is not None and st - prev_end >= div:
            phrases.append(cur); cur = []
        cur.append((p, dur)); prev_end = st + dur
    if cur: phrases.append(cur)
    print(f"\n=== TRACK {tr} bovenstem: {len(top)} noten, {len(phrases)} frases ===")
    for idx, ph in enumerate(phrases):
        s = " ".join(f"{NAMES[p%12]}{p//12-1}{'h' if dur>=div*2 else ('q' if dur>=div else 'e')}" for p,dur in ph)
        print(f"F{idx+1} [{len(ph)}]: {s}")
