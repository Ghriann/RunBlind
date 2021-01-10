'''
Emulate a sinusoidal run around the athletic tracks at polytechnique
Update the Redis database
'''
import math
import redis
import time
import numpy as np

global Rt, cosphi

def loadTraj(str):
    # Chargement du circuit
    global cosphi
    path = "circuits/"
    Traj = np.load(path+str)
    nbTraj = np.size(Traj,1)
    cosphi = np.cos(np.radians(sum(Traj[0,:])/nbTraj))
    return Traj

def heading(X1,X2):
    global cosphi
    #Computes the heading of someone at Lat1,Lon1 looking at Lat2,Lon2
    return -np.arctan2(cosphi*(X2[1,]-X1[1,]),X2[0,]-X1[0,])
    
def perp(X):
    Y = X
    Y[0],Y[1]=-X[1],X[0]
    return Y
    
# Initialize the database
r=redis.Redis('localhost')
# Read the reference track
nom = str(r.get("circuit:nom"),"utf-8")
Traj = loadTraj(nom + ".npy")
nbTraj = np.size(Traj,1)
idx = -1
amp = 0.5 # amplitude of oscillations
om = 2*np.pi/10 # frequency of the oscillations

while True:
    idx = (idx + 1)%nbTraj
    idxp1 = (idx + 10)%nbTraj
    yaw = int(180/np.pi*heading(Traj[:,idx],Traj[:,idxp1]))
    X = Traj[:,idx]
    Lat = f"{X[0,]:.6f}"
    Lon = f"{X[1,]:.6f}"
    Hei = int(X[2,])
    ypr = "YPR="+str(yaw)+",0,0,OK"
    r.mset({"Lat":Lat,"Lon":Lon,"Hei":Hei,"YPR":ypr})
    time.sleep(0.3)
    print(idx)

