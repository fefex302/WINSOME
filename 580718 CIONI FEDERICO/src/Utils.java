

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Utils {
	
	
	//funione che legge un file dato un percorso e restituisce una stringa json
	public static String readFile(String path) throws FileNotFoundException,IOException {
		
		String content = null;
        FileInputStream fin = new FileInputStream(path);
        byte[] byteArray = fin.readAllBytes();
        content = new String(byteArray, StandardCharsets.UTF_8);
        fin.close();
        return content;
      
    }
	
	
	public static void writeFile(String path, String file) throws IOException {
		FileOutputStream fop = new FileOutputStream(path);
		fop.write(file.getBytes());
		fop.close();
	}
}
