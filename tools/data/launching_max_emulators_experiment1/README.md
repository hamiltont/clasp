This generalizes the `checking_attack_bottleneck` initial experiment
by launching 80 emulators sequentially, separated by multi-minute intervals, 
on multiple attack nodes. We recorded node CPU, RAM, Swap metrics. 
Nodes were run in pairs, with odd nodes (e.g. 7,9,11) not having 
VT-x turned on and even nodes (e.g. 8,10,12) having VT-x turned
on. 

The odd nodes were the master nodes, and the even nodes were the workers. 
Pairs were 5-6,7-8,9-10, etc. Masters were `.host_clasp_novtx` and workers
were `.host_clasp_vtx`


All nodes got clean OS install of Ubuntu 12.04


## Measuring VT-x properly with one master-worker pair

Becuase the below results were messed up, I re-ran one trial for VT-x. 

Used node 7 as the master, and node 8 as the worker. Node 8 had 
acceleration enabled. Used 3-minute step between emulators as that
was approximately a safety factor of 4 based on initial results. 


## Measuring with VT-x

Data files are in `vtx` folder, with masters called `<host>.master.log` and
VT-x enabled workers called `<host>.worker.log`. 

An initial experiment with just 7/8 was carried out to ensure VT-x was 
working as expected. This is `7-initial.master.log` and folder 
`2014_10_08-00_50-EDT`. There's nothing wrong with the data gathered, so 
I included it into the plots I generated for experiment 1. 

Because I launched the master/worker pairs within one minute of each 
other, and one minute is the min granularity for the channel log storage
folder, a number of masters shared the same channel log files. There were
only two log folders generated, `2014_10_08-05_37-EDT` and `2014_10_08-05_38-EDT`. 
I had to manually correct some writing errors in the `_emulatormanager.json`
files when reading in. 

For now data is stored on dropbox because it's 300MB

Machines 5..32 were used, although some of them appear to have failed.
For example, machine 6 hung when reinstalling the OS and therefore 
5 and 6 were not used

Each master was manually started using 
    
    $ screen -L -S clasp
    $ target/start --num-emulators 0 --pool 10.0.2.30
    # Detact using C-a d
    $ screen -L -S step
    $ cd tools
    $ ./stepped-launch.sh http://10.0.2.29:8080

## Measuring without VT-x

There are two "runs" of data present. The first run is totally normal, it was 
basically an accident. I thought I had enabled VT-x, but I had not, so I 
accidentally gathered a full set of data on no-VT-x without realizing it and 
moved on to create run 2 as well. 

Run2 experienced an error where the channel manager logs were not being 
flushed to disk frequently and a number of the master logs for 
`_emulatormanager.json` were empty. Luckily the channel server prints all 
messages received to stdout and this was all captured by the screenlog. I 
was able to recover the `_emulatormanager.json` contents for all masters 
and put them inside the `recovered_logs` folder. 

To recover the emulator data from the master logs, I used: 

    $ for i in `ls *.lot`
    do
    NUM=$(echo $i | grep -o -P '\d+')
    cat $i | grep "emulatormanager" | grep "Message" | grep -o -P '{.*}' > $NUM._emulatormanager.json
    done

All data was used. Because I have basically two full data collections for no-vtx I
have a lot more data points for this case. 


