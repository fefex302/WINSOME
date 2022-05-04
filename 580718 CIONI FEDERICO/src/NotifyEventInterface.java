

import java.rmi.*;


public interface NotifyEventInterface extends Remote {
	
	public void notifyNewFollower(String follower) throws RemoteException;
	public void notifyRemoveFollower(String follower) throws RemoteException;
}
