package org.example;

import java.net.*;
import java.io.*;
import java.util.Date;
import java.util.Scanner;

public class Client implements Runnable{
    private String host;
    private int port;
    private String username;
    private String password;
    private Scanner scanner = new Scanner(System.in);



    private String loginMessage;
    public void setLogin() {
        System.out.println("Har du en konto i forvejen? (Ja/Nej)");
        String choice = scanner.nextLine().trim().toUpperCase();

        System.out.println("Indtast venligst brugernavn: ");
        this.username = scanner.nextLine().trim();

        System.out.println("Hej " + this.username + " Indtast venligst et password nu");
        this.password = scanner.nextLine().trim();


        if (choice.equals("JA")) {
            this.loginMessage = username + "|" + java.time.LocalDateTime.now() +
                    "|LOGIN|" + username + "|" + password;
        } else if (choice.equals("NEJ")) {
            this.loginMessage = username + "|" + java.time.LocalDateTime.now() +
                    "|REGISTER|" + username + "|" + password;
        } else {
            System.out.println("Fejl i angivelse af JA/NEJ... ");
        }

    }

    public static void main(String[] args) {
        new Thread(new Client("localhost", 5000)).start();
    }

    public Client(String host, int port){
        this.host = host;
        this.port = port;
        setLogin();
    }

    @Override
    public void run() {
        try (
                Socket socket = new Socket(host, port);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Forbundet til serveren.");
            out.println (loginMessage);

            String loginResponse = in.readLine();

            if(!"OK".equals(loginResponse)) {
                System.out.println("Login fejlede. Lukker klient.");
                return;
            }
            System.out.println("Du er logget ind!");

            //Tråd til at lytte til server aktivitet
            new Thread(() -> {
                try {
                    String response;
                    while ((response = in.readLine()) != null) {

                        // 👇 Fix: ignorer READY her
                        if (response.equals("READY")) {
                            continue;
                        }

                        String[] parts = response.split("\\|");
                        if (parts.length < 4) {
                            System.out.println("Server: " + response);
                            continue;
                        }

                        String sender = parts[0];
                        String type = parts[2];
                        String payload = parts[3];

                        switch (type) {
                            case "INFO":
                                System.out.println("[INFO] " + payload);
                                break;
                            case "FEJL":
                                System.out.println("[FEJL] " + payload);
                                break;
                            case "BROADCAST":
                                System.out.println("[BROADCAST] " + sender + ": " + payload);
                                break;
                            case "PRIVATE":
                                System.out.println("[PM fra " + sender + "]: " + payload);
                                break;
                            case "TEXT":
                                System.out.println(sender + ": " + payload);
                                break;
                            default:
                                System.out.println("Server: " + response);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Forbindelsen til serveren blev afbrudt.");
                }
            }).start();

            String input;
            while ((input = userInput.readLine()) != null) {

                if(input.startsWith("/send ")) {
                    String fileName = input.substring(6).trim();
                    File file = new File(System.getProperty("user.home") + "\\Desktop", fileName);

                    if (!file.exists()) {
                        System.out.println("Filen findes ikke! ");
                        continue;
                    }

                    long fileSize = file.length();
                    String fileMetaData = username + "|" + java.time.LocalDateTime.now() +
                            "|FILE_TRANSFER|" + file.getName() + "|" + fileSize;
                    out.println(fileMetaData);

                    String ready = in.readLine();
                    if(!"READY".equals(ready)){
                        System.out.println("Server er ikke klar til filoverførsel... ");
                        continue;
                    }

                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
                         OutputStream os = socket.getOutputStream()) {

                        byte[] buffer = new byte[4096];
                        int bytesRead;

                        while ((bytesRead = bis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        os.flush();
                        System.out.println("Fil sendt: " + file.getName());
                    }
                } else {
                    String message = username + "|" + java.time.LocalDateTime.now() + "|TEXT|" + input;
                    out.println(message);
                }
            }

        } catch (IOException e) {
            System.err.println("[FEJL] Forbindelsen til serveren blev afbrudt: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
