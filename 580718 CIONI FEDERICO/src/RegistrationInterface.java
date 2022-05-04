
import java.rmi.*;

public interface RegistrationInterface extends Remote{
	
	public String register(String username, String password, String[] tags) throws RemoteException;
	
}
