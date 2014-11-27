This was a test to see how many emulats could be run on the 
ataack nodes before they failed. The nodes used were 
10.0.2.17 for master and 10.0.2.18 for worker. b18 had 
VT-x enabled and emualtors ran with full hardware virtualization 
support. 

There was an 8-minute step between launching emulators to allow
the system to return to steady state, and we output the result 
of http://ROOT/emulators after each launch to ensure that the 
proper number of emulators had started. 

This test only launched up to 20 emualtors, as that took ~3 hours. 
Turns out we can support a lot more, as shown by the excel and the 
simple regressions. Looks like we need to launch 60-70 before the 
ram bottleneck hits. There are also swap metrics in the node metrics
json file, but they are useless as we did not hit that limit here