

public class Activity {

	//like:0 o commento:1
	public int type;
	public int idpost;
	//reward cicle in cui il post � stato creato
	public int rewardcicle;
	//autore del post con cui si � interagito
	public String author;
	//chi ha fatto l'attivit�
	public String madeby;
	//se il tipo di attivit� � un commento allora mi salvo anche il numero di commenti fatti da questa persona
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
