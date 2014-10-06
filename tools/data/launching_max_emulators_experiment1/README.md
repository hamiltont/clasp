This generalizes the `checking_attack_bottleneck` work by launching 
80 emulators sequentially, separated by 8 minute intervals, 
on multiple attack nodes. We recorded node CPU, RAM, Swap metrics. 
Nodes were run in pairs, with odd nodes (e.g. 7,9,11) being the 
clasp master and even nodes (e.g. 8,10,12) being the workers. 

For now data is stored on dropbox because it's 85MB. See
[here](https://dl.dropboxusercontent.com/u/6336312/Clasp/clasp_exp1.tgz)

The log from each clasp master is called `7.screenlog.lot`, and each 
worker log is called `8.worker.log`. The channel server logs are 
stored in a folder named after the launch time e.g. `2014_10_04-02_11-EDT`. 
Due to some errors with buffer flusing to disk, the channel logs are 
largely empty and the data needs be recovered from the master logs (which
thankfully print the full message to stdout whenever something arrives 
at the channel server). 
