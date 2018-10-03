# Grrrr

## Introduction

Grrrr is a peer to peer chatting application, implemented with Apache ZooKeeper, Maven and Google Protocol Buffers. By creating nodes with host, port and udpport information under same ZooKeeper server group, each user(peer) would be able to communicate with each other if they are using same protocol. Implemented both TCP and UDP: user can broadcast or send private messages to other nodes by using Raw Socket, and can also download history data from another chat peer with Datagram Socket. To ensuring reliable delivery of all data packet when streaming, we implemented Go-Back-N algorithm.

"Grrrr" is the sounds that bears communicate with each other, we are a bunch of students in California which is known as "Bear State", and this is how a bunch of grizzly bears talk! Also, Grrr has been taken so I have to add another 'r'.

The user interface of Grrrr is a command-line interface, simply input a command followed by some arguments. Here is an example:

![Grrrr](https://i.imgur.com/71GE6Ck.png)

## How to use

### Command to open Grrrr

```
$ java -cp project2.jar cs682.Chat -user <username> -port <port> -udpport <udpport>
```

### Command to explore nodes

```
>> list
```

### Command to send private message

```
>> send [username] "message content"
```

### Command to broadcast

```
>> broadcast "message content"
```

### Command to request a history data:

```
>> request [username]
```

**Notice that before running Grrrr, the ZooKeeper server and the parameters in MyZooKeeper class should be set up first.**

## Debug mode

### Command to open Grrrr with debug mode

```
$ java -cp project2.jar cs682.Chat -user <username> -port <port> -udpport <udpport> -debug
```

### Command to generate mock history data

```
>> mock <number>
```

**Under debug mode, user can easily track the entire process of data streaming.**

## References
* [University of San Francisco](https://www.usfca.edu/)
* [ZooKeeper](https://zookeeper.apache.org/)
* [Protocol Buffers](https://developers.google.com/protocol-buffers/)
* [Maven](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)
* [Imgur](https://imgur.com/)

## Acknowledgment

This is a course project, not using for any commercial purpose.

## Author and contributors

* **Brian Sung** - *Graduate student in department of Computer Science at University of San Francisco* - [LinkedIn](https://www.linkedin.com/in/ohbriansung/)
* **Dr. Rollins** - *Professor in department of Computer Science at University of San Francisco* - [page](http://srollins.cs.usfca.edu/)
