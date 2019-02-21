#!/usr/bin/env python3

# Embeded Systems
# Coursework 1 "IoT"
# Group: Carina
# Sensors: 2 Ultrasonic LV-MaxSonar-EZ
# ADC: ADS1115
# LED: 2
# Product: crib monitor

import math
import smbus
import json
from time import sleep
import paho.mqtt.client as mqtt
import RPi.GPIO as GPIO


# Setting GPIO pins
GPIO.setmode(GPIO.BCM)
GPIO.setup(22, GPIO.OUT)
GPIO.setup(24, GPIO.OUT)
GPIO.setwarnings(False)

#setup for non-encrypted broker (for demo)
#BROKER_ADDRESS = "ee-estott-octo.ee.ic.ac.uk"
#PORT = 1883

#setup for encrypted broker
BROKER_ADDRESS = "test.mosquitto.org"
PORT = 8884

BUS_ADDRESS = 0x48
CONFIG_REG_ADR = 0x01
CONV_REG_ADR = 0x00
CONTROL_BITS_S1 = [0x56, 0x83]
CONTROL_BITS_S2 = [0x66, 0x83]
THRESHOLD = 10 
RAW_DATA_SCALING = 0.012402718400000002


# Instantiate bus for the serial data and a client for the broker
bus = smbus.SMBus(1)
client = mqtt.Client()
client.connected_flag = False

#setting up encryption
client.tls_set(ca_certs="/home/pi/mosquitto.org.crt", certfile="/home/pi/client.crt",keyfile="/home/pi/client.key")

# Initial state 0 = "Monitoring OFF" vs 1 = "Monitoring ON"
state = 0

# Attempt connect
def attempt_connect():
	try:
		client.connect(BROKER_ADDRESS,port=PORT)
		client.connected_flag=True
	except:
		pass


# Defining action when message is received on the subscribed topics
def on_message(client, userdata, message) :

	global state
	global crib_length_1
	global crib_length_2

	if message.payload == b'monitorOn':
		state = 1
		# State changed to 1 = "Monitoring ON"
	elif message.payload == b'calibrate':
		state = 'calibrate'
		# State changed to "calibrate"
	elif (message.payload == b'monitorOff'):
		state = 0
		# State changed to 1 = "Monitoring ON"
	elif message.payload == b'calibrateDone':
		state = 0
		# State changed to 1 = "Monitoring ON" after calibration is done
	else:
		pass # if message is unknown


# Defining action when the connection to the broker is established
def on_connect(client, userdata, flags, rc):

        if rc==0:
            client.connected_flag = True #set flag
        else:
            attempt_connect()

# Defining action on disconnect -> try to reconnect
def on_disconnect(client, userdata, rc):

	attempt_connect()


client.on_message = on_message
client.on_connect = on_connect
client.on_disconnect = on_disconnect


# Connecting to the broker
while not client.connected_flag: #wait in loop

        attempt_connect()
        sleep(1)


# Reading the previously measured crib dimensions from a file
path = '/home/pi/crib.txt'

try: # to open file and read dimensions
	f=open(path, "r")
	crib_arr = f.read().split()
	crib_length_1 = float(crib_arr[0])
	crib_length_2 = float(crib_arr[1])
	f.close()

except: # in case the file is not found or the parsing failed assign default 0 lengths
	crib_length_1 = 0.0
	crib_length_2 = 0.0


# Orange LED on pin 24 is on for 2 seconds to indicate the broker has connected
# and the script is running as expected (no more risk of out-of-script error cause)
GPIO.output(24, GPIO.HIGH)
sleep(2)
GPIO.output(24, GPIO.LOW)


# subscribe to topic and start listening
client.subscribe("IC.embedded/Carina/mode")
client.loop_start()



# function to write and read register with sensor data
def write_read_i2c_scale (control_bits):

	# write then read from registers
	bus.write_i2c_block_data(BUS_ADDRESS, CONFIG_REG_ADR, control_bits)
	sleep(0.5)
	data = bus.read_i2c_block_data(BUS_ADDRESS, CONV_REG_ADR, 2)
	int_data = int.from_bytes(data, 'big')

	# scaling the raw data with the voltage reference step
	distance_cm = int_data * RAW_DATA_SCALING
	return distance_cm


# Calibration with removing anomalies in sensor reading
def calibrate():

	# Red LED to check if calibration started
	GPIO.output(22, GPIO.HIGH)


	global crib_length_1
	global crib_length_2

	crib_length_10_1 = [] # array of 10 measurements for sensor 1
	crib_length_10_2 = [] # array of 10 measurements for sensor 1

	# Taking median on the middle 6 values of an ordered array of 10 measurements
	# per sensor
	for i in range(1,11):

		#### Sensor 1 ####
		# write then read and scale data from register for sensor 1
		distance_cm = write_read_i2c_scale (CONTROL_BITS_S1)
		crib_length_10_1.append(distance_cm)

		#### Sensor 2 ####
		# write then read and scale data from register for sensor 2
		distance_cm_2 = write_read_i2c_scale (CONTROL_BITS_S2)
		crib_length_10_2.append(distance_cm_2)

		sleep(0.5)

	# Sorting the arrays
	crib_length_10_1.sort()
	crib_length_10_2.sort()

	# Taking the 6 middle values
	crib_median_1 = crib_length_10_1[3:7]
	crib_median_2 = crib_length_10_2[3:7]

	# Average of those 6 values
	crib_length_1 = sum(crib_median_1)/len(crib_median_1)
	crib_length_2 = sum(crib_median_2)/len(crib_median_2)

	####save to file#####

	try:
		f=open(path, "w")
		crib_str_1 = str(crib_length_1)
		crib_str_2 = str(crib_length_2)
		temp_in = crib_str_1 + " " + crib_str_2
		f.write(temp_in)
		f.close()

	except:
		pass

	# Red LED switches off to show end of calibration
	GPIO.output(22, GPIO.LOW)

	display_lengths = "Crib length:{}cm, Crib width:{}cm".format(crib_length_1, crib_length_2)
	client.publish("IC.embedded/Carina/calibrateResult", bytes(display_lengths, 'utf-8'))

	sleep(1)

	client.publish("IC.embedded/Carina/mode", bytes('calibrateDone','utf-8'))

	# Change state to "Monitoring OFF"
	state = 0

	sleep(1)

###################### MAIN TOP LEVEL LOOP #####################################
while(1):

	# If crib length is 0, go to calibrate state
	if (crib_length_1==0 or crib_length_2==0):
		state = 'calibrate'

	if (state == 'calibrate'):
		calibrate()
		state=0

	####### Monitoring ON mode loop ########
	while((state ==  1)):


		### Sensor 1 ###
		# write then read and scale data for sensor 1
		distance_cm_1 = write_read_i2c_scale (CONTROL_BITS_S1)


		### Sensor 2 ###
		# write then read amd scale data for sensor 2
		distance_cm_2 = write_read_i2c_scale (CONTROL_BITS_S2)


		# Check if distance difference is above treshold
		if ((distance_cm_1 < crib_length_1 - THRESHOLD) or (distance_cm_2 < crib_length_2 - THRESHOLD)):
			client.publish("IC.embedded/Carina/sleep", bytes('1','utf-8'))


		sleep(5)


client.loop_stop()