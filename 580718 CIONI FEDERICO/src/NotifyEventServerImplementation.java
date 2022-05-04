

import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;


public class NotifyEventServerImplementation extends RemoteObject implements NotifyEventServerInterface{

	private static final long serialVersionUID = 1L;
	
	private HashMap<String,NotifyEventInterface> clients;
	
	public NotifyEventServerImplementation() {
		super();
		clients = new HashMap<String,NotifyEventInterface>();
	}


	public void registerForCallback(NotifyEventInterface ClientInterface,String iduser) throws RemoteException {
		if(!clients.containsKey(iduser)) {
			clients.put(iduser, ClientInterface);
			System.out.println("User " + iduser + " registrato per le callbacks");
		}
		
	}

	
	public void unregisterForCallback(String iduser) throws RemoteException {
		if (clients.remove(iduser) != null){
			System.out.println("Client unregistered");
		}
		
		 
	}
	
	public void update(String follower,String followed) throws RemoteException{
		doCallbacks(follower,followed);
	}
	
	public void update_remove(String user,String unfollower) throws RemoteException{
		doCallbacksRem(user,unfollower);
	}
	
	private synchronized void doCallbacksRem(String user,String unfollower) throws RemoteException{
		if(!clients.containsKey(user))
			return;
		NotifyEventInterface client = (NotifyEventInterface) clients.get(user);
		client.notifyRemoveFollower(unfollower);
		
	}
	
	private synchronized void doCallbacks(String follower,String followed) throws RemoteException{
		if(!clients.containsKey(followed))
			return;
		NotifyEventInterface client = (NotifyEventInterface) clients.get(followed);
		client.notifyNewFollower(follower);
		
	}




}
