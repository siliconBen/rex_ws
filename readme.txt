Source code to reproduce experiments from the USENIX OSDI 2016 paper:

"REX: A Development Platform and Online Learning Approach for Runtime Emergent Software Systems"
By Porter, Grieves, Rodrigues and Leslie

This package contains:
 - our PAL framework (note this is also in the standard Dana release; here we give the version used in the paper)
 - our statistical linear bandits learning algorithm
 - our emergent web server which is assembled by PAL
 - our client program which generates workloads for the web server


 ----- First steps -----

You will need to install Dana v173 for your platform. All experiments were done on Ubuntu 14.04.

Later versions of Dana may also work, but we cannot gaurantee that they will have the same characteristics.

In the web_server/htdocs directory there is a Dana program GenerateContent.dn

This program generates additional resources for different workloads, based on existing content in htdocs.

You need to compile it using a command prompt:

dnc GenerateContent.dn

And then run it for each file in htdocs, for example:

dana GenerateContent image_big.jpg


 ------ Adaptation Experiments -----

Our first set of experiments in the paper (Section 3.1) report on runtime adaptation characteristics.

Data for setConfig() was acquired by modifying the Assembly.dn program in the "pal" folder to place timing
code at the start and end of the setConfig() function (using Calendar.getTime()).

After compiling this modified version of Assembly, we then run the system from a command line in the "pal" folder:

dana rest ..\web_server\WebServer.o

And the cycle controller:

dana ControlCycle

We collect and average the printed data when the web server is both idle and under load.

Data for the remaining adaptation operators was acquired using a specially modified version of the Dana VM; this
is the only element of our experimental evaluation that it is not currently possible to generally repeat.


 ----- Ground truth experiments -----

Our ground truth experiments (Section 3.2) measure which compositions of our web server are optimal under which
client workloads.

This data is acquired as follows. From a command line in the "pal" folder, run our cycling controller:

dana MetaCycleStats ..\web_server\WebServer.o

This will try every configuration for 50 seconds, writing all perception data to the given file every 10 seconds.

From the "client" folder, start the client program with a workload of your choice, for example:

dana Client client_big_img.txt

Please note that you will need to modify the server IP address in the client program if your web server is remote.

We have also provided a program which parses this output file format to report averaged and best configurations:

dana StatParser big_img_stats.txt


 ----- Learning experiments -----

Our learning experiments (Section 3.3) measure how quickly our learning approach locates the ground-truth optimal.

These experiments require you to run PAL, the learning algorithm, and a client workload.

First, run PAL from a command line in the "pal" folder:

dana rest ..\web_server\WebServer.o

Next, run the learning algorithm from a command line in the "learning_regression" folder:

java -classpath .;commons-math3-3.5.jar Learning

Finally, run a client workload of your choice from a command line in the "client" folder:

dana Client client_med_txt.txt


 - that's all! Please do contact us if you have any questions about any of the above.