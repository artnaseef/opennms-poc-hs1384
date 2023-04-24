========================================
POC for HS-1384: GRPC connections from Minion periodically jump up to 100+ then drop back down


* Symptoms:

	- Periodically the 

# RUN AGAINST CTF (Connect-Then-Fail)

	$ watch -n 1 'OUT="$(netstat -an | grep 9991)"; printf "== COUNT: "; echo "$OUT" | wc -l; echo; echo "$OUT"'
	$ java -jar poc-server/target/POC-HS-1384-server-1.0.0-SNAPSHOT.jar
	$ java -Dgrpc.port=9991 -jar poc-client/target/POC-HS-1384-client-1.0.0-SNAPSHOT.jar 

	*** DON'T LET THE CLIENT RUN FOR LONG!

	* It appears that 4 TCP connections are established per second
	* The old ones remain established as the new ones are created
