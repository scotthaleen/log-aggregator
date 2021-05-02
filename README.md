# Log Aggregator Prototype


![diagram](docs/architecture_diagram.png "Architecture Diagram")

# Components

## Log Aggregator Server

Log Aggregator Server is fairly simple, it's primary responsibility is receiving log data from the log forwarder
and persisting to a key value store. It builds a composite key of "host+file+line" and a value of the content
on that line.  This allows for immutable and eventually consistent logs.  If duplicate data is received it is
simply overwritten.  If data is missing the log forwarder can resend the entire file data with out worries of duplications.

For the purpose of a prototype the server also serves the log data back. These would ideally be 2 different servers in
a production environment to allow independent scaling of Reads and Writes. Since the log aggregator server is mainly
focused on writing data.  The client retrieval of the data from the store is responsible for sorting the data and
recreating the log file content.


## Log Forwarder

The log forwarder has much more complexity, for the prototype it is simplified to running one instance per log file.
It is currently stateless but could be changed to store offsets that have been processed and add logic to pickup where
it left off processing.  Currently it starts at the beginning of the file and sends all the content of a file.

The Log Forwarder utilizes [core.async](https://github.com/clojure/core.async) to separate concerns as well as
allow for back pressure if the aggregator server is being slow or intermittent.

 * the log reading process starts in its own go block. It will read a line from the log file (if it is not at the end
   of the file already) When it has a new line it will _put_ the line on the channel.  As part of the back pressure
   if the channels buffer is full. It will _park_ until the _put_ has completed.  This prevents it from flooding
   down stream with messages that can not be processed in a timely manner.

 * there is a partition channel between the log reader and the sender.  This is just to poll up a batch size to send.
   for the proto type the batch size is 2, So you only need to write 2 lines to a log file to send off a batch.

 * the log forwarding process reads from the partitioner channel, it will park if there is nothing on the channel.
   When it receives a batch off the channel. It attempts to send the batch to the log aggregator server. If it succeeds
   it is free to read another batch from the channel.  If it fails it will retry sending, each time with a decaying
   time out until the max retry has been reached.

   Notes on error handling - for a prototype there is no mechanism for retrying failed batches
   They could be pushed off to another channel or file to be retried later. I like the idea of the system going in to a
   bit of a full _park_ if none of the forwarders are able to send data.  But this also risks locking up the system
   if some sort of bad batch of data comes through.



![log-forwarder](docs/log-forwarder.png "log forwarder")




# Installation

* Install [leiningen](https://leiningen.org/) for building and to simplify running

## Build

To build an executable jar, run:

```sh
$ lein uberjar
```

This will produce an executable jar in the `target` directory. <br />
Example: `target/log-aggregator-standalone.jar`


## Usage

Starting a **Aggregator Server**
```
$ lein server

# or from java
$ java -cp log-aggregator-*-standalone.jar scotthaleen.log.aggregator.app
```

Starting a **Log Forwarder**
```
$ lein forwarder -- http://localhost:3000/store/batch <log_file>

# or from java
$ java -cp log-aggregator-*-standalone.jar scotthaleen.log.aggregator.log_forwarder http://localhost:3000/store/batch <log_file>
```


## Running an example

To run an example you will need to have 4 shells open to this project directory.

 - **Shell #1** - will be used to run the aggregator server
 - **Shell #2, #3**  are for tailing different log files
 - **Shell #4** - we will use to setup and issue commands


In **Shell #1** start the server

```
$ lein server
11339 [main] INFO  s.log.aggregator.server.pedestal - Starting Server
```

Verify in **Shell #4** with curl or in the browser that the server is running
```
$ curl localhost:3000/ruok
imok%
```

You should received `imok` and see the request hit the server, something like below in the server shell
```
Request GUID:  #uuid "3e980646-84e7-4859-b07c-f061d456b287"
```

Now, in **Shell #4** create some files to forward, below will create 2 files `test1.txt` and `test2.txt`

```
for i in {1..10}; do echo $i >> test1.txt; done;
for i in {100..110}; do echo $i >> test2.txt; done;
```

In shells **#3** and **#4** start a forwarder on the files

**Shell #3** forwards `test1.txt`
```
lein forwarder -- http://localhost:3000/store/batch test1.txt
```

**Shell #4** forwards `test4.txt`
```
lein forwarder -- http://localhost:3000/store/batch test2.txt
```


Once the **forwarder** starts running it will read the contents of the file and publish it too the aggregator
server.  It then watches the files for updates.


Now that we have the system running, we can view the logs on the server. Find the `hostname` of your machine

```
$ hostname
scotts-macbook.home
```

You should also be able to see the `:host scotts-macbook.home,` in the log messages of shells **#3** and **#4**
also, note the fully qualified path to the file that is being forwarded.  We need these variables to lookup
the file from the aggregator server.  You should be able to see the full path in the shells as well.

For example:

```
3791 [async-dispatch-3] DEBUG s.log.aggregator.log-forwarder - POSTING:  {:size 2, :batch ({:uuid 7f07277c-2272-482f-a7de-9f8297608bb3, :host scotts-macbook.home, :file /Users/scott/Source/log-aggregator/test2.txt, :line-num 6, :content 106} {:uuid 13d02388-7856-4071-86da-dbad4fe402cd, :host scotts-macbook.home, :file /Users/scott/Source/log-aggregator/test2.txt, :line-num 7, :content 107})}
```

This shows we are looking for **host=scotts-macbook.home** and **file=/Users/scott/Source/log-aggregator/test2.txt**


Assuming everything is working properly we should be able to download the files
@ [http://localhost:3000/store/log?host=scotts-macbook.home&file=/Users/scott/Source/log-aggregator/test1.txt]
@ [http://localhost:3000/store/log?host=scotts-macbook.home&file=/Users/scott/Source/log-aggregator/test2.txt]

Just replace the _host={}_ and _file={}_


Now that this is all working we can also watch for updates. Lets updated `test1.txt`

In **Shell #4**
```
$ echo "hello\nworld" >> test1.txt
```

This should trigger the forwarder to pickup the new lines and send them to the aggregator.  If we refresh the page
for [http://localhost:3000/store/log?host=scotts-macbook.home&file=/Users/scott/Source/log-aggregator/test1.txt]
you will see the new content



## License

Copyright © 2018
