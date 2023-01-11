package dtu.group8.client;

import dtu.group8.server.Game;
import dtu.group8.server.model.Player;
import dtu.group8.util.Printer;
import org.jspace.ActualField;
import org.jspace.FormalField;
import org.jspace.RemoteSpace;
import org.jspace.Space;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Objects;

import static dtu.group8.client.Client.*;

public class GameSetup {
    private static final String LOCK_FOR_GAME_START = "lockForGameStart";
    private static final String JOIN_ME_REQ = "join_req";
    BufferedReader input = new BufferedReader(new InputStreamReader(System.in));


    private RemoteSpace lobbySpace;

    public GameSetup(RemoteSpace lobbySpace) {
        this.lobbySpace = lobbySpace;

    }

    public Game initializeGame(Player player) {
        Game game = new Game();
        game.setMe(player);
        final String OPTIONS = "Options:\n\t1. create game\n\t2. join game";
        try {
            while (true) {
                System.out.println(OPTIONS);
                System.out.print("Input command: ");
                String userInput = input.readLine();
                if (userInput.equalsIgnoreCase("create game") ||
                        userInput.equalsIgnoreCase("1")){

                    //remoteSpace.get(new ActualField("createBoardLock"));
                    System.out.print("Enter board name: ");
                    String gameName = input.readLine();
                    game.setName(gameName);
                    game.setHost(player.getId());
                    lobbySpace.put("create game",gameName, player.getId(), player.getName());
                    getSpace(game);
                    break;

                } else if (userInput.equalsIgnoreCase("2") || userInput.equalsIgnoreCase("join game")){
                    joinGame(game);
                    break;
                }
            }

        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        return game;
    }


    public void display_start_game_option (Game game) {
        Space space = game.getSpace();
        try {
            final String OPTIONS = "Options:\n\t1. start game\n\tor just wait for other to join";

            while (true) {
                System.out.println(OPTIONS);
                System.out.print("Input command: ");
                String userInput = input.readLine();
                if (userInput.equalsIgnoreCase("1") || userInput.equalsIgnoreCase("start game")){
                    getAllPlayersFromSpace(game);
                    space.put("gameStart");
                    break;

                }
            }
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    void getAllPlayersFromSpace(Game game) throws InterruptedException {
        Object[] obj = game.getSpace().get(new ActualField("allPlayers"), new FormalField(ArrayList.class), new FormalField(ArrayList.class));
        ArrayList<String> playerNames = (ArrayList<String>) obj[1];
        ArrayList<String> playerIds = (ArrayList<String>) obj[2];
        assert playerIds.size() != playerNames.size() : "players.size != playerIds.size";

        for (int i = 0; i < playerNames.size(); i++) {
            Player currPlayer = new Player(playerNames.get(i), playerIds.get(i), 0);
            game.addPlayer(currPlayer);
        }

    }

    void getSpace(Game game) throws InterruptedException, IOException {
        Printer printer = new Printer("GameSetup: getSpace", Printer.PrintColor.WHITE);

        Object[] obj = lobbySpace.get(new ActualField("mySpaceId"), new ActualField(game.getMe().getId()), new FormalField(Object.class), new FormalField(Object.class));
        game.setName(obj[3].toString());

        if (Objects.equals(game.getHost(), game.getMe().getId()))
            printer.println("Game " + game.getName() +" created");

        game.setId(obj[2].toString()); // spaceId/gameId
        String uri2 = "tcp://" + IP + ":" + PORT + "/" + game.getId() + TYPE;
        printer.println("You are connected to game " + game.getName());
        game.setSpace(new RemoteSpace(uri2));
    }


    void joinGame(Game game) throws InterruptedException, IOException {
        //Printer printer = new Printer("GameSetup:joinGame", Printer.PrintColor.WHITE);

        String myId =  game.getMe().getId();
        lobbySpace.put("showMeAvailableGames", myId);
        Object[] obj = lobbySpace.get(new ActualField(myId), new FormalField(ArrayList.class));
        ArrayList<String> arr = (ArrayList<String>) obj[1];

        System.out.println("Available game(s):");

        for (String s : arr) {
            String[] currGame = s.split("::", 2);

            System.out.println("\t" + currGame[0]);
        }
        System.out.println("Enter a game name to join: ");

        String userInput = input.readLine();

    }
}
