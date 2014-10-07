This generalizes the `checking_attack_bottleneck` work by launching 
80 emulators sequentially, separated by 8 minute intervals, 
on multiple attack nodes. We recorded node CPU, RAM, Swap metrics. 
Nodes were run in pairs, with odd nodes (e.g. 7,9,11) not having 
VT-x turned on and even nodes (e.g. 8,10,12) having VT-x turned
on. 

## Measuring with VT-x

All nodes got clean OS install of Ubuntu 12.04

The odd nodes were the master nodes, and the even ndoes were the workers. 
Pairs were 5-6,7-8,9-10, etc. Masters were `.host_clasp_novtx` and workers
were `.host_clasp_vtx`

For now data is stored on dropbox because it's 85MB. See
[here](https://dl.dropboxusercontent.com/u/6336312/Clasp/clasp_exp1.tgz)

The log from each clasp master is called `7.master.log`, and each 
worker log is called `8.worker.log`. The channel server logs are 
stored in a folder named after the launch time e.g. `2014_10_04-02_11-EDT`. 
Due to some errors with buffer flusing to disk, the channel logs are 
largely empty and the data needs be recovered from the master logs (which
thankfully print the full message to stdout whenever something arrives 
at the channel server). To print a nice graphic of all of this, we need
to extract the `_emulatormanager.json` log file from all of the master
logs and then run it through R. 

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

To recover the emulator data from the master logs, I used: 

    $ for i in `ls *.lot`
    do
    NUM=$(echo $i | grep -o -P '\d+')
    cat $i | grep "emulatormanager" | grep "Message" | grep -o -P '{.*}' > $NUM._emulatormanager.json
    done

## Measuring with VT-x

Cleaned `/tmp`, ensured `lsof -i -P` showed no remaining processes. Double-checked
VT-x was not enabled on any nodes with odd numbers. Masters were `.host_clasp_vtx`
and workers were `.host_clasp_novtx`. 

The even nodes were the master nodes, and the odd nodes were the worker nodes. 
For example, 5-6 pair, 7-8 pair, etc

Each master was manually started using the same process as above


`.host_clasp_novtx` need to mv /tmp/clasp/hamiltont/nohup.10.0.2.$i worker.$i.log

`.host_clasp_vtx` need to mv /tmp/screenlog.0 master.$i.log

`clasp/logs/2014_10_*`



