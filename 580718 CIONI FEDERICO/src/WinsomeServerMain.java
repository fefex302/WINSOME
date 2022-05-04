

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class WinsomeServerMain {
	
	//parametri di configurazione delle porte, percorsi file, ecc.
	public static int rmiport = 9999;
	public static String host = "localhost";
	public static String path_utenti = "./utenti.txt";
	public static String path_posts = "./post.txt";
	public static String path_idposts = "./idposts.txt";
	public static String path_rewardcicle = "./rewardcicle.txt";
	public static String path_allposts = "./allposts.txt";
	public static String path_wallets = "./wallets.txt";
	public static String path_activities = "./activities.txt";
	public static int serverport = 9888;
	public static int multicastport = 9777;
	public static String multicastgroup = "226.226.226.226";
	public static int reg_port = 39000;
	public static int notify_port = 39010;
	public static int idpostcreator = 0;
	public static int curatorReward = 30;
	public static int authorReward = 70;
	
	public static DatagramSocket datagramsocket;
	public static InetAddress group;
	//parametri dei vari buffer, array, ecc.
	public static int bufsize = 1024*64;
	public static int numthreads = 7;
	public static int terminationDelay = 60000;
	public static int dimCoda = 100;
	public static int sleep_time = 10000;
	public static int checkpoint_time = 15000;
	public static int reward_sleep_time = 60000;
	
	
	//valore per la chiusura del server, if(end==true) il server è in fase di chiusura
	public static boolean end = false;
	//idposts serve per la creazione di id univoci
	public static int idposts;
	//serve per sapere a che ciclo di reward siamo
	public static int reward_cicle;
	
	//stringhe per messaggi di errore
	public static final String failed = "Richiesta fallita";
	public static final String bad_arguments = "Richiesta fallita, argomenti errati";
	public static final String not_logged = "Richiesta fallita, non sei loggato";
	
	//map contenente tutti gli utenti registrati
	static HashMap<String,User> Utenti = new HashMap<String,User>();
	//map contenente la lista dei post collegata all'id dell'utente che li ha scritti (so che l'id è unico quindi la chiave è unica)
	static HashMap<String,ArrayList<Post>> Posts = new HashMap<String,ArrayList<Post>>();
	//lista di tutti i post in ordine di id contenuti nel server
	static ArrayList<Post> allposts = new ArrayList<Post>();
	//lista delle attività del server
	static ArrayList<Activity> activities = new ArrayList<Activity>();
	//map di tutti i portafogli associati ai rispettivi owners
	static HashMap<String,Wallet> Wallets = new HashMap<String,Wallet>();
	
	//readWritelock per la struttura utenti
	public static ReentrantReadWriteLock rwlockUtenti = new ReentrantReadWriteLock();
	public static ReadLock readlockUtenti = rwlockUtenti.readLock();
	public static WriteLock writelockUtenti = rwlockUtenti.writeLock();
	//readWritelock per la struttura Posts
	public static ReentrantReadWriteLock rwlockPosts = new ReentrantReadWriteLock();
	public static ReadLock readlockPosts = rwlockPosts.readLock();
	public static WriteLock writelockPosts = rwlockPosts.writeLock();
	//readWritelock per la struttura allposts
	public static ReentrantReadWriteLock rwlockAllposts = new ReentrantReadWriteLock();
	public static ReadLock readlockAllposts = rwlockAllposts.readLock();
	public static WriteLock writelockAllposts = rwlockAllposts.writeLock();
	
	//lock per la variabile idposts
	public static ReentrantLock idpostslock = new ReentrantLock();
	//lock per la variabile reward_cicle
	public static ReentrantLock rewardciclelock = new ReentrantLock();
	//lock per le attività
	public static ReentrantLock activitieslock = new ReentrantLock();
	//lock per i wallet
	public static ReentrantReadWriteLock rwlockWallet = new ReentrantReadWriteLock();
	public static ReadLock readlockWallet = rwlockWallet.readLock();
	public static WriteLock writelockWallet = rwlockWallet.writeLock();
	
	//tutti i thread ausiliari lanciati dal programma
	static Thread t_save;
	static Thread t_reward;
	static Thread t_end;
	
	//la coda e il pool di thread serviranno per gestire in maniera simultanea più richieste. La ricezione delle richieste avviene tramite NIO, quindi
	//in maniera non bloccante, però il thread che le gestisce è solo uno, tramite il multithreading ho voluto creare una situazione dove la ricezione delle richieste
	//è delegata alle NIO, mentre le operazioni da eseguire a seguito dell'interpretazione delle richieste è delegata a più thread che una volta conclusa l'operazione
	//invieranno una notifica sul canale non bloccante con la risposta da inviare al client (OP_WRITE).

	static BlockingQueue<Runnable> queue = new ArrayBlockingQueue<Runnable>(dimCoda);
	static ExecutorService pool = new ThreadPoolExecutor(numthreads, numthreads, terminationDelay, TimeUnit.MILLISECONDS, queue,
			new ThreadPoolExecutor.AbortPolicy());
	
	static NotifyEventServerImplementation server;
	
	public static void main(String args[]) {
		loadConfigFile();
		//funzione per recuperare gli utenti registrati, se è la prima volta che il server si attiva non succede niente
		retrieveServerStatus();
		try {
			RegistrationImplementation registerService = new RegistrationImplementation(Utenti,writelockUtenti,Wallets,writelockWallet);
			RegistrationInterface stub = (RegistrationInterface) UnicastRemoteObject.exportObject(registerService, reg_port);
			LocateRegistry.createRegistry(rmiport);
			Registry reg = LocateRegistry.getRegistry(rmiport);
			reg.rebind("Register", stub);
			server = new NotifyEventServerImplementation();
			
			NotifyEventServerInterface stub2 = (NotifyEventServerInterface) UnicastRemoteObject.exportObject(server, notify_port);
			reg.rebind("Notifica_followers", stub2);
			
		} catch (RemoteException e) {
			//in caso di errore devo far terminare il server
			e.printStackTrace();
			System.exit(1);
		}
		
		//thread che salva su disco i dati
		t_save = new Thread(new SaveServer());
		t_save.start();
		//thread che attende la digitazione di "end" per terminare
		t_end = new Thread(new EndServer());
		t_end.start();
		//thread che calcola le rewards
		t_reward = new Thread(new RewardCalculator());
		t_reward.start();
		
		try {
			datagramsocket = new DatagramSocket();
			 group = InetAddress.getByName(multicastgroup);
		} catch (SocketException e2) {
			System.err.println("Errore nell'apertura del DatagramSocket");
			System.exit(1);
		} catch (UnknownHostException e) {
			System.err.println("Errore nell'apertura del gruppo di multicast");
			System.exit(1);
		}
		
		//Da questo punto in poi inizierò ad accettare le vere e proprie richieste, ad iniziare da quella di login
		//utilizzo un metodo non bloccante in maniera tale da avere un utilizzo ottimale delle risorse
		ServerSocketChannel serverchannel;
		Selector selector;
		try {
			//apro il canale su cui creerò il socket sul quale ricevere le richieste dei vari client
			serverchannel = ServerSocketChannel.open();
			ServerSocket ss = serverchannel.socket();
			InetSocketAddress address = new InetSocketAddress(serverport);
			ss.bind(address);
			serverchannel.configureBlocking(false);
			selector = Selector.open();
			serverchannel.register(selector, SelectionKey.OP_ACCEPT);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		while(true) {
			if(end)
				break;
			try {
				selector.select();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			Set <SelectionKey> readyKeys = selector.selectedKeys();
			Iterator <SelectionKey> iterator = readyKeys.iterator();
			
			while(iterator.hasNext()) {
				SelectionKey key = iterator.next();
				iterator.remove();
				try {
				if(key.isAcceptable()) {
					 ServerSocketChannel server = (ServerSocketChannel) key.channel();
					 SocketChannel client;
					
						
					//tramite questo procedimento accetto la connessione del client e mi preparo a ricevere
					//richieste
					client = server.accept();
					client.configureBlocking(false);
					System.out.println("Connessione accettata");
					SelectionKey key2 = client.register(selector, SelectionKey.OP_READ);
					System.out.println("Client connesso");
					
					//questa classe viene usata per verificare che il client abbia effettuato il login, 
					//senza login nessuna operazione è consentita, tranne ovviamente quella per il login
					//inoltre contiene un buffer che verrà utilizzato per ricevere richieste e inviare risposte
					key2.attach(new Attachment());
				
				}
				
				else if(key.isReadable()) {
					

					SocketChannel client = (SocketChannel) key.channel();
					client.configureBlocking(false);
					Attachment att = (Attachment) key.attachment();
					att.bb = ByteBuffer.allocate(bufsize);
					client.read(att.bb);
					pool.execute(new RequestHandler(key,client));
					
				}
				
				} catch (IOException e) {
					//nel caso in cui il cient termini inaspettatamente cancellerò la key e chiuderò il canale associato
					System.err.println("Errore nella comunicazione col client, chiusura canale di comunicazione in corso");
					key.cancel();
					try {
						key.channel().close();
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		}
		
		//System.out.println("Sono qua\n");
		
	}
	
	//RICORDA CHE SE QUESTO METODO NON FUNZIONA NON BISOGNA ESEGUIRE IL BACKUP DEGLI ALTRI DATI (POST,COMMENTI ECC.)
	public static void retrieveServerStatus() {
		try {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			try{
				idposts = gson.fromJson(Utils.readFile(path_idposts), int.class);
			} catch (FileNotFoundException e) {
				idposts = 0;
			}
			try{
				reward_cicle = gson.fromJson(Utils.readFile(path_rewardcicle), int.class);
			} catch (FileNotFoundException e) {
				reward_cicle = 0;
			}
			
			try {
				Type type = new TypeToken<HashMap<String, ArrayList<Post>>>(){}.getType();
				Posts = gson.fromJson(Utils.readFile(path_posts),type);
			} catch(Exception e) {
				
			}
		
			try {
				Type type = new TypeToken<ArrayList<Post>>(){}.getType();
				allposts = gson.fromJson(Utils.readFile(path_allposts),type);
			} catch(Exception e) {
				
			}
			
			try {
				Type type = new TypeToken<HashMap<String,Wallet>>(){}.getType();
				Wallets = gson.fromJson(Utils.readFile(path_wallets),type);
			} catch(Exception e) {}
			
			try {
				Type type = new TypeToken<ArrayList<Activity>>(){}.getType();
				activities = gson.fromJson(Utils.readFile(path_activities),type);
			} catch(Exception e) {}
			
			
			//creo una classe gson TypeToken passandogli il tipo della classe Hashmap per far sapere a Gson che classe gli
			//sto passando tra quelle generiche
			Type type = new TypeToken<HashMap<String, User>>(){}.getType();
			Utenti = gson.fromJson(Utils.readFile(path_utenti), type);
		} catch (JsonSyntaxException  e) {

			e.printStackTrace();
			System.err.println("Json file illeggibile");
		} catch (FileNotFoundException e) {
			//se il file non eiste non faccio niente, la map sarà inizializzata e sarà vuota
		} catch (IOException e) {
			e.printStackTrace();
			System.err.print("Errore nella lettura dei file, chiusura server");
			System.exit(1);
		}
		
		
		
		//System.out.print("Ok\n");
	}
	
	//funzione per leggere il file di configurazione
	public static void loadConfigFile() {
		Properties prop = new Properties();
		String filename = "./server.config";
		
		try(FileInputStream fis = new FileInputStream(filename)){
			prop.load(fis);
			rmiport = Integer.parseInt(prop.getProperty("rmiport"));
			host = prop.getProperty("host");
			path_utenti = prop.getProperty("path_utenti");
			path_posts = prop.getProperty("path_posts");
			path_idposts = prop.getProperty("path_idposts");
			path_rewardcicle = prop.getProperty("path_rewardcicle");
			path_allposts = prop.getProperty("path_allposts");
			path_wallets = prop.getProperty("path_wallets");
			path_activities = prop.getProperty("path_activities");
			serverport = Integer.parseInt(prop.getProperty("serverport"));
			multicastport =  Integer.parseInt(prop.getProperty("multicastport"));
			multicastgroup = prop.getProperty("multicastgroup");
			reg_port = Integer.parseInt(prop.getProperty("reg_port"));
			notify_port = Integer.parseInt(prop.getProperty("notify_port"));
			curatorReward = Integer.parseInt(prop.getProperty("curatorReward"));
			authorReward = Integer.parseInt(prop.getProperty("authorReward"));
			bufsize = Integer.parseInt(prop.getProperty("bufsize"));
			numthreads = Integer.parseInt(prop.getProperty("numthreads"));
			terminationDelay = Integer.parseInt(prop.getProperty("terminationDelay"));
			dimCoda = Integer.parseInt(prop.getProperty("dimCoda"));
			checkpoint_time = Integer.parseInt(prop.getProperty("checkpoint_time"));
			reward_sleep_time = Integer.parseInt(prop.getProperty("reward_sleep_time"));
		} catch (IOException e) {
			System.err.println("File di configurazione illeggibile o non trovato");
		} catch (NumberFormatException e1) {
			System.err.println("File di configurazione illeggibile o non trovato");
		}

	}
	
	private static class RequestHandler implements Runnable {

		SelectionKey key;
		SocketChannel channel;
		
		//alla creazione, mi memorizzo il canale e la chiave, così da interagire direttamente col client
		public RequestHandler(SelectionKey key, SocketChannel channel) {
			//this.request = request;
			this.channel = channel;
			this.key = key;
		}
		
		@Override
		public void run() {
			try {
				Attachment att = (Attachment)key.attachment();
				Gson gson = new GsonBuilder().setPrettyPrinting().create();
				Type type = new TypeToken<ArrayList<String>>(){}.getType();
				String json = new String(att.bb.array()).trim();
				ArrayList<String> request = gson.fromJson(json, type);
				
				switch (request.remove(0)) {
				
				//----------------------login----------------------------
				case "login":
					login(request.remove(0),request.remove(0));
					break;
					
				//----------------------logout----------------------------
				case "logout":
					if(att.loginStatus)
						logout();
					else
						sendResponse(not_logged,0);
					break;
				//----------------------list----------------------------
				case "list":
					
					switch (request.remove(0)) {
					//-------------------------------listUsers----------------------
					case "users":
						if(att.loginStatus)
							listUsers(att.loggedAs);
						else
							sendResponse(not_logged,0);
						break;
					case "following":
						if(att.loginStatus)
							listFollowing(att.loggedAs);
						else
							sendResponse(not_logged,0);
					}
					break;
				
				//---------------------------follow---------------------------------
				case "follow":
					if(att.loginStatus)
						follow(request.remove(0));
					else
						sendResponse(not_logged,0);
					break;
					
				//---------------------------unfollow--------------------------------
				case "unfollow":
					if(att.loginStatus)
						unfollow(request.remove(0));
					else
						sendResponse(not_logged,0);
					break;
					
				//---------------------------createPost-------------------------------
				case "post":
					if(att.loginStatus)
						createPost(request.remove(0),request.remove(0));
					else
						sendResponse(not_logged,0);
					break;
					
				case "blog":
					if(att.loginStatus)
						viewBlog(att.loggedAs);
					else
						sendResponse(not_logged,0);
					break;
					
				case "show":
					if(att.loginStatus) {
						switch(request.remove(0)) {
							case "feed":
								showFeed(att.loggedAs);	
								break;
							case "post":
								showPost(request.remove(0));
								break;
						}
					}
					else
						sendResponse(not_logged,0);
					break;
					
				case "rewin":
					if(att.loginStatus) {
						rewinPost(request.remove(0));
						break;
					}
					else
						sendResponse(not_logged,0);
					break;
				case "delete":
					if(att.loginStatus) {
						deletePost(request.remove(0));
						break;
					}
					else
						sendResponse(not_logged,0);
					break;
					
				case "rate":
					if(att.loginStatus) {
						ratePost(request.remove(0),request.remove(0));
						break;
					}
					else
						sendResponse(not_logged,0);
					break;
					
				case "wallet":
					if(att.loginStatus) {
						if(request.size() == 0)
							getWallet();
						else if(request.remove(0).equals("btc"))
								getWalletInBitcoin();
						else
							sendResponse("Comando non riconosciuto",0);
						break;
					}
					else
						sendResponse(not_logged,0);
					break;
					
				case "comment":
					if(att.loginStatus) {
						addComment(request.remove(0),request.remove(0));
						break;
					}
					else
						sendResponse(not_logged,0);
					break;
				case "followers":
					if(att.loginStatus) {
						getFollowers();
						break;
					}
					else
						sendResponse(not_logged,0);
					break;
				default:
						sendResponse("Richiesta non riconosciuta",0);
						break;
				}
					
				
				//SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
				//Attachment att = new Attachment();
				//key.attach(att.bb.put("richiesta eseguita".getBytes()));
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// TODO Auto-generated method stub
			return;
		}
		
		//-------------------------login--------------------------------
		private int login(String userid,String password) {
			
			//se l'username non esiste ritorno subito un errore
			if(!Utenti.containsKey(userid) ) {
				sendResponse("utente inesistente",0);
				return 0;
			}
			
			//se l'username esiste procedo alla verifica della validità della password
			try {
				
				//mi creo l'hash della password ricevuta
				String hashedpassword = Hash.bytesToHex(Hash.sha256(password));
				
				//confronto l'hash con quello del server
				readlockUtenti.lock();
				if(hashedpassword.equals(Utenti.get(userid).getHashedPassword())) {
					Attachment att = (Attachment) key.attachment();
					if(att.loginStatus) {
						readlockUtenti.unlock();
						sendResponse("Hai già effettuato il login",0);
						return 0;
					}
					readlockUtenti.unlock();
					att.loginStatus = true;
					att.loggedAs = userid;
					sendResponse("login effettuato, benvenuto\\a " + userid,1);
				}
				else {
					sendResponse("password sbagliata, riprovare",0);
					return 0;
				}
			} catch (NoSuchAlgorithmException e) {
				sendResponse(failed,0);
				e.printStackTrace();
				return 0;
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e1) {
		
				e1.printStackTrace();
			}

			return 1;
			
		}
		
		//----------------------logout----------------------------
		private int logout() {
			Attachment att = (Attachment) key.attachment();
			att.loginStatus = false;
			sendResponse("logout effettuato",1);
			
			
			try {
				key.channel().close();
			} catch (IOException e1) {
				System.err.println("Errore nella chiusura del canale");
				return 0;
			}
			return 1;
		}
		
		//----------------------listUsers----------------------------
		private int listUsers(String tmpuser) {
			String response = "";
			
			//acquisisco la readlock per prendere l'utente e il keySet della mappa degli utenti in maniera safe 
			readlockUtenti.lock();
	
			User user = Utenti.get(tmpuser);
			Set<String> kset = Utenti.keySet();
			
			readlockUtenti.unlock();
			
			
			//questo algoritmo serve per scrivere gli utenti che hanno tags in comune ed evitare di riscriverli
			//nel caso si abbiano più tags in comune, per semplicità ho spedito al client tutti i tag dell'utente e non solo
			//quelli in comune
			for(String key : kset) {
				
				if(key.equals(tmpuser))
					continue;
				
				String tags[] = Utenti.get(key).getTags();
				int skip = 0;
				for(String i : tags) {
					if(skip == 1)
						break;
					if(i == null)
						break;
					for(String k : user.getTags()) {
						if(k == null)
							break;
						if(i.equals(k)) {
							response += "userid: " + key + "\n" + "tags di " + key + ":";
							for(int j = 0; j < tags.length ; j ++) {
								if(tags[j] == null)
									break;
								response += " " + tags[j] ;
									
							}
							response += "\n\n";
							skip = 1;
							break;
						}
						
					}
				
				}
			}
			sendResponse(response,1);
			return 1;
		}
		
		
		
		//--------------------------------------follow--------------------------------------------
		
		//funzione per seguire un utente, viene prima verificato che l'utente da seguire esista, dopodichè 
		//si verifica che non si segua già e infine viene aggiunto nella lista dei seguiti dall'utente e dei followers
		//dell'utente seguito
		private int follow(String tofollow) {
			Attachment att = (Attachment) key.attachment();
			if(tofollow.equals(att.loggedAs)) {
				sendResponse("Non puoi seguire te stesso",0);
				return 0;
			}
			
			//acquisisco la readlock degli utenti
			readlockUtenti.lock();
			if(!Utenti.containsKey(tofollow)) {
				sendResponse("Utente inesistente",0);
				readlockUtenti.unlock();
				return 0;
			}
			else {
				if(Utenti.get(tofollow).addFollower(att.loggedAs) == 0) {
					
					readlockUtenti.unlock();
					sendResponse("Segui già " + tofollow,0);
					return 0;
				}
				if(Utenti.get(att.loggedAs).addFollowed(tofollow) == 0) {
					
					readlockUtenti.unlock();
					sendResponse("Segui già " + tofollow,0);
					return 0;
				}
				readlockUtenti.unlock();
				try {
					server.update(att.loggedAs,tofollow);
				} catch (RemoteException e) {
		
					try {
						server.unregisterForCallback(att.loggedAs);
					} catch (RemoteException e1) {
						
					}
				}
				sendResponse("Hai iniziato a seguire " + tofollow,1);
				return 1;
			}
		}
		
		//--------------------------------------unfollow--------------------------------------------

		private int unfollow(String tounfollow) {
			Attachment att = (Attachment) key.attachment();
			if(tounfollow.equals(att.loggedAs)) {
				sendResponse("Non puoi smettere di seguire te stesso",0);
				return 0;
			}
			readlockUtenti.lock();
			if(!Utenti.containsKey(tounfollow)) {
				sendResponse("Utente inesistente",0);
				readlockUtenti.unlock();
				return 0;
			}
			User user = Utenti.get(att.loggedAs);
			if(!user.getFollowing().contains(tounfollow)) {
				readlockUtenti.unlock();
				sendResponse("Non segui " + tounfollow,0);
				return 0;
			}
			else {
				user.removeFollowed(tounfollow);

			}
			User user2 = Utenti.get(tounfollow);
			if(user2.getFollowers().contains(att.loggedAs)) {
				user2.removeFollower(att.loggedAs);
				readlockUtenti.unlock();
				sendResponse("Hai smesso di seguire "+ tounfollow,1);
				try {
					server.update_remove(tounfollow,att.loggedAs);
				} catch (RemoteException e) {
					try {
						server.unregisterForCallback(att.loggedAs);
					} catch (RemoteException e1) {
						
					}
				}

			}
			else {
				readlockUtenti.unlock();
				sendResponse("Non segui " + tounfollow,0);
				return 0;
			}
			
			return 1;
		}
		
		
		//--------------------------------------listFollowing--------------------------------------------
		
		private int listFollowing(String tmpuser) {
			readlockUtenti.lock();
			User user = Utenti.get(tmpuser);
			readlockUtenti.unlock();
			String response = "";
			
			ArrayList<String> following = user.getFollowing();
			if(following.isEmpty()) {
				sendResponse("Non segui ancora nessuno",1);
				return 1;
			}
			for(String string : following)
				response += string + "\n";
			sendResponse(response,1);
			return 1;
		}
		
		//--------------------------------------createPost--------------------------------------------
		
		private int createPost(String title, String content) {
			
			if(title.length() > 20) {
				sendResponse("Titolo troppo lungo, massimo 20 caratteri",0);
				return 0;
			}
			if(content.length() > 500) {
				sendResponse("Contenuto troppo lungo, massimo 500 caratteri",0);
				return 0;
			}
			Attachment att = (Attachment) key.attachment();
			
			//la creazione di un post richiede un accesso sincronizzato non solo alla struttura dati dei post, ma
			//anche alla variabile idposts, questo pezzo di codice è l'unico modo per modificare la struttura dati, quindi 
			//basterà sincronizzare questa parte
			Post post;
			
			post = new Post(title,content,att.loggedAs,idposts,reward_cicle);
			
			//locko l'idpost che mi genera gli id per non avere duplicati
			idpostslock.lock();
			idposts++;
			idpostslock.unlock();
			
			writelockPosts.lock();
			writelockAllposts.lock();
			allposts.add(post);
			writelockAllposts.unlock();
			if(Posts.containsKey(att.loggedAs)) {
				Posts.get(att.loggedAs).add(post);
			}
			else {
				ArrayList<Post> array = new ArrayList<Post>();
				array.add(post);
				Posts.put(att.loggedAs, array);
			}
			writelockPosts.unlock();
				
			
			
			sendResponse("Post pubblicato! id: " + post.getId(),1);
			
			
			return 1;
		}
		
		//-------------------------viewBlog--------------------------------
		
		private int viewBlog(String loggedas) {
			
			//il blog è la lista di post pubblicati o rewinnati dall'utente
			readlockPosts.lock();
			ArrayList<Post> tmp = Posts.get(loggedas);
			
			if(tmp == null) {
				readlockPosts.unlock();
				sendResponse("Nessun post da visualizzare nel blog",1);
				return 1;
			}
			if(tmp.isEmpty()) {
				readlockPosts.unlock();
				sendResponse("Nessun post da visualizzare nel blog",1);
				return 1;
			}
			
			String answ = "";
			
			for(Post post : tmp) {
				if(post == null )
					continue;
				//se il post è un rewin lo riconosco dal fatto che l'autore non è chi è il proprietario del blog
				if(!post.getAuthor().equals(loggedas)) {
					answ += "Rewin\n" + "  ID: " + post.getId() + "\n" + "  Autore: " + post.getAuthor() + "\n" + "  Titolo del post: " + post.getTitle() + "\n";
					answ += "--------------------------------------------\n";
					
				}
				else {
					
					answ += "ID: " + post.getId() + "\n" + "Autore: " + post.getAuthor() + "\n" + "Titolo del post: " + post.getTitle() + "\n";
					answ += "--------------------------------------------\n";
				}
			}
			readlockPosts.unlock();
			sendResponse(answ,1);
			return 1;
		}
		
		
		//-------------------------showFeed--------------------------------
		private int showFeed(String loggedas) {
			
			//per ottenere il feed cerco i post di tutti gli utenti che seguo 
			readlockUtenti.lock();
			User user = Utenti.get(loggedas);
			ArrayList<String> seguiti = user.getFollowing();
			String answ = "";
			for(String string : seguiti) {
				ArrayList<Post> post = Posts.get(string);

				if(post == null || post.isEmpty())
					continue;
				for(Post p : post) {
					if(p == null)
						continue;
					if(!p.getAuthor().equals(string)) {
						answ += "Rewinnato da " + string + "\n  ID: " + p.getId() + "\n" + "  Autore: " + p.getAuthor() + "\n" + "  Titolo del post: " + p.getTitle() + "\n";
						answ += "--------------------------------------------\n";
					}
					else {
						answ += "ID: " + p.getId() + "\n" + "Autore: " + p.getAuthor() + "\n" + "Titolo del post: " + p.getTitle() + "\n";
						answ += "--------------------------------------------\n";
						
					}
				}
			}
			readlockUtenti.unlock();
			sendResponse(answ,1);
			return 1;
		}
		
		//-------------------------showPost--------------------------------
		private int showPost(String _idpost) {
			int idpost;
			try{
				idpost = Integer.parseInt(_idpost);
			} catch (NumberFormatException e) {
				sendResponse("Formato id non valido",0);
				return 0;
			}
			
			readlockAllposts.lock();
			for(Post post : allposts) {
				if(post == null)
					continue;
				if(post.getId() == idpost) {
					String answ = "";
					answ += "Titolo: " + post.getTitle() + "\n" + "Contenuto: " + post.getContent() + "\n" + "Likes: " + post.getLikesNumber()
							+ "\n" + "Dislikes: " + post.getDislikesNumber() + "\n" + "Comments: \n";
					for(Comment comment : post.getComments()) {
						answ += " " + comment.getAuthor() + ": " + comment.getComment() + "\n";
					}
					readlockAllposts.unlock();
					sendResponse(answ,1);
					return 1;
				}
			}
			readlockAllposts.unlock();
			sendResponse("Post inesistente",0);
			return 0;
		}
		
		//-------------------------rewinPost--------------------------------
		private int rewinPost(String _idpost) {
			int idpost;
			try{
				idpost = Integer.parseInt(_idpost);
			} catch (NumberFormatException e) {
				sendResponse("Formato id non valido",0);
				return 0;
			}
			Attachment att = (Attachment) key.attachment();
			
			Post rewinned = null;
			readlockPosts.lock();
			ArrayList<Post> tmppost = Posts.get(att.loggedAs);
			if(tmppost != null) {
				
				for(Post p : tmppost) {
					if(p.getId() == idpost) {
						if(p.getAuthor().equals(att.loggedAs)) {
							readlockPosts.unlock();
							sendResponse("Non puoi fare il rewin di un tuo post",0);
							return 0;
						}
						readlockPosts.unlock();
						sendResponse("Hai già fatto il rewin di questo post",0);
						return 0;
					}
				}
			}
			readlockPosts.unlock();
			readlockAllposts.lock();
			for(Post post : allposts) {
				if(post.getId() == idpost) {
					rewinned = post;
						
					//aggiungo in maniera sincronizzata il post rewinnato ai post dell'utente che ha fatto il rewin,
					//ora apparirà come un suo post
					writelockPosts.lock();
					readlockAllposts.unlock();
					writelockAllposts.lock();
					if(Posts.get(att.loggedAs) == null) {
						ArrayList<Post> nuovo = new ArrayList<Post>();
						nuovo.add(rewinned);
						Posts.put(att.loggedAs, nuovo);
					}
					else
						Posts.get(att.loggedAs).add(rewinned);
					
					writelockPosts.unlock();
					writelockAllposts.unlock();
					sendResponse("Post "+post.getId()+" rewinnato",1);
					return 1;
				}
			}
			readlockAllposts.unlock();
			sendResponse("Post inesistente",0);
			return 0;
		}
		
		//------------------------------deletePost-------------------------------------
		private int deletePost(String _idpost) {
			int idpost;
			//controllo che l'id sia valido
			try{
				idpost = Integer.parseInt(_idpost);
			} catch (NumberFormatException e) {
				sendResponse("Formato id non valido",0);
				return 0;
			}
			
			Attachment att = (Attachment) key.attachment();
			

			readlockAllposts.lock();
			for(Post post : allposts) {
				//se l'id del post che voglio eliminare è uguale a quello che ho trovato posso procedere
				//con l'eliminazione
				if(post.getId() == idpost) {
					
					//prima mi assicuro che l'autore del post sia chi ha richiesto l'eliminazione
					if(!att.loggedAs.equals(post.getAuthor())){
						readlockAllposts.unlock();
						
						sendResponse("Non sei l'autore del post",0);
						return 0;
					}
					readlockAllposts.unlock();
					writelockAllposts.lock();
					writelockPosts.lock();
					//tolgo il post da tutte le parti in cui si trova, compresi i rewin degli altri utenti
					allposts.remove(post);
					writelockAllposts.unlock();
					
					activitieslock.lock();
					Iterator<Activity> itr = activities.iterator();
					while(itr.hasNext()) {
						Activity act = itr.next();
						if(act.idpost == post.getId())
							itr.remove();
					}
					activitieslock.unlock();
					Set<String> kset = Posts.keySet();
					for(String string : kset) {
						ArrayList<Post> tmppost = Posts.get(string);
						
						for(Post p : tmppost) {
							if(p.getId() == post.getId()) {
								tmppost.remove(p);
								break;
							}
								
						}
					}
					writelockPosts.unlock();
					sendResponse("Post "+_idpost +" eliminato",1);
					return 1;
				}
			}
			readlockAllposts.unlock();
			sendResponse("Post inesistente",0);
			return 0;
		}
		//-------------------------------ratePost---------------------------------------
		
		private int ratePost(String _idpost,String vote) {
			int idpost;
			//controllo che l'id sia valido
			try{
				idpost = Integer.parseInt(_idpost);
			} catch (NumberFormatException e) {
				sendResponse("Formato id non valido",0);
				return 0;
			}
			
			if(!vote.equals("-1")) {
				if(!vote.equals("+1")) {
					sendResponse("Voto non valido",0);
					return 0;
				}
			}
			Attachment att = (Attachment) key.attachment();
			readlockAllposts.lock();
			readlockPosts.lock();
			for(Post post : allposts) {
				if(post.getId() == idpost) {
					if(post.getAuthor().equals(att.loggedAs)) {
						readlockAllposts.unlock();
						readlockPosts.unlock();
						sendResponse("Non puoi valutare un tuo post",0);
						return 0;
					}
					
					readlockUtenti.lock();
					User user = Utenti.get(att.loggedAs);
					if(!(user.getFollowing().contains(post.getAuthor()))) {
						readlockPosts.unlock();
						readlockAllposts.unlock();
						readlockUtenti.unlock();
						sendResponse("Non puoi valutare un post che non è nel tuo feed",0);
						return 0;
					}
					readlockUtenti.unlock();
					
					if(post.getLikes().contains(att.loggedAs)) {
						readlockAllposts.unlock();
						readlockPosts.unlock();
						sendResponse("Hai già valutato questo post",0);
						return 0;
					}
					//qua serve solo la readlock perchè i metodi dei post sono sincornizzati già da soli

					if(vote.equals("-1"))
						post.addDislike(att.loggedAs);
					else 
						post.addLike(att.loggedAs);
					activitieslock.lock();
					activities.add(new Activity (0,idpost,att.loggedAs,post.getAuthor(),post.getRewardCicle()));
					activitieslock.unlock();
					readlockAllposts.unlock();
					readlockPosts.unlock();
					sendResponse("Post valutato con successo",1);
					return 1;
				}
			}
			sendResponse("Post inesistente",0);
			return 0;
		}
		
		//-------------------------------addComment---------------------------------------
		
		private int addComment(String _idpost,String comment) {
			
			if(comment.length()>100) {
				sendResponse("Commento troppo lungo, massimo 100 caratteri",0);
				return 0;
			}
			
			int idpost;
			//controllo che l'id sia valido
			try{
				idpost = Integer.parseInt(_idpost);
			} catch (NumberFormatException e) {
				sendResponse("Formato id non valido",0);
				return 0;
			}
			
			Attachment att = (Attachment) key.attachment();
			readlockAllposts.lock();
			readlockPosts.lock();
			for(Post post : allposts) {
				if(post.getId() == idpost) {
					if(post.getAuthor().equals(att.loggedAs )) {
						readlockAllposts.unlock();
						readlockPosts.unlock();
						sendResponse("Non puoi commentare un tuo post",0);
						return 0;
					}
					
					readlockUtenti.lock();
					User user = Utenti.get(att.loggedAs);
					if(!(user.getFollowing().contains(post.getAuthor()))) {
						readlockUtenti.unlock();
						readlockAllposts.unlock();
						readlockPosts.unlock();
						sendResponse("Non puoi commentare un post che non è nel tuo feed",0);
						return 0;
					}
					readlockUtenti.unlock();
					Comment newcomment = new Comment(att.loggedAs,comment);
					//qua serve solo la readlock perchè i metodi dei post sono sincornizzati già da soli
					
					post.addComment(newcomment);
					
					activitieslock.lock();
					Activity act = new Activity (1,idpost,att.loggedAs,post.getAuthor(),post.getRewardCicle());
					act.numcomment = post.getCommentsAmmount(att.loggedAs);
					activities.add(act);
					activitieslock.unlock();
					readlockAllposts.unlock();
					readlockPosts.unlock();
					sendResponse("Commento aggiunto con successo",1);
					return 1;
				}
			}
			
			sendResponse("Post inesistente",1);
			return 0;

		}
		
		//-------------------------------getWallet---------------------------------------
		
		private int getWallet() {
			Attachment att = (Attachment) key.attachment();
			readlockWallet.lock();
			Wallet wal = Wallets.get(att.loggedAs);
			ArrayList<String> trans = wal.getTransactions();
			String tosend = "";
			tosend += "Ammontare wincoin: " + wal.getWincoin() + "\nStorico transazioni:\n";
			for(String string : trans) {
				tosend += string + "\n";
			}
			readlockWallet.unlock();
			sendResponse(tosend,1);
			return 1;
		}
		
		//-------------------------------getWalletInBitcoin---------------------------------------
		
				private int getWalletInBitcoin() {
					Attachment att = (Attachment) key.attachment();
					readlockWallet.lock();
					Wallet wal = Wallets.get(att.loggedAs);
					double wincoin = wal.getWincoin();
					readlockWallet.unlock();
					try {
						URL u = new URL("https://www.random.org/integers/?num=1&min=1&max=100&col=1&base=10&format=plain&rnd=new");
						URLConnection uc = u.openConnection();
						InputStream raw = uc.getInputStream();
						byte[] b = raw.readAllBytes();
						String as_string = new String(b);
						int btc = Integer.parseInt(as_string.trim());
						wincoin = (wincoin * btc)/100;
						sendResponse("Valore portafoglio convertito in bitcoin: " + wincoin,1);
						return 1;
					} catch (IOException e) {
						sendResponse("Conversione fallita, riprovare",0);
						return 0;
					}

				}
				
		//-------------------------------getFollowers---------------------------------------
				
		private int getFollowers() {
			Attachment att = (Attachment) key.attachment();
			readlockUtenti.lock();
			User user = Utenti.get(att.loggedAs);
			readlockUtenti.unlock();
			String response = "";
			//getFollowers è già di per sè un metodo sincronizzato, non ho bisogno di lock
			ArrayList<String> followers = user.getFollowers();
			if(followers.isEmpty()) {
				sendResponse("",1);
				return 1;
			}
			for(String string : followers)
				response += string + " ";
			sendResponse(response,1);
			return 1;
		}
		
		//-----------------------------------------sendResponse------------------------------------------
		//metodo generico per inviare una risposta al client, il tipo Response è una coppia che ha un intero
		//che identifica l'esito della richiesta (1 successo, 0 fallimento) e una stringa che contiene il motivo
		//del fallimento oppure la notifica del successo
		private void sendResponse(String response,int responseID) {
			
			Gson gson = new GsonBuilder().create();
			String json = gson.toJson(new Response(responseID,response));
			ByteBuffer bb = ByteBuffer.allocate(json.length());
			//la dimensione di un int
			ByteBuffer bb2 = ByteBuffer.allocate(4);
			bb.put(json.getBytes());
			int size = json.length();
			bb2.putInt(size);
			bb.flip();
			bb2.flip();
			
			//qua provo ad inviare la risposta, se fallisco per un errore di connessione la chiave viene cancellata e il canale chiuso
			try {
				//invio la dimensione del pacchetto
				while(bb2.hasRemaining())
					channel.write(bb2);
				//invio il pacchetto
				while(bb.hasRemaining())
					channel.write(bb);
			} catch (IOException e) {
				System.err.println("Errore nella comunicazione col client, chiusura canale in corso");
				key.cancel();
				try {
					key.channel().close();
				} catch (IOException e1) {
					System.err.println("Errore nella chiusura del canale");
				}
			
			}
		}
	}
	
	
	//------------------------------------------------metodo per il salvataggio-------------------------------------------------
	private static void save(){

					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					
				
					
					idpostslock.lock();
					try {
						Utils.writeFile(path_idposts, gson.toJson(idposts));
					} catch (IOException e) {
						System.err.println("Salvataggio idposts non riuscito");
					}
					idpostslock.unlock();
					
					rewardciclelock.lock();
					try {
						Utils.writeFile(path_rewardcicle, gson.toJson(reward_cicle));
					} catch (IOException e) {
						System.err.println("Salvataggio reward_cicle non riuscito");
					}
					rewardciclelock.unlock();
					
					readlockPosts.lock();
					try {
						Utils.writeFile(path_posts, gson.toJson(Posts));
					} catch (IOException e) {
						System.err.println("Salvataggio Posts non riuscito");
					}
					readlockPosts.unlock();
					
					readlockUtenti.lock();
					try {
						Utils.writeFile(path_utenti, gson.toJson(Utenti));
					} catch (IOException e) {
						System.err.println("Salvataggio Utenti non riuscito");
					}
					readlockUtenti.unlock();
					
					readlockAllposts.lock();
					try {
						Utils.writeFile(path_allposts, gson.toJson(allposts));
					} catch (IOException e) {
						System.err.println("Salvataggio allposts non riuscito");
					}
					readlockAllposts.unlock();
					
					readlockWallet.lock();
					try {
						Utils.writeFile(path_wallets, gson.toJson(Wallets));
					} catch (IOException e) {
						System.err.println("Salvataggio Wallets non riuscito");
					}
					readlockWallet.unlock();
					
					activitieslock.lock();
					try {
						Utils.writeFile(path_activities, gson.toJson(activities));
					} catch (IOException e) {
						System.err.println("Salvataggio attività non riuscito");
					}
					activitieslock.unlock();

		
		}
		
	//questa classe usata da un thread sta in attesa di ricevere il comando "end" da tastiera, se lo riceve
	//avvia lo spegnimento del server in maniera safe, ovvero terminando tutte le richieste ma non accettandone altre, per
	//poi terminare correttamente il server, salvando anche i dati su disco
	private static class EndServer implements Runnable {

		@Override
		public void run() {
			while(true) {
				Scanner keyboard = new Scanner(System.in);
				String command = keyboard.nextLine();
				if(command.equals("end")) {
					end = true;
					keyboard.close();
					try {
						//prima della chiusura del server mi accerto che le richieste accettate dal server siano prima eseguite
						//ovviamente non ne acetterò altre
						System.out.println("Terminazione server in corso...");
						pool.shutdown();
						pool.awaitTermination(10000,TimeUnit.MILLISECONDS);
						t_save.interrupt();
						t_save.join();
					} catch (InterruptedException e) {
						System.out.println("Qualcosa è andato storto nella chiusura dei threads");
					}
					save();
					System.out.println("Server terminato con successo");
					System.exit(0);
				}
			}
			
			
		}
		
	}
	
	//classe Runnable che ciclicamente salva lo stato del server
	private static class SaveServer implements Runnable {

		@Override
		public void run() {
			while(true) {
				try {
					Thread.sleep(checkpoint_time);
				} catch (InterruptedException e) {
					
				}
				if(end)
					return;
				save();
			}
			
			
		}
		
	}
	
	//-------------------classe che calcola le reward e le distribuisce---------------------
	private static class RewardCalculator implements Runnable {

		@Override
		public void run() {
			while(true) {
				try {
					Thread.sleep(reward_sleep_time);
				} catch (InterruptedException e) {	}
				
				if(end)
					return;
				
				ArrayList<Activity> tmp;
				
				//mi creo una copia locale delle attività cosi posso togliere quelle che calcolerò per le rewards 
				//e restituire la lock delle attività così da non bloccare altre operazioni del server
				activitieslock.lock();
				//se la lista delle attività è vuota 
				if(activities == null) {
					activitieslock.unlock();
					continue;
				}
				tmp = new ArrayList<Activity>(activities);
				//rimuovo dalle attività quelle che andrò a usare per il calcolo delle reward
				activities.removeAll(tmp);
				activitieslock.unlock();
				
				
				//mi creo dei gruppi, ogni gruppo avrà come chiave l'id del post associata ad una lista di attività
				//relative a quel post (like e commenti fatti al post)
				HashMap<Integer,ArrayList<Activity>> groups = new HashMap<Integer,ArrayList<Activity>>();
				for(Activity act : tmp) {
					if(groups.containsKey(act.idpost)) {
						groups.get(act.idpost).add(act);
					}
					else {
						ArrayList<Activity> a = new ArrayList<Activity>();
						a.add(act);
						groups.put(act.idpost, a);
					}
				}
				
				Set<Integer> kset = groups.keySet();
				
				//ciclo sugli id dei post andando a distinguere chi ha messo like e chi ha commentato
				for(Integer k : kset) {
					//lista di persone che hanno messo like, mi servirà in seguito
					ArrayList<String> peopleLike = new ArrayList<String>();
					String author;
					int rewcicle;
					double likes = 0;
					//associo a chi commenta il numero di commenti che ha fatto in totale
					HashMap<String,Integer> comments = new HashMap<String,Integer>();
					//prendo la lista delle attività di ogni gruppo
					ArrayList<Activity> act = groups.get(k);
					//mi salvo autore del post e cicloreward del post
					author = act.get(0).author;
					rewcicle = act.get(0).rewardcicle;
					for(Activity a : act) {
						//se è un like incremento il numero di like e aggiungo la persona alla lista
						if(a.type == 0) {
							likes++;
							peopleLike.add(a.madeby);
						}
						//altrimenti aggiungo il commentatore alla mappa dei commenti e aggiorno il suo numero di commenti totali sul post
						else {
							if(comments.containsKey(a.madeby)) {
								int num = comments.get(a.madeby);
								num ++;
								comments.put(a.madeby, num);
							}
							else {
								comments.put(a.madeby, a.numcomment);
							}
						}
						
					}
					//questa variabile serve per accumulare il valore totale dei commenti secondo la formula data
					double valcomments = 0;
					Set<String> ksetcomments = comments.keySet();
					if(!ksetcomments.isEmpty()) {
						for(String string : ksetcomments) {
							valcomments += 2/1+Math.exp(-(comments.get(string) -1));
							//aggiungo alla lista dei like anche chi ha commentato, cosi da avere una lista di curatori che andrò
							//ad usare per spartire la ricompensa curatori
							if(!peopleLike.contains(string))
								peopleLike.add(string);
						
						}
					}
					
					
					//qua viene calcolata la reward, per il valore "n_interazioni" viene tenuto di conto il ciclo in cui il post
					//è stato creato e quindi viene fatta la differenza tra il ciclo attuale e quello del post, per avere una valutazione
					//esatta, ho scritto in diversi passaggi la formula per una maggiore chiarezza
					double maxlikes = Math.max(likes,0);
					maxlikes++;
					valcomments ++;
					double reward = (Math.log(maxlikes) + Math.log(valcomments))/((reward_cicle - rewcicle)+1);

					//imposto un arrotondamento a 3 cifre e arrotondo la reward
					BigDecimal bd = BigDecimal.valueOf(reward);
				    bd = bd.setScale(3, RoundingMode.HALF_UP);
				    reward = bd.doubleValue();
					
					//qua vado ad aggiungere le ricompense ai vari curatori e all'autore del post, secondo la percentuale indicata nelle
					//variabili authorReward e curatorReward, per i curatori ovviamente dividerò la ricompensa tra tutti i curatori del post
					readlockWallet.lock();
					
					Wallets.get(author).addWincoin((reward*authorReward)/100);
					double rewardtocurators = ((reward*curatorReward)/100)/peopleLike.size();
					
					//arrotondo anche le rewards dei curatori
					bd = BigDecimal.valueOf(rewardtocurators);
					bd = bd.setScale(3,RoundingMode.HALF_UP);
					rewardtocurators = bd.doubleValue();

					for(String string : peopleLike) {
						
						Wallets.get(string).addWincoin(rewardtocurators);
					}
					readlockWallet.unlock();
				}
				String message = "Ricompense in wincoin calcolate e distribuite";
				
				byte[] buffer = message.getBytes();
				DatagramPacket packet = new DatagramPacket(buffer,buffer.length,group,multicastport);
				try {
					datagramsocket.send(packet);
				} catch (IOException e) {
					System.err.println("Errore nella notifica delle ricompense");
				}
				
				rewardciclelock.lock();
				reward_cicle++;
				rewardciclelock.unlock();
				
			}
			
		}
		
	}
}


