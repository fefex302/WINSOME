

import java.nio.ByteBuffer;

public class Attachment {
	
	public boolean loginStatus = false;
	public ByteBuffer bb = ByteBuffer.allocate(1024*64);
	public String loggedAs;
	public Attachment() {
	}

}
