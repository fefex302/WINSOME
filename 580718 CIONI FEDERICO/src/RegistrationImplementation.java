
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

public class RegistrationImplementation implements RegistrationInterface {

	HashMap<String,User> Utenti;
	HashMap<String,Wallet> Wallets;
	WriteLock wlockU;
	WriteLock wlockW;
	
	public RegistrationImplementation(HashMap<String,User> Utenti,WriteLock wlock,HashMap<String,Wallet> Wallets,WriteLock wlockw) {
		this.Utenti = Utenti;
		this.wlockU = wlock;
		this.Wallets = Wallets;
		this.wlockW = wlockw;
	}
	
	public void RetrieveUsers(String path) {
		return;
	}
	
	@Override
	//ho messo unicamente questo metodo synchronized, in maniera tale da togliere ogni eventuale race condition dalla
	//registrazione, la lettura della struttura contentente gli utenti invece può essere effettuata da più thread contemporaneamente
	public String register(String username, String password, String[] tags) throws RemoteException {
		
		if(Utenti.containsKey(username))
			return "Nome utente già in uso\n";
		
		if(username.isBlank() || username.isEmpty())
			return "Password non valida\n";
		
		if(username.length() > 30)
			return "Nome utente troppo lungo\n";
		
		if(username.length() < 2)
			return "Nome utente troppo corto\n";
		
		if(password.length() <4)
			return "Password troppo corta\n";
		
		if(password.length() > 50)
			return "Password troppo lunga\n";
		
		if(tags[0] == null)
			return "Inserire almeno un tag\n";
		
		wlockU.lock();
		try {
			User newuser = new User(username,Hash.bytesToHex(Hash.sha256(password)),tags);
			Utenti.put(username, newuser);

		} catch (NoSuchAlgorithmException e) {
			
			e.printStackTrace();
			wlockU.unlock();
			return "Qualcosa è andato storto, riprovare\n";
		}
		wlockU.unlock();
		wlockW.lock();
		Wallets.put(username, new Wallet(username));
		wlockW.unlock();
		return "Registrazione avvenuta, benvenuto su WINSOME!\n";
	}


	
}
