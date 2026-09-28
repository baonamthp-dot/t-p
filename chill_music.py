import math, wave, struct

SR = 44100
BPM = 84
BEAT = 60 / BPM

notes = {"C4":261.63,"D4":293.66,"E4":329.63,"F4":349.23,"G4":392.00,"A4":440.00,"B4":493.88,"C5":523.25,"D5":587.33,"E5":659.25}

melody = [
    ("E4",1),("G4",1),("B4",2),("A4",1),("G4",1),("E4",2),
    ("D4",1),("F4",1),("A4",2),("G4",1),("F4",1),("D4",2),
    ("E4",1),("G4",1),("C5",2),("B4",1),("G4",1),("E4",2),
    ("D4",1),("E4",1),("G4",2),("E4",4)
]

def envelope(t, dur):
    attack = min(0.04, dur/5)
    release = min(0.25, dur/3)
    if t < attack: return t/attack
    if t > dur-release: return max(0, (dur-t)/release)
    return 1.0

samples = []
for name, beats in melody:
    dur = beats * BEAT
    f = notes[name]
    for i in range(int(dur * SR)):
        t = i / SR
        s = (0.68*math.sin(2*math.pi*f*t)
             + 0.18*math.sin(2*math.pi*2*f*t)
             + 0.05*math.sin(2*math.pi*3*f*t))
        samples.append(s * envelope(t, dur) * 0.28)

with wave.open("chill_music.wav", "wb") as out:
    out.setnchannels(1)
    out.setsampwidth(2)
    out.setframerate(SR)
    frames = b"".join(struct.pack("<h", int(max(-1,min(1,s))*32767)) for s in samples)
    out.writeframes(frames)

print("Created chill_music.wav")
