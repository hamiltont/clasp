#!/usr/bin/env python3.3
#
# out-parser.py
# Parse Clasp's output.
#
# Brandon Amos
# 2013.05.05

import sys # Arguments.
import re # Regex operations.
import matplotlib.pyplot as plt # Plotting.
import os.path # File extension extraction.

# `time` is of format: MM/DD/YYYY HH:MM:SS.MMM
def toMinutes(time):
  time = re.split('[ /:]', time)
  for i in range(0,len(time)):
    time[i] = float(time[i])
  day = time[0]*365.25/12.0+time[1]+time[2]*365.25
  return day*24+time[3]+time[4]/60.0+time[5]/(60.0*60.0)

# `time` is of format: HH:MM:SS.MMM
def toSeconds(time):
  time = re.split(':', time)
  for i in range(0,len(time)):
    time[i] = float(time[i])
  return time[2]+time[1]*60.0+time[0]*60.0*60.0

dateRegex = re.compile("^\[INFO\] \[([^\]]*)\]")
def parseDate(line):
  return dateRegex.search(line).group(1)

secondsRegex = re.compile(".*([0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3}).*")
def parseSeconds(line):
  return secondsRegex.search(line).group(1)

if len(sys.argv) != 2:
  print("Usage: ./parse.py <output-file>")
  exit(42)

outFile = sys.argv[1]
outFileName = os.path.splitext(outFile)[0]
f = open(outFile, "r")

for line in f:
  if "RemoteServerStarted" in line:
    startTime = toMinutes(parseDate(line))
    break

clientDelays = []
firstClientSeconds = None
for line in f:
  if "RemoteClientStarted" in line:
    if not firstClientSeconds:
      firstClientSeconds = toSeconds(parseSeconds(line))
    clientDelays.append(60*(toMinutes(parseDate(line))-startTime))

plt.figure()
plt.title('Successful remote client initialization delays')
plt.xlabel('Delay (seconds)')
plt.ylabel('Clients online')
plt.plot(clientDelays, range(1,len(clientDelays)+1), color='k')
plt.savefig(outFileName + '-timeToGoOnline.png')

f.seek(0)
msRegex = re.compile("Profiled [0-9]* in ([0-9]*) ms")
taskDelays = []
for line in f:
  if "Profiled " in line:
    taskDelays.append(float(msRegex.search(line).group(1))/(1000*60*60))

plt.figure()
plt.title('Time to profile applications')
plt.xlabel('Delay (hours)')
plt.ylabel('Applications profiled')
plt.plot(taskDelays, range(1,len(taskDelays)+1), color='k')
plt.savefig(outFileName + '-timeForTasks.png')

f.seek(0)
emuDelays = []
for line in f:
  if "emulators awake" in line:
    emuDelays.append(toSeconds(parseSeconds(line)) - firstClientSeconds)

plt.figure()
plt.title('Time to boot emulators')
plt.xlabel('Delay (seconds)')
plt.ylabel('Emulators booted')
plt.plot(emuDelays, range(1,len(emuDelays)+1), color='k')
plt.savefig(outFileName + '-timeForEmulators.png')

f.close()
