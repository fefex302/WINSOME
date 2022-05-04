
import java.rmi.*;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;
import java.util.HashMap;



public class NotifyEventImplementation extends RemoteObject implements NotifyEventInterface{

	public ArrayList<String> status = new ArrayList<String>();
	private static final long serialVersionUID = 1L;

	public NotifyEventImplementation() {
		
	}
	public NotifyEventImplementation(ArrayList<String> status) {
		this.status = status;
	}

	@Override
	public synchronized void notifyNewFollower(String follower) throws RemoteException {
		status.add(new String(follower));
		System.out.println(follower + " ha iniziato a seguirti");
	}
	
	public synchronized void notifyRemoveFollower(String follower) throws RemoteException {
		status.remove(new String(follower));
		System.out.println(follower + " ha smesso di seguirti");
	}

}
