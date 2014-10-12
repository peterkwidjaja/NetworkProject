
import java.io.*;
import java.net.*;
/**
 *
 * @author Peter Kristianto Widjaja
 */
public class FTPClient {
    static final String mainDir = "client-directory";
    static private Socket controlSocket;
    static private Socket dataSocket;
    static BufferedReader controlInput;
    static DataOutputStream controlOutput;
    static BufferedReader dataInput;
    static BufferedOutputStream dataOutput;
    static BufferedWriter bwLog;
    
    private static void setControl(InetAddress IP, int port) throws IOException{
        controlSocket = new Socket(IP, port);
        controlInput = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        controlOutput = new DataOutputStream(controlSocket.getOutputStream());
    }
    private static void sendPASV() throws IOException{
        controlOutput.writeBytes("PASV\r\n");
        controlOutput.flush();
    }
    private static String receiveControl() throws IOException{
        String data = controlInput.readLine();
        return data;
    } 
    private static void setDataPort(InetAddress IP, int port) throws IOException{
        dataSocket = new Socket(IP, port);
        dataInput = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        dataOutput = new BufferedOutputStream(new DataOutputStream(dataSocket.getOutputStream()));
    }
    private static void sendCommand(String command) throws IOException{
        controlOutput.writeBytes(command);
        controlOutput.flush();
    }
    private static void closeControl() throws IOException{
        controlSocket.close();
        controlInput.close();
        controlOutput.close();
    }
    private static void closeData() throws IOException{
        dataSocket.close();
        dataInput.close();
        dataOutput.close();
    }
    //check response. Will return true for 200, will close connection for other responses.
    private static boolean checkResponse(String response) throws IOException{
        String responseCode = response.substring(0, response.indexOf(" "));
        if(responseCode.equals("500")){
            bwLog.write(response);
            bwLog.close();
            closeControl();
            return false;
        }
        else if(responseCode.equals("501")){
            bwLog.write(response);
            bwLog.close();
            closeControl();
            return false;
        }
        else if(responseCode.equals("200")){
            return true;
        }
        return false;
    }
    private static void execDir() throws IOException{
        File outFile = new File(mainDir, "directory_listing");
        if(!outFile.exists()){
            outFile.createNewFile();
        }
        BufferedWriter bwFile = new BufferedWriter(new FileWriter(outFile));
        int input;
        while((input=dataInput.read())!=-1){
            bwFile.write(input);
        }
        String closeCommand = receiveControl();
        if(closeCommand.equalsIgnoreCase("200 OK")){
            bwLog.write(closeCommand);
        }
        bwFile.close();
        closeData();
    }
    private static void execGet(String path) throws IOException{
        String filename = path.substring(path.lastIndexOf("/")+1);
        File outFile = new File(mainDir, filename);
        if(!outFile.exists()){
            outFile.createNewFile();
        }
        BufferedWriter bwFile = new BufferedWriter(new FileWriter(outFile));
        int input;
        while((input=dataInput.read())!=-1){
            bwFile.write(input);
        }
        String closeCommand = receiveControl();
        if(closeCommand.equalsIgnoreCase("200 OK")){
            bwLog.write(closeCommand);
        }
        bwFile.close();
        closeData();
    }
    private static void execPut(File path) throws IOException{
        BufferedReader brFile = new BufferedReader(new FileReader(path));
        int input;
        while((input=brFile.read())!=-1){
            dataOutput.write(input);
        }
        closeData();
        String closeCommand = receiveControl();
        if(closeCommand.equalsIgnoreCase("200 OK")){
            bwLog.write(closeCommand);
        }         
    }
    
    public static void main(String[] args) throws UnknownHostException, IOException{
        bwLog = new BufferedWriter(new FileWriter(new File("log")));
        String command = args[2];
        
        if(command.equalsIgnoreCase("DIR")){
            InetAddress serverControlIP = InetAddress.getByName(args[0]);
            int controlPort = Integer.parseInt(args[1]);
            setControl(serverControlIP, controlPort);   //connects to server port            
            sendPASV();                         //send PASV to server
            String[] serverReplyPort = receiveControl().split(" "); //receive response from server
            if(!serverReplyPort[0].equalsIgnoreCase("200")){
                return;
            }
            InetAddress serverDataIP = InetAddress.getByName(serverReplyPort[2]);
            int dataPort = Integer.parseInt(serverReplyPort[3]);            
            sendCommand("DIR\r\n");
            String serverReplyCommand = receiveControl();
            if(checkResponse(serverReplyCommand)){
                setDataPort(serverDataIP, dataPort);
                execDir();
                closeControl();
            }
        }
        
        else if(command.equalsIgnoreCase("GET")){
            InetAddress serverControlIP = InetAddress.getByName(args[0]);
            int controlPort = Integer.parseInt(args[1]);
            setControl(serverControlIP, controlPort);   //connects to server port                
            sendPASV();                         //send PASV to server
            String[] serverReplyPort = receiveControl().split(" "); //receive response from server
            if(!serverReplyPort[0].equalsIgnoreCase("200")){
                return;
            }
            InetAddress serverDataIP = InetAddress.getByName(serverReplyPort[2]);
            int dataPort = Integer.parseInt(serverReplyPort[3]);            
            String path = args[3];
            sendCommand("GET "+path+"\r\n");
            String serverReplyCommand = receiveControl();
            if(checkResponse(serverReplyCommand)){
                setDataPort(serverDataIP, dataPort);
                execGet(path);
                closeControl();
            }
        }
        
        else if(command.equalsIgnoreCase("PUT")){
            String pathClient = args[3];
            File clientFile = new File(mainDir, pathClient);
            if(clientFile.exists() && clientFile.isFile()){
                String pathServer = "";
                if(args.length==5){
                    pathServer = args[4];
                }
                InetAddress serverControlIP = InetAddress.getByName(args[0]);
                int controlPort = Integer.parseInt(args[1]);
                setControl(serverControlIP, controlPort);               //connects to server port                
                sendPASV();                                             //send PASV to server
                String[] serverReplyPort = receiveControl().split(" "); //receive response from server
                if(!serverReplyPort[0].equalsIgnoreCase("200")){
                    return;
                }
                InetAddress serverDataIP = InetAddress.getByName(serverReplyPort[2]);
                int dataPort = Integer.parseInt(serverReplyPort[3]);
                sendCommand("PUT "+pathClient+" "+pathServer+"\r\n");
                String serverReplyCommand = receiveControl();
                if(checkResponse(serverReplyCommand)){
                    setDataPort(serverDataIP, dataPort);
                    execPut(clientFile);
                    closeControl();
                }
            }
            else{
                System.out.println("testNotFound");
                bwLog.write("FILE NOT FOUND");
            } 
        }
        bwLog.close();
    }
}