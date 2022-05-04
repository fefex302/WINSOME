

import java.util.ArrayList;
import java.util.concurrent.locks.*;

public class Post {
	private final int idpost;
	private String title;
	private String content;
	private String author;

	//questa variabile mi serve per registrare il ciclo di reward in cui sono, per determinare e attuare il concetto di
	//decadenza, la decadenza verrà calcolata in base al ciclo attuale di reward meno il ciclo in cui il post è stato creato
	private int reward_cicle;
	ArrayList<String> likes = new ArrayList<String>();
	
	private int n_likes;
	private int n_dislikes;
	ArrayList<Comment> comments = new ArrayList<Comment>();
	
	public Post(String titolo, String contenuto, String autore, int idpost, int reward_cicle) {
		this.idpost = idpost;
		this.title = titolo;
		this.content = contenuto;
		this.author = autore;
		this.reward_cicle = reward_cicle;
		this.n_dislikes = 0;
		this.n_likes = 0;
	}

	public synchronized int addLike(String likedBy) {
		if(likes.contains(likedBy))
			return 0;
		else {
			likes.add(likedBy);
			n_likes ++;
		}
		return 1;
	}
	
	public synchronized int addDislike(String dislikedBy) {
		if(likes.contains(dislikedBy))
			return 0;
		else {
			likes.add(dislikedBy);
			n_dislikes ++;
		}
		return 1;
	}
	
	public synchronized void addComment(Comment comment) {
		comments.add(comment);
	}
	
	public String getTitle() {
		return this.title;
	}
	
	public String getContent() {
		return this.content;
	}
	
	public int getId() {
		return this.idpost;
	}
	
	public String getAuthor() {
		return this.author;
	}
	
	public int getRewardCicle() {
		return this.reward_cicle;
	}
	
	public synchronized ArrayList<String> getLikes(){
		return new ArrayList<>(likes);
	}
	
	public synchronized int getLikesNumber() {
		return this.n_likes;
	}
	
	public synchronized int getDislikesNumber() {
		return this.n_dislikes;
	}
	
	
	public synchronized ArrayList<Comment> getComments(){
		return new ArrayList<>(comments);
	}
	
	public synchronized int getCommentsAmmount(String id) {
		if(comments.isEmpty())
			return 0;
		int ret = 0;
		for(Comment comment : comments) {
			if(comment.getAuthor().equals(id))
				ret++;
		}
		return ret;
	}
}
