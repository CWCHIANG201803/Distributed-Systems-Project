# comp90015-assignment2b

# How to demo the system?

To build the code, in root direcory:
	(1) open a new terminal at the root directory
	(2) use command: package

The following lists the step to run the system.

1. create two subdirectories in the root directory: test1 and test2
	 mkdir test1 test2

2. copy all files to be shared to the directory test1: 
		cp ShareFile test1

3. To start the index server with password:
	(1). open a new terminal at the root directory
	(2). use the command:
		java -cp ./target/pb2b-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.IndexServer -password 1234

4. To start the file index upload peer:
	(1). open a new terminal at the root directory
	(2). switch to subdirectory test1
		cd test1
	(3). use the command:
		java -cp ../target/pb2b-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.FileSharingPeer -share File1.txt File2.txt test.jar

5. To start the peer querying files:
	(1). open a new terminal at the root directory
	(2). go to subdirectory test2
		cd ../test2
	(3). use the command, so the uploaded file will be downloaded to the subdirectory test2
		java -cp ../target/pb2b-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.FileSharingPeer -query File jar
   
6. To use the AdminClient to shutdown the server:
	(1) open a new terminal at the root directory
	(2). use command, this will gentally shutdown the server. other options are -force or -vader
		java -cp ./target/pb2b-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.AdminClient -password 1234 -shutdown -port 3101
