#!/usr/bin/env python3
import math
import smbus
from time import sleep
import json
import paho.mqtt.client as mqtt
import RPi.GPIO as GPIO

GPIO.setmode(GPIO.BCM)
GPIO.setup(22, GPIO.OUT)
GPIO.setup(24, GPIO.OUT)
GPIO.setwarnings(False)

bus = smbus.SMBus(1)
client = mqtt.Client()


state = 0 #baby is awake, do not monitor
path = '/home/pi/crib.txt'

#reading
f=open(path, "r")
crib_arr = f.read().split()
crib_length_1 = float(crib_arr[0])
crib_length_2 = float(crib_arr[1])
f.close()


#BROKER_ADDRESS = "test.mosquitto.org"
BROKER_ADDRESS = "ee-estott-octo.ee.ic.ac.uk"
#client.tls_set(ca_certs="/home/pi/mosquitto.org.crt", certfile="/home/pi/client.crt",keyfile="/home/pi/client.key")

#try:
#client.connect(BROKER_ADDRESS,port=8884)
client.connect(BROKER_ADDRESS,port=1883)
#except:
	#print("needs to reconnect")

GPIO.output(24, GPIO.HIGH)
sleep(2)
GPIO.output(24, GPIO.LOW)


def on_message(client, userdata, message) :
	global state
	global crib_length_1
	global crib_length_2
	#print("Received message:{} on topic{}".format(message.payload, message.topic))
	if message.payload == b'monitorOn':
		state = 1
		#print("state changed to 1 start monitoring")
	elif message.payload == b'calibrate':
		state = 'calibrate'
		
	elif (message.payload == b'monitorOff'):
		state = 0
		#print("state changed to 0 stop monitoring")

	elif message.payload == b'calibrateDone':
		#client.publish("IC.embedded/Carina/calibrateResult", bytes(crib_length_1.to_string(), 'utf-8'))
		#client.publish("IC.embedded/Carina/calibrateResult", bytes(crib_length_2.to_string(), 'utf-8'))
		
		print("calibrate done message received") 
	else:
		print("ERROR: unknown message from app")
		#exit()

client.on_message = on_message
client.subscribe("IC.embedded/Carina/mode")
client.loop_start()

def calibrate():

	global crib_length_1
	global crib_length_2

	GPIO.output(22, GPIO.HIGH) #LED to check calibrate entered
	print ("calibration started")

	crib_length_10_1 = []
	crib_length_10_2 = []
	for i in range(1,11):
		#write then read data from register
		bus.write_i2c_block_data(0x48, 0x01, [0x56, 0x83])
		sleep(0.5)
		data = bus.read_i2c_block_data(0x48, 0x00, 2)
		int_data = int.from_bytes(data, 'big')
		distance_cm = (int_data*0.00488296)*2.54
		crib_length_10_1.append(distance_cm)

		bus.write_i2c_block_data(0x48, 0x01, [0x66, 0x83])
		sleep(0.5)
		data_2 = bus.read_i2c_block_data(0x48, 0x00, 2)
		int_data_2 = int.from_bytes(data_2, 'big')
		distance_cm_2 = (int_data_2*0.00488296)*2.54
		crib_length_10_2.append(distance_cm_2)
		
		sleep(0.5)

	crib_length_10_1.sort()
	crib_length_10_2.sort()
	crib_median_1 = crib_length_10_1[3:7]
	crib_median_2 = crib_length_10_2[3:7]
	crib_length_1 = sum(crib_median_1)/len(crib_median_1)
	crib_length_2 = sum(crib_median_2)/len(crib_median_2)
	print ("length 1 is", crib_length_1, "length 2 is", crib_length_2)
	

	####save to file#####
	f=open(path, "w")
	crib_str_1 = str(crib_length_1)
	crib_str_2 = str(crib_length_2)
	temp_in = crib_str_1 + " " + crib_str_2
	f.write(temp_in)
	f.close()
	GPIO.output(22, GPIO.LOW)
	display_lengths = "Crib length:{}cm, Crib width:{}cm".format(crib_length_1, crib_length_2)
	client.publish("IC.embedded/Carina/calibrateResult", bytes(display_lengths, 'utf-8'))
	sleep(1)	
	client.publish("IC.embedded/Carina/mode", bytes('calibrateDone','utf-8'))
	state = 0 #query this? do we need?
	sleep(1)
 

#client.publish("IC.embedded/Carina/sleep", bytes('autorun','utf-8')) #mqtt publish
while(1): 

	#client.publish("IC.embedded/Carina/sleep", bytes('autorun','utf-8')) #mqtt publish test
	#GPIO.output(22, GPIO.LOW)
	#sleep(3)

	if (crib_length_1==0 or crib_length_2==0):
		state = 'calibrate'
	
	if (state == 'calibrate'):
		calibrate()
		state=0
	
	while((state ==  1)):
	
		#write then read data from register
		bus.write_i2c_block_data(0x48, 0x01, [0x56, 0x83])
		sleep(0.5)
		data = bus.read_i2c_block_data(0x48, 0x00, 2)
		int_data = int.from_bytes(data, 'big')
		distance_cm_1 = (int_data*0.00488296)*2.54

		bus.write_i2c_block_data(0x48, 0x01, [0x66, 0x83])
		sleep(0.5)
		data = bus.read_i2c_block_data(0x48, 0x00, 2)
		int_data = int.from_bytes(data, 'big')
		distance_cm_2 = (int_data*0.00488296)*2.54

		#add new measuremnet to distance data 
		#distancedata.append(distance_cm_1)
	
		#update json
		#payload = json.dumps({'name':'distance', 'distance_record':distancedata})

		#print(json.dumps({'name':'distance', 'distance_record':distancedata}))
		#print distancedata
		#print (distance_cm)

		if ((distance_cm_1 < crib_length_1-10) or (distance_cm_2 < crib_length_2-10)):
			#client.publish("IC.embedded/Carina/sleep", bytes(distance_cm_1,'utf-8'))
			client.publish("IC.embedded/Carina/sleep", bytes('1','utf-8'))
			#print(distance_cm_1, "and", distance_cm_2)
		sleep(5)


client.loop_stop()



