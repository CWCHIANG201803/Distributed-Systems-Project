# comp90015-assignment2b

To build the code, in main direcory, use : mvn package

The following lists the step to run the system.
1. Index server. To run the index server, use this command:
    java -cp target/pb2b-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.IndexServer


2. For FileSharingPeer, there are two modes: 
    (a) update the file list (not the real file) to the index server (client+server). 
        For example, a peer can provide the list of shared files (file1.txt, file2.txt, and test.jar) to the index server:
        
        java -cp target/pb2b-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.FileSharingPeer -share ShareFile/file1.txt ShareFile/file2.txt ShareFile/test.jar

        Note: (1) the file name should include the host directory name. 
              (2) press enter to terminate this peer or later it will become a content provider.

	(b) query the file to the index server, and then turn to receive the real file from the peer
		For example, a query peer queries the file prefixing "file" and postfix "jar":
		Followed by (a), this client peer will receive file1.txt, file2.txt, and test.jar from the sharing peer.

		java -cp target/pb2b-0.0.1-SNAPSHOT-jar-with-dependencies.jar pb.FileSharingPeer -port 3300 -query file jar

		How to work?
		First, this peer will come to index server to ask the source and file, then the index server will provide the peer sharing the file. If there is nothing wrong, second, this peer will disconnect with index server and turn to get the file from the peer provided by the index server. Once tranmission is finished, this peer will also be terminated.
