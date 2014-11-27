This focused on timing operations on emulators. I used commit 
[232498e9](https://github.com/hamiltont/attack/tree/232498e971c8bd8f7583cee8d8730c219f2e14fb)
to send 6 types of interactions to emulators. Each interaction 
was sent [1000 times](https://github.com/hamiltont/attack/blob/232498e971c8bd8f7583cee8d8730c219f2e14fb/src/clasp/clasp.scala#L135) to get a large sample. 
This commit needs no external prompting, once an emulator is 
launched this large stress test will begin. 

The first experiment used hardware acceleration, the second did
not, and then the graphic compares these two scenarios. 

## Measuring with VT-x

Used node 7 as master, node 8 as worker. Node 8 had VT-x and KVM 
enabled. 

Logs are in `logs-vtx`. The channel logs are stored in the timestamped
folder, including the relevant `_tasks.json` file. For now data is stored 
on Dropbox. 

The master was manually started using 
    
    $ screen -L -S clasp
    $ target/start --num-emulators 1 --pool 10.0.2.8
    # Detact using C-a d


## Measuring without VT-x

Used node 7 as master, node 8 as worker. Node 8 had no acceleration.  

Logs are in `logs-novtx`, and I forgot to record the master log
from 7. 
