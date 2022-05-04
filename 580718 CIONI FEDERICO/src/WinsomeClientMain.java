
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.regex.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class WinsomeClientMain {
	public static int rmiport = 9999;
	public static String host = "localhost";
	public static int tcpport = 9888;
	public static int multicastport = 9777;
	public static String multicastgroup = "226.226.226.226";	
	public static int numtags = 5;
	public static int bufsize = 1024 * 1024;
	
	public static String notLoggedMessage = "Non sei loggato";
	
	static SocketAddress address;
	static SocketChannel client;
	static int logged = 0;
	//id col quale sono loggato
	static String loggedas;
	static int connected = 0;
	static Registry registry;
	static NotifyEventServerInterface server;
	static NotifyEventImplementation callback;
	static NotifyEventInterface stub;
	static ArrayList<String> followers = new ArrayList<String>();
	
	public static Thread t_reward;
	public static MulticastSocket mcsocket = null;
	public static DatagramPacket packet = null;
	public static InetAddress group = null;
	
	public static void main(String args[]) {
		loadConfigFile();
		//in questo ciclo infinito accetterò da tastiera i comandi dell'utente e li passerò a uno switch che a seconda
		//del comando utilizzerà una funzione adeguata
		Scanner keyboard = new Scanner(System.in);
		System.out.println("Benvenuto su WINSOME, per registrarsi usare il comando \"register\", per effettuare il login usare il comando \"login\", digitare \"help\" per una lista dei comandi");
		while(true) {
			System.out.println("\nInserire un comando");
			try {
				String command;
				StringTokenizer tokenizer = new StringTokenizer(keyboard.nextLine());
				command = tokenizer.nextToken();

				switch(command){
					//in caso di registrazione leggero i 2 parametri (username e password) e successivamente i tag per un massimo di n
					//se ci sono problemi a livello di input da tastiera (per esempio l'utente ha inserito solo username o nemmeno quello)
					//un'eccezione viene lanciata invitando a riprovare con argomenti validi
				case "help":
					System.out.print("Ecco i comandi che è possibile eseguire, il comando \"post\" necessita delle virgolette intorno al titolo e al contenuto del post:\n"
							+ "register <username> <password> \n"
							+ "login <username> <password>\n"
							+ "logout\n"
							+ "list followers\n"
							+ "list followed\n"
							+ "list users\n"
							+ "follow <idutente>\n"
							+ "unfollow <idutente>\n"
							+ "blog\n"
							+ "show feed"
							+ "post \"titolo\" \"contenuto\"\n"
							+ "show post <IdPost>\n"
							+ "rewin <IdPost>\n"
							+ "rate <IdPost> <Voto> (esprimibile con +1 o -1)\n"
							+ "comment <IdPost> <Comment> (non c'è bisogno delle virgolette)\n"
							+ "wallet\n"
							+ "wallet btc\n"
							+ "followers\n");
						break;
					case "register":
						if(logged == 1) {
							System.out.println("Comando non riconosciuto, utilizzare il comando \"help\" per la lista dei comandi possibili");
							break;
						}
						String username = tokenizer.nextToken();
						String password = tokenizer.nextToken();
						String[] tags= new String[numtags];
						int i = 0;
						//prenderò un massimo di n tags anche se l'utente ne inserisce di più, se non inserisce tag non viene lanciato un errore qua,
						//ma l'operazione fallirà lato server e restituirà errore 
						while(i < numtags && tokenizer.hasMoreTokens()) {
							tags[i] = new String(tokenizer.nextToken()).toLowerCase();
							i++;
						}
						
						register(username,password,tags);
						break;
	
					case "quit":
						if(logged == 1) {
							logout();						
						}
						keyboard.close();
						System.out.println("Arrivederci!");
						System.exit(0);
						break;
						
					case "login":
						if(logged == 1) {
							System.out.println("Sei già loggato");
							break;
						}
						else { 
							String loginusername = tokenizer.nextToken();
							String loginpassword = tokenizer.nextToken();
							login(loginusername,loginpassword);
							break;
						}
					case "logout":
						logout();
						break;
						
					case "list":
						switch (tokenizer.nextToken()) {
							case "users":
								listUsers();
								break;
							case "followers":
								listFollowers();
								break;
							case "following":
								listFollowing();
								break;
						}
						break;
						
					case "follow":
						follow(tokenizer.nextToken());
						break;
						
					case "unfollow":
						unfollow(tokenizer.nextToken());
						break;
						
					case "post":
						String title;
						String content;
						String str = "";
						
						while(tokenizer.hasMoreTokens())
							str += tokenizer.nextToken() + " ";

						List<String> list = new ArrayList<String>();
						Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(str);
						while (m.find())
							list.add(m.group(1).replace("\"", ""));
						
						try{
							title = list.get(0);
							content = list.get(1);
						} catch (Exception e) {
							System.out.println("Errore nella lettura dei parametri, riprovare");
							break;
						}
						
							
						
						createPost(title,content);
						break;
						
					case "blog":
						viewBlog();
						break;
						
					case "show":
						switch(tokenizer.nextToken()) {
							case "feed":
								showFeed();
								break;
							case "post":
								showPost(tokenizer.nextToken());
								break;
						}
						break;
					case "rewin":
						rewinPost(tokenizer.nextToken());
						break;		
						
					case "delete":
						deletePost(tokenizer.nextToken());
						break;
						
					case "rate":
						ratePost(tokenizer.nextToken(),tokenizer.nextToken());
						break;
						
					case "wallet":
						if(tokenizer.hasMoreTokens()) {
							if(tokenizer.nextToken().equals("btc"))
								getWalletInBitcoin();
						}
						else
							getWallet();
						break;
						
					case "comment":
						String idpost = tokenizer.nextToken();
						String comment = "";
						while(tokenizer.hasMoreTokens())
							comment += " " + tokenizer.nextToken();
						addComment(idpost,comment);
						break;
						
					case "followers":
						retrieveFollowers();
						break;
						
					default:
						System.out.println("Comando non riconosciuto, utilizzare il comando \"help\" per la lista dei comandi possibili");
				}
				
			} catch (NoSuchElementException e) {
				System.err.println("Argomenti non validi, riprovare\n");
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("qualcosa è andato storto, riprovare\n");
			}
		}
	}
	
	private static void loadConfigFile() {
		Properties prop = new Properties();
		String filename = "./client.config";
		
		try(FileInputStream fis = new FileInputStream(filename)){
			prop.load(fis);
			rmiport = Integer.parseInt(prop.getProperty("rmiport"));
			host = prop.getProperty("host");
			tcpport = Integer.parseInt(prop.getProperty("tcpport"));
			multicastport = Integer.parseInt(prop.getProperty("multicastport"));
			multicastgroup = prop.getProperty("multicastgroup");
			numtags = Integer.parseInt(prop.getProperty("numtags"));
			bufsize = Integer.parseInt(prop.getProperty("bufsize"));
			
		} catch (IOException e) {
			System.err.println("File di configurazione illeggibile o non trovato");
		} catch (NumberFormatException e1) {
			System.err.println("File di configurazione illeggibile o non trovato");
		}

	}
	
	//------------------------------------------------------------register-------------------------------------------------------------
	
	private static void register(String username, String password, String[] tags) {
		Registry r;
		try {
			r = LocateRegistry.getRegistry(host,rmiport);
			RegistrationInterface registration = (RegistrationInterface) r.lookup("Register");
			System.out.print(registration.register(username, password, tags));
		} catch (RemoteException | NotBoundException e) {

			e.printStackTrace();
		}
		
	}
	
	//------------------------------------------------------------login-------------------------------------------------------------
	
	@SuppressWarnings("deprecation")
	private static void login(String username,String password) {
		try {
			if(logged == 1) {
				System.out.println("Hai già effettuato il login");
				return;
			}
			if(connected == 0) {
				address = new InetSocketAddress(host, tcpport);
				client = SocketChannel.open(address);
				connected = 1;
				
			}
			//ricorda: lato server sarà inutile leggere più byte tanto la password e l'username hanno un tetto massimo di caratteri,
			//se avvenisse una lettura parziale comunque risulterebbe in errore perchè il tetto massimo verrebbe superato.
			ArrayList<String> request = new ArrayList<String>();
			request.add("login");
			request.add(username);
			request.add(password);
			
			//gestisco la richiesta
			Response resp = manageRequest(request);
			
			//elaboro la risposta per capire se il login è andato a buon fine, se si setto la variabile logged a 1 e loggedas = username
			//inoltre mi registro per le callbacks
			if(resp.id == 1) {
				System.out.println(resp.message);
				//setto logged a 1 e loggedas all'username col quale mi sono loggato
				logged = 1;
				loggedas = username;
				
				//mi registro al servizio di notifica followers
				registry = LocateRegistry.getRegistry(host, rmiport);
				server = (NotifyEventServerInterface) registry.lookup("Notifica_followers");
				callback = new NotifyEventImplementation(followers);
				stub = (NotifyEventInterface) UnicastRemoteObject.exportObject(callback, 0);
				server.registerForCallback(stub,loggedas);
				
				//joino il gruppo di multicast
				mcsocket = new MulticastSocket(multicastport);
				group = InetAddress.getByName(multicastgroup);
				mcsocket.joinGroup(group);
				t_reward = new Thread(new Multicast());
				t_reward.start();
			}
			//altrimenti stampo l'errore e non setto niente
			else {
				System.out.println(resp.message);
				connected = 0;
			}
			return;
		} catch (IOException e) {
			System.err.println("Errore di connessione nella fase di login");
			return;
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------logout-------------------------------------------------------------
	
	@SuppressWarnings("deprecation")
	private static void logout() {
		if(logged == 1) {
			try {
				server.unregisterForCallback(loggedas);
			} catch (RemoteException e1) {
				e1.printStackTrace();
			}
			ArrayList<String> request = new ArrayList<String>();
			request.add("logout");
			try {
				manageRequest(request);
			} catch (IOException e) {
				e.printStackTrace();
			}
			logged = 0;
			connected = 0;
			try {
				client.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			try {
				mcsocket.leaveGroup(group);
			} catch (IOException e) {
				e.printStackTrace();
			}
			mcsocket.close();
			
		}
		
		else
			System.out.println(notLoggedMessage);
	}
	
	
	//------------------------------------------------------------listUsers-------------------------------------------------------------
	
	private static void listUsers() {
		if(logged == 0)
			System.out.println(notLoggedMessage);
		else {
			ArrayList<String> request = new ArrayList<String>();
			request.add("list");
			request.add("users");
			try {
				Response response = manageRequest(request);
				System.out.println("-----------------------------LISTA USER PARZIALE-----------------------------\n");
				System.out.println(response.message);
				System.out.println("-----------------------------------------------------------------------------\n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	//------------------------------------------------------------listFollowers-------------------------------------------------------------
	private static void listFollowers() {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return ;
		}
		
		if(followers.size() == 0)
			System.out.println("Non hai ancora nessun follower :(");
		else {
			System.out.println("Ecco i tuoi followers:");
			for(String string : followers) {
				System.out.println(string);
			
			}
		}
	}
	//------------------------------------------------------------listFollowing-------------------------------------------------------------
	
	private static void listFollowing() {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return ;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("list");
		request.add("following");
		try {
			Response response = manageRequest(request);
			System.out.println("-----------------------------LISTA DEI PROPRI SEGUITI-----------------------------\n");
			System.out.println(response.message);
			System.out.println("----------------------------------------------------------------------------------\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------follow-------------------------------------------------------------
	
	private static void follow(String tofollow) {
		if(logged == 0)
			System.out.println(notLoggedMessage);
		else {
			ArrayList<String> request = new ArrayList<String>();
			request.add("follow");
			request.add(tofollow);
			try {

				Response resp = manageRequest(request);
				System.out.println(resp.message);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	//------------------------------------------------------------unfollow-------------------------------------------------------------
	
	private static void unfollow(String tounfollow) {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("unfollow");
		request.add(tounfollow);
		try {

			Response resp = manageRequest(request);
			System.out.println(resp.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------createPost-------------------------------------------------------------
	
	private static void createPost(String titolo,String contenuto) {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		if(titolo.length() > 20) {
			System.out.println("Titolo troppo lungo, il massimo è 20 caratteri");
			return;
		}
		if(contenuto.length() > 500) {
			System.out.println("Post troppo lungo, il massimo è 500 caratteri");
			return;
		}
		
		ArrayList<String> request = new ArrayList<String>();
		request.add("post");
		request.add(titolo);
		request.add(contenuto);
		try {
			Response response = manageRequest(request);
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	
	//------------------------------------------------------------viewBlog-------------------------------------------------------------
	private static void viewBlog() {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		
		ArrayList<String> request = new ArrayList<String>();
		request.add("blog");
		
		try {
			Response response = manageRequest(request);                   
			System.out.println("--------------------BLOG--------------------");
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	//------------------------------------------------------------showFeed-------------------------------------------------------------

	private static void showFeed() {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("show");
		request.add("feed");
		
		try {
			Response response = manageRequest(request);                   
			System.out.println("--------------------FEED--------------------");
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	//------------------------------------------------------------showPost-------------------------------------------------------------
	private static void showPost(String idpost) {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("show");
		request.add("post");
		request.add(idpost);
		try {
			Response response = manageRequest(request);                   
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	//------------------------------------------------------------rewinPost-------------------------------------------------------------
	private static void rewinPost(String idpost) {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		
		ArrayList<String> request = new ArrayList<String>();
		request.add("rewin");
		request.add(idpost);
		try {
			Response response = manageRequest(request);                   
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	//------------------------------------------------------------deletePost-------------------------------------------------------------
	
	private static void deletePost(String idpost) {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		
		ArrayList<String> request = new ArrayList<String>();
		request.add("delete");
		request.add(idpost);
		try {
			Response response = manageRequest(request);                   
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	//------------------------------------------------------------ratePost-------------------------------------------------------------
	
	private static void ratePost(String idpost,String vote) {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("rate");
		request.add(idpost);
		request.add(vote);
		
		try {
			Response response = manageRequest(request);                   
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------getWallet-------------------------------------------------------------
	
	private static void getWallet() {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("wallet");

		try {
			Response response = manageRequest(request);                   
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------addComment-------------------------------------------------------------
	
	private static void addComment(String idpost,String comment) {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("comment");
		request.add(idpost);
		request.add(comment);
		
		try {
			Response response = manageRequest(request);                   
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------getWalletInBitcoin-------------------------------------------------------------
	
	private static void getWalletInBitcoin() {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("wallet");
		request.add("btc");

		try {
			Response response = manageRequest(request);                   
			System.out.println(response.message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//------------------------------------------------------------retrieveFollowers-------------------------------------------------------------
	
	private static void retrieveFollowers() {
		if(logged == 0) {
			System.out.println(notLoggedMessage);
			return;
		}
		ArrayList<String> request = new ArrayList<String>();
		request.add("followers");

		try {
			Response response = manageRequest(request);    
			StringTokenizer tkn = new StringTokenizer(response.message);
			while(tkn.hasMoreTokens()) {
				String token = tkn.nextToken();
				if(!followers.contains(token))
					followers.add(token);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static Response manageRequest(ArrayList<String> request) throws IOException {
		//creo il buffer per inviare la richiesta
		ByteBuffer bb = ByteBuffer.allocate(bufsize);
		Gson gson = new GsonBuilder().create();
		//metto la richiesta sottoforma di json nel buffer
		bb.put(gson.toJson(request).getBytes());
		bb.flip();
		
		//invio la richiesta
		while(bb.hasRemaining())
			client.write(bb);
		
		//creo un buffer per la lettura della risposta
		ByteBuffer bb2 = ByteBuffer.allocate(bufsize);
		ByteBuffer bb3 = ByteBuffer.allocate(4);
		String prova = "";
		//leggo la risposta, se il buffer è troppo piccolo per scrivere tutti i dati allora più cicli di lettura verranno fatti
		//fino a che la lettura non produrrà 0 byte letti
		client.read(bb3);
		bb3.flip();
		int size = bb3.getInt();
		int sizeRead = 0;
		while(size > sizeRead) {
			sizeRead += client.read(bb2);
			bb2.flip();
			prova += new String(bb2.array());
			bb2.clear();
		}
		
		
		//elaboro la risposta e la ritorno come risultato per notificare il successo o il fallimento
		//Response response =  gson.fromJson(new String(bb2.array()).trim(), Response.class);
		Response response =  gson.fromJson(prova.trim(), Response.class);
		
		return response;
	}
	
	private static class Multicast implements Runnable{

		@Override
		public void run() {
			byte[] buffer = new byte[1024];
			try {
				while(true) {
					packet = new DatagramPacket(buffer,1024);
					mcsocket.receive(packet);
					String received = new String(packet.getData());
					System.out.println(received.trim());
				}
			} catch (IOException e) {
				return;
			}
			
		}
		
	}
}
