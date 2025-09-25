package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static List<ClientHandler> clients = new ArrayList<>();
    public static final Map<String, String> users = new HashMap<>();
    public static final Map<String, ClientHandler> activeUsers = new HashMap<>();


    public static void main(String[] args) {

        int port = 5000;
        int thread_Pool = 8;

        ExecutorService executor = Executors.newFixedThreadPool(thread_Pool);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server startet... Venter på forbindelse...");

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Klient forbundet! ");

                ClientHandler handler = new ClientHandler(socket);
                clients.add(handler);

                executor.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("[FEJL] Kunne ikke starte server på port " + port);
            e.printStackTrace();
        }
    }

    public static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }
}

class ClientHandler implements Runnable {

    private Socket socket;
    private PrintWriter out;


    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void sendMessage(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        ) {

            this.out = new PrintWriter(socket.getOutputStream(), true);

            String msg;

            while ((msg = in.readLine()) != null) {
                System.out.println("Besked modtaget fra klient: " + msg);

                String[] parts = msg.split("\\|");

                if (parts.length >= 4) {
                    String clientId = parts[0];
                    String timeStamp = parts[1];
                    String type = parts[2];

                    switch (type) {
                        case "LOGIN":
                            String user = parts[3];
                            String pass = parts[4];

                            if(Server.users.containsKey(user) && Server.users.get(user).equals(pass)) {
                                out.println("OK");
                                System.out.println("Login success: " + user);
                                Server.activeUsers.put(user, this);

                                sendMessage("System|" + timeStamp + "|INFO|Velkommen " + user +
                                        "! Skriv /list for at se brugere, /bc <besked> for broadcast, /pm <navn> <besked> for privat.");
                            } else {
                                out.println("FAIL");
                                socket.close();
                                return;
                            }
                            break;


                        // HUSK AT LÆSE KOMMENTARER FOR GENBRUG!!!
                        // HUSK AT LÆSE KOMMENTARER FOR GENBRUG!!!
                        // HUSK AT LÆSE KOMMENTARER FOR GENBRUG!!!
                        // HUSK AT LÆSE KOMMENTARER FOR GENBRUG!!!
                        // HUSK AT LÆSE KOMMENTARER FOR GENBRUG!!!
                        // HUSK AT LÆSE KOMMENTARER FOR GENBRUG!!!


                        case "REGISTER":
                            String newUser = parts[3];
                            String newPass = parts[4];

                            if(Server.users.containsKey(newUser)) {
                                out.println("Fejl, brugernavn eksisterer allerede");
                                socket.close();
                                return;
                            } else {
                                Server.users.put(newUser, newPass);
                                Server.activeUsers.put(newUser, this);
                                out.println("OK");
                                System.out.println("Ny bruger registreret: " + newUser);
                                sendMessage("System|" + timeStamp + "|INFO|Velkommen " + newUser + "! Skriv /list for at se brugere, /bc <besked> for broadcast, /pm <navn> <besked> for privat og /send 'filnavn.txt' af en fil der ligger på skrivebordet");
                            }
                            break;

                        case "TEXT":
                            String textPayload = parts[3];
                            if (textPayload.startsWith("/list")) {
                                String online = String.join(", ", Server.activeUsers.keySet());
                                sendMessage(("System|" + timeStamp + "|INFO|Online brugere: " + online));


                            } else if (textPayload.startsWith("/bc ")) {
                                String bcMsg = textPayload.substring(4);
                                Server.broadcast(clientId + "|" + timeStamp + "|BROADCAST|" + bcMsg, this);
                                System.out.println("[BC] " + clientId + ": " + bcMsg);



                            } else if (textPayload.startsWith("/pm ")) {
                                String cmdRemoveLine = textPayload.substring(4).trim(); // Jeg fjerner kommandoen /pm

                                int startUser = cmdRemoveLine.indexOf("<");
                                int endUser = cmdRemoveLine.indexOf(">");
                                int startPMMsg = cmdRemoveLine.indexOf("<", endUser + 1);
                                int endPMMsg = cmdRemoveLine.lastIndexOf(">");

                                if (startUser == -1 || endUser == -1 || startPMMsg == -1 || endPMMsg == -1) {
                                    sendMessage("System|" + timeStamp + "|FEJL|Brug: /pm <brugernavn> <besked> Ingen ekstra mellemrum tilladt eller lign. :)");
                                } else {
                                    String receiver = cmdRemoveLine.substring(startUser + 1, endUser).trim();
                                    String PMMsg = cmdRemoveLine.substring(startPMMsg + 1, endPMMsg).trim();

                                    ClientHandler target = Server.activeUsers.get(receiver);
                                    if (target != null) {
                                        target.sendMessage(clientId + "|" + timeStamp + "|PRIVATE|" + PMMsg);
                                        sendMessage("System|" + timeStamp + "|INFO|PM sendt til " + receiver);
                                    } else {
                                        sendMessage("System|" + timeStamp + "|FEJL|Brugeren findes ikke eller er offline");
                                    }
                                }
                            }

                            break;

                        case "FILE_TRANSFER":
                            String fileName = parts[3];
                            long fileSize = Long.parseLong(parts[4]);

                            out.println("READY"); // sig til klienten at vi er klar til en ny fil

                            try (FileOutputStream fos = new FileOutputStream("uploads_" + fileName)) {
                                InputStream is = socket.getInputStream();

                                byte[] buffer = new byte[4096];
                                long totalRead = 0;
                                int bytesRead;

                                while (totalRead < fileSize && (bytesRead = is.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesRead);
                                    totalRead += bytesRead;
                                }

                                fos.flush();
                                System.out.println("Fil modtaget: " + fileName + " (" + totalRead + " bytes)");

                                // Fortæl alle andre i chatten at en fil er modtaget
                                Server.broadcast(clientId + "|" + timeStamp + "|TEXT|" +
                                        clientId + " har sendt en fil: " + fileName, this);

                            } catch (IOException e) {
                                System.err.println("[FEJL] Under filoverførsel fra klient: " + e.getMessage());
                                e.printStackTrace();
                            } break;
                    }
                }

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
