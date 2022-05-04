
import java.util.ArrayList;
import java.util.List;

public class User {
	//ID univoco dell'utente
	private final String ID;
	//Password dell'utente, utilizzata per accedere all'account
	private String password;
	//tag (massimo 5)
	private String[] tags;
	//seguaci
	private ArrayList<String> followers;
	//seguiti
	private ArrayList<String> following;
	
	//metodo di creazione di un user, usato alla registrazione
	public User(String iduser, String password, String[] tags) {
		ID = iduser;
		this.password = password;
		this.tags = tags;
		followers = new ArrayList<String>();
		following = new ArrayList<String>();
	}

	public String getHashedPassword() {
		return password;
	}
	
	public String getUserid() {
		return ID;
	}
	
	public String[] getTags() {
		return this.tags;
	}
	
	public synchronized int addFollower(String follower) {
		if(followers.contains(follower))
			return 0;
		else
			followers.add(follower);
		return 1;
	}
	
	public synchronized int removeFollower(String follower) {
		if(!followers.contains(follower))
			return 0;
		else
			followers.remove(follower);
		return 1;
	}
	
	public synchronized int addFollowed(String followed) {
		if(this.following.contains(followed))
			return 0;
		else
			this.following.add(followed);
		return 1;
	}
	
	public synchronized int removeFollowed(String followed) {
		if(!this.following.contains(followed))
			return 0;
		else
			this.following.remove(followed);
		return 1;
	}
	
	//ritorno una copia dell'array cosi non ho problemi ad aggiornare l'originale
	public synchronized ArrayList<String> getFollowing(){
		return new ArrayList<>(following);
	}
	//ritorno una copia dell'array cosi non ho problemi ad aggiornare l'originale
	public synchronized ArrayList<String> getFollowers(){
		return new ArrayList<>(followers);
	}
}
