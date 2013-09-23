#!/usr/bin/env python3.3
#
# log-info.py
# Parse client logs and display info.
#
# Brandon Amos
# 2013.09.23

import argparse
import os
import re
import sys

# `time` is of format: HH:MM:SS.MMM
def toSeconds(time):
  time = re.split(':', time)
  for i in range(0,len(time)):
    time[i] = float(time[i])
  return time[2]+time[1]*60.0+time[0]*60.0*60.0

def parseHeartbeat(log, responseTimes):
  heartbeatSent = {}
  f = open(log, 'r')
  for line in f:
    s = re.search('([0-9:.]*).*Sending heartbeat to (emulator-[0-9]*)', line)
    if s:
      heartbeatSent[s.group(2)] = toSeconds(s.group(1))
    s = re.search(
      '([0-9:.]*).*Finished executing.*(emulator-[0-9]*) shell echo', line)
    if s:
      responseTimes.append(toSeconds(s.group(1))-heartbeatSent[s.group(2)])
  f.close()

if __name__=='__main__':
  parser = argparse.ArgumentParser(description="Parse client logs.")
  parser.add_argument('logDir', type=str,
    help='Directory with logs.')
  args = parser.parse_args()

  responseTimes = []
  for log in os.listdir(args.logDir):
    parseHeartbeat(args.logDir + '/' + log, responseTimes)
  print("Collected {0} heartbeats with an average latency of {1} seconds."
    .format(len(responseTimes), sum(responseTimes)/float(len(responseTimes))));
