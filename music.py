import math, wave, struct

# "Neon Dream" - instrumental melody
# Run: python music.py
# Creates neon_dream.wav (44.1 kHz stereo)

SR = 44100
BPM = 112
BEAT = 60 / BPM

notes = {
    "C4":261.63,"D4":293.66,"E4":329.63,"F4":349.23,"G4":392.00,
    "A4":440.00,"B4":493.88,"C5":523.25,"D5":587.33,"E5":659.25,
    "F5":698.46,"G5":783.99,"A5":880.00
}

melody = [
    ("E4",1),("G4",1),("A4",2),("G4",1),("E4",1),("D4",2),
    ("E4",1),("G4",1),("C5",2),("B4",1),("A4",1),("G4",2),
    ("E4",1),("G4",1),("A4",1),("C5",1),("D5",2),("C5",2),
    ("A4",1),("G4",1),("E4",2),("D4",1),("E4",3)
]

def envelope(t, dur):
    attack = min(0.03, dur/4)
    release = min(0.12, dur/3)
    if t < attack:
        return t/attack
    if t > dur-release:
        return max(0, (dur-t)/release)
    return 1.0

samples = []
for name, beats in melody:
    dur = beats * BEAT
    f = notes[name]
    n = int(dur * SR)
    for i in range(n):
        t = i / SR
        # Soft synth tone: fundamental + harmonics
        s = (0.72*math.sin(2*math.pi*f*t)
             + 0.20*math.sin(2*math.pi*2*f*t)
             + 0.08*math.sin(2*math.pi*3*f*t))
        s *= envelope(t, dur) * 0.42
        samples.append(s)

with wave.open("neon_dream.wav", "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(SR)
    frames = b"".join(struct.pack("<h", max(-1,min(1,s))*32767) for s in samples)
    out.writeframes(frames)

print("Created neon_dream.wav")
