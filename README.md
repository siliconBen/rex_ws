# rex_ws
Web server plus example REx controller using UCB1.

# instructions

This repository contains a web server with multiple possible configurations; a simple single-threaded client program to generate a workload for that web server; and a learning controller which uses a reinforcement learning algorithm and the PAL framework to control the current composition of the web server.

To run the basic learning system, you'll need three command prompts, as follows:

Command prompt 1:

    cd web_server
    dnc .
    dana pal.rest WebServer.o

Command prompt 2 (note there are multiple workload files, we just pick one of them here):

    cd client
    dnc .
    dana .\Client.o .\client_default.txt

Command prompt 3:

    cd learning
    dnc .
    dana Learning.o

When this is running, you will see output in all three windows which shows what the system is currently doing. Note the above assumes that all three programs are running on the same host; if the client is on a different host you'll need to update the IP address of the server in Client.dn


# how it works

The command dana pal.rest WebServer.o does two things:

First, it discovers every available composition of the web server system, by finding all possible implementations of each interface and their combinations. It then starts the web server in one of those compositions.

Second, it launches a web service endpoint at http://localhost:8008/meta/ via which you can control the current composition and get runtime metrics. The learning program connects to this endpoint to control the system, but you can also use any other programming language to connect to this web service endpoint and control the system.


# using the ground truth generator

When we're performing machine learning experiments, it's useful to have a ground truth - a known reward level for each possible action under each set of conditions. The ground truth tells us which action is best, and we can then measure things like how long it takes a learning algorithm to locate this known correct answer.

The stat_vis folder contains a simple program for gathering this ground truth data. It works by trying each action (composition of components) a set number of times, and reporting the average reward for that action.

You can run it in a very similar way to the above learning example, but we replace the learning element with the stat_vis program:

Command prompt 1:

    cd web_server
    dnc .
    dana pal.rest WebServer.o

Command prompt 2 (note there are multiple workload files, we just pick one of them here):

    cd client
    dnc .
    dana .\Client.o .\client_default.txt

Command prompt 3:

    cd stat_vis
    dnc .
    dana StatWriter.o out.txt

This will create a file out.txt in the stat_vis directory, which contains the ground truth data for each composition. The program will exit once it has tried every composition a given number of times. You can control how man times the stat writer program tries each composition, and how long it runs each composition for, using the ITERATION_COUNT and STAT_INTERVAL constants defined near the top of StatWriter.dn.
