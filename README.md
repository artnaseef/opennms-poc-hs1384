# HS-1384 POC

POC for HS-1384: GRPC connections from Minion periodically jump up to 100+ then drop back down


# Background

The OpenNMS Horizon Stream project is using GRPC for Minions to connect to the cloud as follows:

* Connections are long-lived and communication needs to be resilient to failures (middleboxes, network hiccups, etc.)
* The cloud will send messages to the Minion at any time; the Minion cannot predict the timing reliably
* Minions commonly send periodic messages to the cloud
* Minions must initiate the connections - the cloud cannot reach out to Minions to initiate the connection
* Minions need to make their best effort to stay connected to the cloud even when there are no messages to send to the cloud
  * At any time, the cloud may be waiting for the Minion to reconnect in order to push messages to the Minion
* Either party may initiate message sends to the other at any time

Below is the solution:

* Minions initiate GRPC connections to the cloud at startup
* A reconnect strategy attempts to force reconnect attempts when the underlying connection is down
* GRPC streams are used to support pushes in both directions
* The cloud periodically sends ping messages to the Minion
* The Minion periodically sends heartbeat messages to the cloud

The cloud is running in Kubernetes - all local testing is done with `kind`.
An ingress NGINX controller is proxying the GRPC connections to a custom gateway service.


History of problems:
* Occasionally the Minion losses connection to the cloud for a notable period of time, but eventually it reconnects.
* Running a local, overnight test, and recording the GRPC socket connections, a build-up of connections was observed
  * Over a period of around 1 hour the socket counts slowly go up,
  * That period is followed by another period of around 1 hour that appeared to be normal, with 1 connection from the Minion.
  * This cycle repeated multiple times
* This outcome is repeatable
* This test application was built to reproduce the leaking connections
  * Issues were not reproduced with the client directly connecting to a fully-implemented GRPC server
  * This includes attempts at forcing race conditions on calls to `ManagedChannel.getState(true)`
  * Using the CTF (Connect-Then-Fail) server, problems are reproduced


# BUILD

	$ mvn clean install


# CTF

	CTF (Connect-Then-Fail) server functionality is used to reproduce problems.
	In this mode, the server accepts the TCP connection from clients,
	then immediately sends back a GOAWAY HTTP2 packet (after sending the necessary setup packets).


# CTF SCENARIOS

	1. Netty with reconnect strategy periodically calling channel.getState(true)
	2. Netty long run (connection lingers after failure)
	3. OK HTTP long run
    4. Netty with multiple connections and no GRPC requests initiated


# GENERAL RUN AGAINST CTF (Connect-Then-Fail)

	# Watch the sockets
	$ watch -n 1 'OUT="$(netstat -an | grep 9991)"; printf "== COUNT: "; echo "$OUT" | wc -l; echo; echo "$OUT"'

	# Start the server
	$ java -jar poc-server/target/POC-HS-1384-server-1.0.0-SNAPSHOT.jar

	# Start the client
	$ java -Dgrpc.port=9991 -jar poc-client/target/POC-HS-1384-client-1.0.0-SNAPSHOT.jar 


# SCENARIO 1 - Netty with reconnect strategy periodically calling channel.getState(true)

* NOTES
  * Instructions here are for the client only; see the "GENERAL RUN AGAINST CTF" section above
  * **WARNING** don't run this test for long - it leaks connections rapidly
* FINDINGS
  * Every call to `getState(true)` creates a new connection (when the channel is in IDLE state)
  * Connections are never cleaned up by the client until the underlying socket is closed externally (i.e. by the O/S or server)
	
## Start the client
	$ java -Dgrpc.port=9991 -jar poc-client/target/POC-HS-1384-client-1.0.0-SNAPSHOT.jar --enable-reconnect-strategy=true --shutdown-delay=10_000


# SCENARIO 2 - Netty long run (connection lingers after failure)

* NOTES
  * Instructions here are for the client only; see the "GENERAL RUN AGAINST CTF" section above
* FINDINGS
  * One connection is created and remains connected indefinitely (until the 2 hour test terminates)

## Start the client
	$ java -Dgrpc.port=9991 -jar poc-client/target/POC-HS-1384-client-1.0.0-SNAPSHOT.jar --enable-reconnect-strategy=false --shutdown-delay=7200_000


# SCENARIO 3 - OK Http long run

* NOTES
  * Instructions here are for the client only; see the "GENERAL RUN AGAINST CTF" section above
* FINDINGS
  * The one connection is created and then cleaned up as expected
* CAVEATS
  * While this is promising, separate testing has shown problems still arise - the difference in conditions causing the problems is as-yet unknown
  
## Start the client
	$ java -Dgrpc.port=9991 -jar poc-client/target/POC-HS-1384-client-1.0.0-SNAPSHOT.jar --enable-reconnect-strategy=false --shutdown-delay=7200_000 --ok-http


# SCENARIO 4 - Netty with multiple connections and no GRPC requests initiated

* NOTES
  * Instructions here are for the client only; see the "GENERAL RUN AGAINST CTF" section above
  * The client does not make any calls to the GRPC service
  * The client's reconnect strategy makes multiple calls to `getState(true)`
    * Each call creates a connection
  * The client connections are never removed
* FINDINGS
  * Connections leak

## Start the client
	$ java -Dgrpc.port=9991 -jar poc-client/target/POC-HS-1384-client-1.0.0-SNAPSHOT.jar --enable-reconnect-strategy=true --shutdown-delay=7200_000 --netty-http --num-iteration=0 --reconnect-rate=1_000 --max-reconnect-attempts=3 
