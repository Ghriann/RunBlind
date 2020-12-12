import numpy as np
import os

path = os.path.abspath(os.path.dirname(__file__)) + "\\"

Lat = np.load(path + "XstadeLat.npy")
Lon = np.load(path + "XstadeLon.npy")

outString = "["

for i in range(100):
    outString += "[" + str(Lat[i*8]) + "," + str(Lon[i*8]) + "],"

print(outString[:-1] + "]")