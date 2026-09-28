import math, wave, struct

SR = 44100
BPM = 58
BEAT = 60 / BPM

notes = {"C4":261.63,"D4":293.66,"E4":329.63,"F4":349.23,"G4":392.00,"A4":440.00,"B4":493.88,"C5":523.25,"D5":587.33,"E5":659.25}

melody = [
    ("C4",2),("E4",2),("G4",2),("E4",2),
    ("D4",2),("F4",2),("A4",2),("F4",2),
    ("C4",2),("E4",2),("G4",2),("C5",2),
    ("B4",2),("G4",2),("E4",2),("C4",4)
]

def envelope(t, dur):
    attack = min(0.25, dur/4)
    release = min(0.8, dur/3)
    if t < attack: return t/attack
    if t > dur-release: return max(0, (dur-t)/release)
    return 1.0

samples = []
for name, beats in melody:
    dur = beats * BEAT
    f = notes[name]
    for i in range(int(dur * SR)):
        t = i / SR
        s = (0.82*math.sin(2*math.pi*f*t)
             + 0.12*math.sin(2*math.pi*2*f*t))
        samples.append(s * envelope(t, dur) * 0.24)

with wave.open("sleep_music.wav", "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(SR)
    frames = b"".join(struct.pack("<h", int(max(-1,min(1,s))*32767)) for s in samples)
    out.writeframes(frames)

print("Created sleep_music.wav")
