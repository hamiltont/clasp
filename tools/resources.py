#!/usr/bin/env python3.3
#
# resources.py
# Monitor Clasp's resource consumption.

import argparse
import matplotlib.pyplot as plt
import pickle
import psutil
import signal
import sys

from time import sleep

sigCaught = False
delay = 0.1

def stopProfiling(signal, frame):
  global sigCaught
  print("Caught signal.")
  sigCaught = True
signal.signal(signal.SIGINT, stopProfiling)

def frange(x, y, jump):
  l = []
  while abs(x-y) > 1E-7:
    l.append(x)
    x += jump
  return l

class Resource:
  def __init__(self, name):
    self.name = name
    self.vms = []
    self.rss = []
  def __repr__(self):
    return "{{{0}: vms = {1}, rss = {2}}}".format(
        self.name, self.vms, self.rss)

def collectData(names):
  print("Collecting data.")
  resources = {}
  while not sigCaught:
    for ps in psutil.process_iter():
      if ps.name in names:
        pid = ps.pid
        if not pid in resources:
          resources[pid] = Resource(ps.name)
        mem = ps.get_memory_info()
        resources[pid].vms.append(mem.vms)
        resources[pid].rss.append(mem.rss)
    sleep(delay)

  return resources

def plot(resources):
  print('Creating plots.')
  plt.figure()
  plt.title("Clasp's memory overhead")
  plt.xlabel('Offset (seconds)')
  plt.ylabel('Memory consumption (bytes)')
  legend = []
  ax = plt.subplot(111)
  for pid in resources:
    resource = resources[pid]
    length = len(resource.vms)
    ax.plot(frange(0.0,length*delay,delay), resource.vms)
    ax.plot(frange(0.0,length*delay,delay), resource.rss)
    legend.append("{0} ({1}) - vms".format(resource.name, pid))
    legend.append("{0} ({1}) - rss".format(resource.name, pid))
  box = ax.get_position()
  ax.set_position([box.x0, box.y0, box.width * 0.8, box.height])
  plt.legend(legend, loc='center left', bbox_to_anchor=(1,0.5), fancybox=True,
      prop={'size':10})
  plt.savefig('memoryLoad.eps')

if __name__=='__main__':
  parser = argparse.ArgumentParser(description="Analyze memory usage.")
  parser.add_argument('-p', '--pickle', type=str, metavar='file',
      help="The location of the resource pickle to load.")
  args = parser.parse_args()

  if args.pickle:
    f = open('resources.pickle', 'rb')
    resources = pickle.load(f)
  else:
    f = open('resources.pickle', 'wb')
    resources = collectData(['java'])
    pickle.dump(resources, f)
  f.close()
  plot(resources)
