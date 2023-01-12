package dtu.group8.client;

import dtu.group8.server.ClientServer;
import dtu.group8.server.model.Player;
import dtu.group8.util.Printer;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;
import org.jspace.Space;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.UUID;

/**
 * Client
 * Responsibilities:
 * Must have:
 *      Receive questions
 *      Send answers
 *      Receive correct answer
 *      Show if answer was correct or wrong
 * Can have:
 *      See opponent points
 *      See timer
 *      See amount of remaining questions
 *      Reconnect to game
 */
public class Client {
    private final String PORT = "9002", IP = "localhost";
    private String clientName = "", clientID = "";
    //private static final String LOCALHOST = "10.209.95.114";
    private static final String TYPE = "?keep";
    private Player player;
    private BufferedReader input;
    public static Object[] allPlayers;
    private static final String JOIN_ME_REQ = "join_req", JOIN_ME_RES = "join_res";
    private RemoteSpace lobby;



    public Space matchMake(){
        try {
            Printer printer = new Printer("Client:matchMake",Printer.PrintColor.WHITE);
            if (input == null) {
                input = new BufferedReader(new InputStreamReader(System.in));
            }
            // ____________________________________ SETUP CONNECTION TO LOBBY ____________________________________
            clientID = UUID.randomUUID().toString();
            player = new Player(clientID);
            player.setName(clientName);
            if (lobby == null) {
                // Set the URI of the chat space
                printer.print("Enter URI of the chat server or press enter for default: ");
                String uri = input.readLine();
                // Default value
                if (uri.isEmpty()) {
                    //uri = "tcp://" + LOCALHOST + ":" + PORT + "/lobby" + TYPE;
                    uri = getUri("lobby");
                }
                // Connect to the remote chat space
                printer.println("Connecting to chat space " + uri + "...");

                lobby = new RemoteSpace(uri);

                // Read client name from the console
                printer.print("", "Enter your name: ", Printer.PrintColor.ANSI_RESET);

                clientName = input.readLine();
                player.setName(clientName);
            }

            // ____________________________________ JOIN LOBBY ____________________________________
            lobby.put("lobby", clientName, clientID);

            ThreadCreateBoard threadCreateBoard = new ThreadCreateBoard(lobby);
            Thread thread = new Thread(threadCreateBoard);
            thread.start();

            Object[] obj = lobby.get(new ActualField(clientID), new FormalField(String.class));
            thread.join();

            String spaceId = obj[1].toString();
            String uri2 = "tcp://" + IP + ":" + PORT + "/" + spaceId + TYPE;
            //String uri2 = "tcp://" + LOCALHOST + ":" + PORT + "/" + spaceId + TYPE;
            printer.println("You are connected to board " + spaceId);

            Space gameSpace = new RemoteSpace(uri2);
            startGame(gameSpace);
            return gameSpace;
        } catch (
                IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;

    }
    public Space startGame(Space space) {
        //____________________________________ SETUP FOR GAME ____________________________________
        if (space == null) {
            return null;
        }
        try {

            Printer printer = new Printer();
            Printer log = new Printer("PlayerLog", Printer.PrintColor.YELLOW);

            ThreadStartGame threadStartGame = new ThreadStartGame(space, player);
            Thread sThread = new Thread(threadStartGame);
            sThread.start();
            // Waiting for an invitation
            Object[] ackMsg = space.get(new ActualField(clientID), new FormalField(Object.class), new FormalField(Object.class));
            String invitedPlayerName = ackMsg[2].toString();

            Object[] obj = space.query(new ActualField("host"), new FormalField(Object.class));
            String hostClientId = obj[1].toString();
            sThread.join();
            // Checks if this client is the host
            if (!Objects.equals(hostClientId, clientID)) {
                while (true) {
                    System.out.println("You are invited to join " + invitedPlayerName + "'s game.\nWrite <ok> to join, or <no> to refuse. You have 10 seconds.");
                    String userInput = input.readLine();
                    if (userInput.equalsIgnoreCase("ok")) {
                        space.put(JOIN_ME_RES, "ok", clientID);
                        break;

                    } else if (userInput.equalsIgnoreCase("no")) {
                        space.put(JOIN_ME_RES, "no", clientID);
                        break;
                    }
                }
            } else {
                System.out.println("Waiting for player(s) to join...");
            }

            if (Objects.equals(hostClientId, clientID)) {
                Thread checkAckThread = new Thread(new Thread_Acknowledgement_ToJoinGame(space, false));
                checkAckThread.start();
                //checkAckThread.join();
                Thread sleepThread = new Thread(new Thread_Acknowledgement_ToJoinGame(space, true));
                sleepThread.start();
            }

            space.query(new ActualField("game started"));
            System.out.println("Game is starting...");

            ///// Game starts here.
            printer.println("Adding client");
            space.put("add", clientName, clientID);

            if (Objects.equals(hostClientId, clientID)) {
                System.out.println("You are the host.");
                Thread gameThread = new Thread(new ClientServer(space));
                gameThread.start();
                printer.println("Thread started", Printer.PrintColor.YELLOW);
            }

            // Connect to space
            // Get ack from space
            log.println("Getting ack");
            Object[] t = space.get(new ActualField(clientID), new FormalField(String.class));
            log.println("Got ack response");

            if (!t[1].equals("ok")) {
                System.out.println("Server did not ack... returning");
                return null;
            }
            //Wait for space to start
            log.println("Waiting for game state to be start");
            //Get game state
            t = space.query(new ActualField("gameState"), new FormalField(Integer.class));
            if (((Integer) t[1] == 1)) {
                log.println("Starting game...");
            } else {
                System.out.println("Failed to start game");
                return null;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return space;
    }

        public void start(Space space){
            Printer log = new Printer("PlayerLog", Printer.PrintColor.YELLOW);
            Printer printer = new Printer();
            try{
            //____________________________________ STARTING GAME ____________________________________
            log.println("playing game!");
            log.println("getting question size");
            Object[] size = space.query(new ActualField("QuizSize"),new FormalField(Integer.class));
            log.println("starting game loop");
            Object[] question;
            for (int i = 0; i < (Integer) size[1]; i++){
                printer.println("Question coming up!");
                question = space.query(new ActualField("Q" + i), new FormalField(String.class));
                printer.println("Question " + (i+1) + ":\n\t" + question[1].toString());
                log.println("Getting answer and sending it to space");

                questionGuess(space, log, printer, i);
            }
            log.println("Stopping game...");
            endGame();
            //____________________________________ EXCEPTION HANDLING ____________________________________
        } catch (
                InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
                e.printStackTrace();
            }

        }

    private void questionGuess(Space space, Printer log, Printer printer, int i) throws IOException, InterruptedException {
        //Tuple contains:
        //"A", clientId, answer, question index
        Object[] answer;
        printer.print("Please input your answer: ");
        space.put("A",clientID, input.readLine(),i);
        log.println("Waiting for verification of answer");
        answer = space.get(new ActualField("V"),new FormalField(String.class),new FormalField(Boolean.class));
        log.println("Received verification from Space");
        if ((boolean) answer[2]){
            printer.println("You got the answer correct!");
        } else{
            log.println("Getting correct answer");
            answer = space.query(new ActualField("CA" + i), new FormalField(String.class));
            printer.println("You got the answer wrong! The correct answer was " + answer[1]);
            questionGuess(space, log, printer, i);
        }
    }

    public void endGame(){
        Printer printer = new Printer();
        printer.print("Do you want join another lobby? y/n ");
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        try {
            String str =  input.readLine().trim();
            if (str.equalsIgnoreCase("y")){

                start(matchMake());
            } else {

            }
        }  catch (IOException e){
            e.printStackTrace();
        }
    }

    private String getUri(String parameter) {
        return  "tcp://" + IP + ":" + PORT + "/" + parameter + TYPE;
    }
}