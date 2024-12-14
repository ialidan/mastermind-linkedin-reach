package com.linkedin.reach.Mastermind.server;

import com.linkedin.reach.Mastermind.game.GameRoom;
import com.linkedin.reach.Mastermind.utils.MastermindUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class handles WebSocket connections for the Mastermind game. It manages
 * player sessions, creates game rooms, and facilitates communication between
 * players and the game logic.
 * The server supports both multiplayer and single-player modes. Multiplayer
 * games require two players to be connected, while single-player games can be
 * played by a single user against the computer.
 */

@Controller
@Slf4j
public class MastermindWebSocketServer extends TextWebSocketHandler implements Runnable {

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> activeUsers = new HashMap<>();

    private final Map<WebSocketSession, String> playerRooms = new ConcurrentHashMap<>();
    private final Queue<WebSocketSession> waitingPlayers = new ConcurrentLinkedQueue<>();
    private final Thread gameManagerThread;

    /**
     *  Constructor.
     */
    public MastermindWebSocketServer() {
        gameManagerThread = new Thread(this);
        gameManagerThread.start();
    }

    /**
     * Serves the HTML page for players to connect.
     *
     * @return the name of the HTML file ("index")
     */
    @GetMapping("/")
    public String home() {
        return "index"; // HTML for connecting players (client)
    }

    /**
     * Handles a new WebSocket connection.
     *
     * If the connection URL ends with "/singleplayer", a single-player game
     * room is created and the game starts immediately. Otherwise, the player
     * is added to a waiting queue for multiplayer games.
     *
     * @param session the WebSocket session for the connected player
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String query = session.getUri().getQuery();
        Map<String, String> queryParams = parseQueryParams(query);
        String username = queryParams.get("username");

        if (username == null || username.trim().isEmpty()) {
            MastermindUtils.sendMessageToUser(session, "Error: A valid username is required to register." );
            try {
                session.close(CloseStatus.NOT_ACCEPTABLE);
            } catch (IOException e) {
                log.error("Error closing connection: ", e.getMessage(), e);
            }
            return;
        }

        if (activeUsers.get(username) != null) {
            MastermindUtils.sendMessageToUser(session, "Error: User is already connected to the server.");
            return;
        }

        session.getAttributes().put("username", username);
        activeUsers.put(username, session);

        if (session.getUri().getPath().endsWith("/singleplayer")) {
            String roomId = UUID.randomUUID().toString();
            GameRoom gameRoom = new GameRoom(roomId, true);
            gameRoom.addPlayer(session);
            gameRoom.startGame();
            gameRooms.put(roomId, gameRoom);
            playerRooms.put(session, roomId);
            return;
        }

        // Add the player to the waiting queue
        waitingPlayers.add(session);
    }

    /**
     * Handles a player disconnection.
     *
     * Removes the player from the game room and notifies other players in the
     * same room. The room is then closed if one player disconnects.
     *
     * @param session the WebSocket session for the disconnected player
     * @param closeStatus the reason for the disconnection
     */
    @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        String roomId = playerRooms.remove(session);

        if (roomId == null) {
            return;
        }
        GameRoom gameRoom = gameRooms.get(roomId);

        if (gameRoom == null) {
            return;
        }

        for (WebSocketSession player : gameRoom.getPlayers()) {
            if (!player.equals(session)) {
                MastermindUtils.sendMessageToUser(player, "Player " + session.getAttributes().get("username") + " disconnected! Closing room");
                playerRooms.remove(player);
            }
        }
        gameRooms.remove(roomId);
    }

    /**
     * Handles incoming text messages from players.
     *
     * The message is interpreted as a guess in the current game and processed
     * by the corresponding GameRoom object.
     *
     * @param session the WebSocket session for the player who sent the message
     * @param message the text message containing the player's guess
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String roomId = playerRooms.get(session);

        if (roomId == null) {
            MastermindUtils.sendMessageToUser(session, "Waiting to be assigned to a room...");
            return;
        }
        GameRoom gameRoom = gameRooms.get(roomId);

        if (gameRoom == null) {
            MastermindUtils.sendMessageToUser(session, "Current room does not exist anymore");
            return;
        }
        gameRoom.handleGuess(session, message.getPayload());
        // TODO debugging line, uncomment or comment for showcase
        MastermindUtils.sendMessageToUser(session, (String.format("Number of active game rooms: %d", gameRooms.size())));
    }

    /**
     * Parses a query string into a map of key-value pairs.
     *
     * Splits the query string into parameters and extracts keys and values
     * based on `=` and `&` delimiters.
     *
     * @param query the query string to parse (e.g., "key1=value1&key2=value2")
     * @return a map containing the extracted key-value pairs, or an empty map if the query is null or empty
     */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length == 2) {
                    params.put(pair[0], pair[1]);
                }
            }
        }
        return params;
    }

    /**
     * The main loop of the background thread.
     *
     * Continuously checks for enough players to start a new multiplayer game
     * and creates game rooms if needed.
     */
    @Override
    public void run() {
        while (true) {
            try {
                if (waitingPlayers.size() >= 2) {
                    String roomId = UUID.randomUUID().toString();
                    GameRoom gameRoom = new GameRoom(roomId, false);

                    WebSocketSession player1 = waitingPlayers.poll();
                    WebSocketSession player2 = waitingPlayers.poll();

                    if (player1 != null && player2 != null) {
                        gameRoom.addPlayer(player1);
                        gameRoom.addPlayer(player2);

                        gameRoom.startGame();
                        gameRooms.put(roomId, gameRoom);
                        playerRooms.put(player1, roomId);
                        playerRooms.put(player2, roomId);
                    }
                }

                // Sleep for a short duration to avoid busy-waiting
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // Exit the loop if the thread is interrupted
            }
        }
    }
}
