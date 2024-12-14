package com.linkedin.reach.Mastermind.game;

import com.linkedin.reach.Mastermind.utils.MastermindUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Class that manages the state of a game.
 */
@Slf4j
public class GameRoom {
    private final String roomId;
    private final List<WebSocketSession> players = new ArrayList<>();
    private String secretCode;
    private int currentPlayerIndex = 0;
    private int remainingTurns = MastermindUtils.DEFAULT_NUMBER_OF_TURNS;
    private final boolean isSinglePlayer;

    /**
     * Constructor of a game room.
     * @param roomId a UUID to identify the game room.
     */
    public GameRoom(String roomId, boolean isSinglePlayer) {
        this.roomId = roomId;
        this.isSinglePlayer = isSinglePlayer;
        this.secretCode = MastermindUtils.generateSecretCode(MastermindUtils.DEFAULT_CODE_LENGTH);
    }

    /**
     * Returns the current players in a game room.
     * @return a list of WebSocketSession.
     */
    public List<WebSocketSession> getPlayers() {
        return players;
    }

    /**
     * Adds a player to the game room.
     * @param session a WebSocketSession of a player.
     */
    public void addPlayer(WebSocketSession session) {
        players.add(session);
    }

    /**
     * This method is called when two players are matched in a game room. It broadcasts the start of the game.
     */
    public void startGame() {
        //TODO uncomment for debugging if needed
        broadcastMessage("Room ID: " + roomId);
        broadcastMessage(secretCode);

        broadcastMessage("Game started!");
        broadcastMessage("Guess the secret code in 10 turns or less!");
    }

    /**
     * Notifies the players the state of the game, such as turns remaining, a user trying to guess when it's not his turn,
     * the code is not the same length as the code generated, or if a player has guessed the secret code.
     * @param session the WebSocketSession of a player.
     * @param guess the secret code guessed.
     */
    public void handleGuess(WebSocketSession session, String guess) {
        if (isSinglePlayer) {
            currentPlayerIndex = 0;
        }

        if (!isPlayerGuessingTurn(session) || !guessLengthMatchesCodeLength(session, guess) || !isGuessOnlyNumbers(session, guess)) {
            return;
        }

        if (guess.equals(secretCode)) {
            broadcastMessage("Player " + session.getAttributes().get("username") + " wins!");
            broadcastMessage("The secret code was: " + secretCode);
            resetGame();
        } else {
            MastermindUtils.sendMessageToUser(session, String.format("Turns remaining: %d", (remainingTurns + 1)));
            String feedback = MastermindUtils.getFeedback(secretCode, guess);
            broadcastMessage("Player " + session.getAttributes().get("username") + " guessed: " + guess);
            broadcastMessage("Feedback: " + feedback);
            nextTurn();
        }
        if (remainingTurns == 0) {
            broadcastMessage("Everyone ran out of turns! Resetting the game");
            remainingTurns = 3;
            resetGame();
        }
    }

    /**
     * Helper method to verify that user sends a code with the correct length
     * @param session the session that sent the guess.
     * @param guess the code guessed by the user.
     * @return `true` if the guess length has the same length as the secret code, `false` otherwise
     */
    private boolean guessLengthMatchesCodeLength(WebSocketSession session, String guess) {
        if (guess.length() != secretCode.length()) {
            MastermindUtils.sendMessageToUser(session, "Your guess code is not the same digits length as the secret code!");
            return false;
        }
        return true;
    }

    /**
     * Validates if the given guess string contains only numbers.
     *
     * @param session the WebSocket session of the player who made the guess
     * @param guess   the player's guess as a string
     * @return `true` if the guess consists only of numbers, `false` otherwise
     */
    private boolean isGuessOnlyNumbers(WebSocketSession session, String guess) {
        if (!guess.matches("^[0-9]*$")) {
            MastermindUtils.sendMessageToUser(session, "The code contains only numbers!");
            return false;
        }
        return true;
    }

    /**
     * Checks if it's the current player's turn to guess.
     *
     * @param session the WebSocket session of the player who made the guess
     * @return `true` if it's the player's turn, `false` otherwise
     */
    private boolean isPlayerGuessingTurn(WebSocketSession session) {
        if (!players.get(currentPlayerIndex).equals(session)) {
            MastermindUtils.sendMessageToUser(session, "Wait for your turn!");
            return false;
        }
        return true;
    }

    /**
     * Helper method to control who is next to guess.
     */
    private void nextTurn() {
        if (remainingTurns == 0) {
            broadcastMessage("Everyone ran out of turns! Resetting the game");
            remainingTurns = 3;
            resetGame();
        }
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();

        if (isSinglePlayer || currentPlayerIndex == 1) {
            remainingTurns--;
        }
    }

    /**
     * Helper method to reset the game by generating the new code, resetting the turns and alternate the starter player.
     */
    private void resetGame() {
        this.secretCode = MastermindUtils.generateSecretCode(MastermindUtils.DEFAULT_CODE_LENGTH);
        this.remainingTurns = MastermindUtils.DEFAULT_NUMBER_OF_TURNS;
        nextTurn();
        broadcastMessage("Game reset! New secret code generated.");
        //TODO uncomment for debugging if needed
        broadcastMessage(secretCode);
    }

    /**
     * Helper method to broadcast a message to all the players.
     * @param message a String containing the message.
     */
    private void broadcastMessage(String message) {
        for (WebSocketSession player : players) {
            MastermindUtils.sendMessageToUser(player, message);
        }
    }
}