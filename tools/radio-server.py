#!/usr/bin/env python3.3
#
# Provides a modem to communicate with the Android emulator
# reference Radio Interface Layer (RIL).
#
# References:
# https://android.googlesource.com/platform/hardware/ril/+/android-4.3.1_r1/reference-ril/reference-ril.c
# http://www.forensicswiki.org/wiki/AT_Commands
#
# Brandon Amos
# 2013.10.27
import socket

TCP_IP = '127.0.0.1'
TCP_PORT = 10000
BUFFER_SIZE = 20

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.bind((TCP_IP, TCP_PORT))
s.listen(1)

conn, addr = s.accept()
print('Connection address:', addr)
while True:
  data = conn.recv(BUFFER_SIZE).decode()
  if not data: break
  data = data.strip()
  if not len(data): continue
  print("Received '" + data + "'")

  response = ""
  if data.startswith('AT'):
    data = data[2:]
    if data == 'E0Q0V1': # Echo off, Q0, verbose on
      response = 'ATE0Q0V1'
    elif data.startswith('+'):
      data = data[1:]
      if data == 'CTEC?':
        response = "+CTEC: 6" #TODO
      elif data == "WNAM":
        response = "+WNAM: 1" #TODO
      elif data == '+CREG=2' or data == '+CGREG?':
        response = "+CREG: 1,2,2" #TODO
      elif data == 'CFUN?':
        response = '+CFUN: 1' #TODO
      elif data == '+CGSN':
        response = '+GSN: 299B5900' #TODO: IMEI of the phone.
      elif "COPS" in data:
        response = '+COPS: 0,2,"20601",2'

  response += "\r\nOK\r\n"
  print("Responding with '" + str(response.strip()) + "'")
  conn.send(response.encode())
conn.close()
