

public class Activity {

	//like:0 o commento:1
	public int type;
	public int idpost;
	//reward cicle in cui il post è stato creato
	public int rewardcicle;
	//autore del post con cui si è interagito
	public String author;
	//chi ha fatto l'attività
	public String madeby;
	//se il tipo di attività è un commento allora mi salvo anche il numero di commenti fatti da questa persona
	//in precedenza + 1
	public int numcomment;
	
	public Activity(int type,int idpost,String madeby,String author,int rewardcicle) {
		this.author = author;
		this.type = type;
		this.idpost = idpost;
		this.madeby = madeby;
		this.rewardcicle = rewardcicle;
	}

}
