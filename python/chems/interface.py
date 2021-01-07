from flask import Flask
import redis
import os
import numpy as np

app = Flask(__name__)
global i, r, b 
b = 0

def waitForIdle():
    global r
    r.set("Command","stop")
    state = r.get("State")
    while state == "running":
        state = r.get("State")
    return
    
@app.route('/')
def index():
    return '1'

@app.route('/get_volume')
def get_volume():
    global r
    string = r.get("volume")
    return string

@app.route('/set_volume/<val>')
def set_volume(val):
    global r
    print("\n set volume to "+val+"\n")
    r.set("volume",val)
    return "1"
    
@app.route('/get_settings')
def get_settings():
    return '1,0,2,0,0,1,25,1,1,1,0,0,1'

@app.route('/set_setting/<key>/<value>')
def set_setting(key, value):
    return key + " set to " + value

@app.route('/get_spatial_data')
def get_spatial_data():
    global r, b
    data = r.mget(["Lat","Lon","Hei"])
    rawypr=str(r.get("YPR"),"utf-8").rstrip()
    ypr=rawypr.split('=')[1].split(',')
    ypr = ypr[0]+','+ypr[1]+','+ypr[2]
    string = ypr+","+str(data[0],"utf-8")+","+str(data[1],"utf-8")+"," + str(b)

    if (b == 0):
        b = 1
    else:
        b = 0

    print(string+"\n")
    return string

@app.route('/get_circuit_list')
def get_circuit_list():
    liste = os.listdir('circuits')
    string = liste[0]
    for i in range(1,len(liste)):
        string = string + "," + liste[i]
    print(string)
    return string
    
@app.route('/get_circuit_path/<index>')
def get_circuit_path(index):
    liste = os.listdir('circuits')
    string = liste[int(index)]
    r.set("circuit:nom",string)
    trajRef = np.load("circuits/"+string)
    length = np.size(trajRef,1)
    Rt = 6366198.
    #evalue la distance entre le premier et le dernier point
    d0 = Rt*abs(trajRef[0,length-1]-trajRef[0,0])+Rt*abs(trajRef[1,length-1]-trajRef[1,0]) + abs(trajRef[2,length-1]-trajRef[2,0])
    Lat = trajRef[0,0::20]
    Lon = trajRef[1,0::20]
    #circuit ferme?
    if d0<1:
        Lat = np.append(Lat,Lat[0])
        Lon = np.append(Lon,Lon[0])
    length = np.size(Lat)
    string = "["
    for i in range(length-1):
        string = string+"["+str(Lat[i])+","+str(Lon[i])+"],"
    string = string + "["+str(Lat[length-1])+","+str(Lon[length-1])+"]]"
    #print(string)
    return string
    
@app.route('/start_recording/<nom>/<mode>')
def start_record(nom,mode):
    global r
    r.mset({"circuit:collect":"1","circuit:mode":mode,"circuit:nom":nom})
    r.set("Command","record")
    print("Start recording ",nom,mode)
    return "1"
    
@app.route('/stop_recording')
def stop_record():
    r.set("circuit:collect","0")
    waitForIdle()
    print("Stop recording")
    return "1"

@app.route('/start_compass')
def start_compass():
    r.set("Command","compass")
    print("Start compass")
    return "1"

@app.route('/stop_compass')
def stop_compass():
    global r
    waitForIdle()
    print("Stop compass")
    return "1"
    
@app.route('/start_circuit/<num>')
def get_start_circuit(num):
    global r
    liste = os.listdir('circuits')
    nom = liste[int(num)]
    r.mset({"circuit:nom":nom,"Command":"stop"})
    r.set("Command","guide")
    print("Start circuit "+num)
    return "1"
    
@app.route('/stop_circuit')
def get_stop_circuit():
    global r
    waitForIdle()
    print("Stop circuit")
    return "1"

if __name__ == '__main__':
    i=0
    r=redis.Redis('localhost')
    r.mset({"Lat":"0.","Lon":"0.","Hei":"0."})
    r.mset({"ipos1":"0","ipos2":"0","ipos3":"0","vol1":"0.","vol2":"0.","vol3":"0."})
    r.set('YPR', "YPR=0,0,0,OK")
    r.mset({"circuit:nom":"Xstade","circuit:collect":"0","circuit:ferme":"1"})
    r.set("volume","50")
    r.set("Command","stop")
    app.run(debug=True, host='0.0.0.0')
