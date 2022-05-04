

import java.rmi.*;

public interface NotifyEventServerInterface extends Remote{

	public void registerForCallback(NotifyEventInterface clientint, String client) throws RemoteException;
	
	public void unregisterForCallback(String client) throws RemoteException;
	
}
