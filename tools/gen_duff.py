#!/usr/bin/env python3
"""Synthesizes the reminder sounds res/raw/khatma_duff_1..4.wav (frame drum, 'dum' and 'tak'). Pure python, no samples,
so there is no third-party licence. Seeded noise: output is reproducible."""
import math, random, struct, wave, sys
RATE = 22050
def dum(n):  # low skin thump: decaying sine with a fast pitch drop + short slap
    out = [0.0] * n; ph = 0.0
    for i in range(n):
        t = i / RATE
        f = 85 + 85 * math.exp(-t * 28)
        ph += 2 * math.pi * f / RATE
        out[i] = math.sin(ph) * math.exp(-t * 9) * 0.95 + 0.0
    rnd = random.Random(1); lp = 0.0
    for i in range(int(0.02 * RATE)):
        lp += 0.35 * (rnd.uniform(-1, 1) - lp); out[i] += lp * math.exp(-i / (0.004 * RATE)) * 0.5
    return out
def tak(n):  # rim hit: short bright noise burst + ringing 420 Hz
    out = [0.0] * n; rnd = random.Random(2); lp = 0.0; ph = 0.0
    for i in range(n):
        t = i / RATE
        lp += 0.8 * (rnd.uniform(-1, 1) - lp)
        ph += 2 * math.pi * 420 / RATE
        out[i] = (lp * math.exp(-t * 90) * 0.8 + math.sin(ph) * math.exp(-t * 40) * 0.35)
    return out
PATTERN = [dum, tak, tak, dum]
def render(hits, gap=0.26):
    total = int(RATE * (gap * (hits - 1) + 0.45)); buf = [0.0] * total
    for h in range(hits):
        s = int(h * gap * RATE); snd = PATTERN[h](int(0.45 * RATE))
        for i, v in enumerate(snd):
            if s + i < total: buf[s + i] += v
    pk = max(abs(v) for v in buf) or 1
    return [v / pk * 0.9 for v in buf]
for n in range(1, 5):
    d = render(n)
    with wave.open(f"app/src/main/res/raw/khatma_duff_{n}.wav", "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, v)) * 32767)) for v in d))
