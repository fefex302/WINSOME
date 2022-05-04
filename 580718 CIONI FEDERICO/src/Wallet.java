
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Wallet {

	//memorizzate solo 3 cifre decimali
	private double wincoin;
	private ArrayList<String> transactions;
	private String iduser;
	
	public Wallet(String iduser) {
		this.iduser = iduser;
		transactions = new ArrayList<String>();
		wincoin = 0;
	}

	public synchronized void addWincoin(double ammount) {
		wincoin += ammount;
		//wincoin = wincoin * 100;
		//wincoin = Math.round(wincoin);
		//wincoin = wincoin / 100;
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
		LocalDateTime now = LocalDateTime.now();  
		transactions.add(dtf.format(now) + " ricevuti " + ammount + " wincoin");
	}
	
	public ArrayList<String> getTransactions(){
		return transactions;
	}
	
	public double getWincoin() {
		return wincoin;
	}
	
	public String getOwner() {
		return iduser;
	}
}
