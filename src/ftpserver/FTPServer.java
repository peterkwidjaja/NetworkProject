
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
/**
 *
 * @author Peter Kristianto Widjaja
 */
public class FTPServer {
    static final String mainDir = "server-directory";
    static String serverIP = "";
    static private ServerSocket welcomeControlSocket, welcomeDataSocket;
    static private Socket controlSocket, dataSocket;
    static private BufferedReader brControl, brData;
    static private DataOutputStream outControl;
    static private BufferedOutputStream outData;
    private static void setControl(int port) throws IOException{
        welcomeControlSocket = new ServerSocket(port);
        serverIP = welcomeControlSocket.getInetAddress().getHostAddress();
    }
    private static void waitConn() throws IOException{
        controlSocket = welcomeControlSocket.accept();
        brControl = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        outControl = new DataOutputStream(controlSocket.getOutputStream());
    }
    private static void waitData(int port) throws IOException{
        welcomeDataSocket = new ServerSocket(port);
        dataSocket = welcomeDataSocket.accept();
        brData = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        outData = new BufferedOutputStream(new DataOutputStream(dataSocket.getOutputStream()));
    }
    private static String receiveControl() throws IOException{
        return brControl.readLine();
    }
    private static void sendControl(String message) throws IOException{
        outControl.writeBytes(message+"\r\n");
        outControl.flush();
    }
    private static boolean checkCommand(String[] command) throws IOException{
        if(command[0].equals("DIR")){
            if(command.length==1){
                sendControl("200 DIR COMMAND OK");
                return true;
            }
            else{
                sendControl("501 INVALID ARGUMENTS");
                return false;
            }    
        }
        else if(command[0].equals("GET")){
            if(command.length==2){
                File inFile = new File(mainDir,command[1]);
                if(inFile.exists() && inFile.isFile()){
                    sendControl("200 GET COMMAND OK");
                    return true;
                }
                else{
                    sendControl("401 FILE NOT FOUND");
                    return false;
                }
            }
            else{
                sendControl("501 INVALID ARGUMENTS");
                return false;
            }
        }
        else if(command[0].equals("PUT")){
            if(command.length==2 || command.length==3){
                sendControl("200 PUT COMMAND OK");
                return true;
            }
            sendControl("501 INVALID ARGUMENTS");
            return false;
        }
        else{
            sendControl("500 UNKNOWN COMMAND");
            return false;
        }
    }
    private static void closeControlSocket() throws IOException{
        brControl.close();
        outControl.close();
        controlSocket.close();
    }
    private static void closeDataChannel() throws IOException{
        outData.close();
        brData.close();
        dataSocket.close();
        welcomeDataSocket.close();
    }
    private static void printDir(File parent, String pathname, ArrayList allFiles) throws IOException{
        File[] listDir = parent.listFiles();
        if(listDir.length==0){
            allFiles.add(pathname+"/");
        }
        else{
            for(int i=0;i<listDir.length;i++){
                String filename = listDir[i].getName();
                if(listDir[i].isDirectory())
                    printDir(listDir[i],pathname+"/"+filename,allFiles);
                else if(listDir[i].isFile()){
                    allFiles.add(pathname+"/"+filename);
                }
            }            
        }
    }
    private static void execDir() throws IOException{
        File serverDir = new File(mainDir);
        File[] listDir = serverDir.listFiles();
        if(listDir.length==0){
            outData.write("---the server directory is empty---".getBytes());
        }
        else{
            ArrayList<String> allFiles = new ArrayList<String>();
            for(int i=0;i<listDir.length;i++){
                String filename = listDir[i].getName();
                if(listDir[i].isDirectory())
                    printDir(listDir[i],filename,allFiles);
                else if(listDir[i].isFile()){
                    allFiles.add(filename);
                }
            }
            Collections.sort(allFiles);
            int i;
            for(i=0;i<allFiles.size()-1;i++){
                outData.write((allFiles.get(i)+"\n").getBytes());
            }
            outData.write((allFiles.get(i)).getBytes());
        }
        sendControl("200 OK");
        closeDataChannel();
    }
    private static void execGet(String path) throws FileNotFoundException, IOException{
        File inFile = new File(mainDir, path);
        BufferedReader brFile = new BufferedReader(new FileReader(inFile));
        int out;
        while((out=brFile.read())!=-1){
            outData.write(out);
        }
        sendControl("200 OK");
        closeDataChannel();
    }
    private static void execPut(String filename, String path) throws IOException{
        File outFile = new File(mainDir+"/"+path+"/"+filename);
        outFile.getParentFile().mkdirs();
        if(!outFile.exists()){
            outFile.createNewFile();
        }
        BufferedWriter bwFile = new BufferedWriter(new FileWriter(outFile));
        int input;
        while((input=brData.read())!=-1){
            bwFile.write(input);
        }
        bwFile.close();
        sendControl("200 OK");
        closeDataChannel();
    }
    public static void main(String[] args) throws IOException{
        int controlPort = Integer.parseInt(args[0]);
        int dataPort = controlPort + 1;
        if(args.length==2){
            dataPort = Integer.parseInt(args[1]);
        }
        setControl(controlPort);
        while(true){
            waitConn();
            String pasv = receiveControl();
            sendControl("200 PORT "+serverIP+" "+dataPort);
            String[] command = receiveControl().split(" ");
            if(checkCommand(command)){
                waitData(dataPort);
                if(command[0].equals("DIR")){
                    execDir();
                }
                else if(command[0].equals("GET")){
                    execGet(command[1]);
                }
                else if(command[0].equals("PUT")){
                    String path="";
                    if(command.length==3)
                        path = command[2];
                    String filename = command[1].substring(command[1].lastIndexOf("/")+1);
                    execPut(filename, path);
                }
            }
        }
    }
}
