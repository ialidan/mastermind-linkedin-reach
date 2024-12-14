package com.linkedin.reach.Mastermind.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * This class provides utility methods for the Mastermind game and messaging.
 */
@Slf4j
public class MastermindUtils {


    /**
     * The base URL for the random.org API used for generating secret codes.
     */
    private static final String RANDOM_ORG_API_URL =
            "https://www.random.org/integers/?num=%d&min=0&max=7&col=1&base=10&format=plain&rnd=new";

    /**
     * The default number of turns allowed in a game. (0 is considered a valid turn, resulting in 10 total)
     */
    public static final int DEFAULT_NUMBER_OF_TURNS = 9;

    /**
     * The default length of the secret code for a game.
     */
    public static final int DEFAULT_CODE_LENGTH = 4;

    /**
     * RestTemplate object used to make API calls;
     */
    public static final RestTemplate restTemplate = new RestTemplate();

    /**
     * Generates a secret code using by calling with RestTemplate an external API (<a href="https://www.random.org/clients/http/api/">...</a>).
     * @param codeLength number of digits the secret code will have.
     * @return a secret code for a game as a String.
     */
    public static String generateSecretCode(int codeLength) {

        String response = restTemplate.getForObject(String.format(RANDOM_ORG_API_URL, codeLength), String.class);
        return response.replaceAll("\n", "");
    }

    /**
     * Provides feedback based on a secret code and a guess. It will only provide the number of correct digits that exist in the code but
     * are in the wrong place and the number of correct digits in a correct position, or will only say no matches if the guess does not
     * contain any numbers from the secret code.
     * @param secretCode the secret code that is being used in a game room
     * @param guess the code that a user guessed.
     * @return a String containing a hint about the number of correct digits and the number of correct digits in correct positions.
     */
    public static String getFeedback(String secretCode, String guess) {
        int correctPositions = 0;
        int correctDigits = 0;

        int[] secretFreq = new int[10];
        int[] guessFreq = new int[10];

        for (int i = 0; i < secretCode.length(); i++) {
            int secretDigit = secretCode.charAt(i) - '0';
            int guessDigit = guess.charAt(i) - '0';

            if (secretDigit == guessDigit) {
                correctPositions++;
            } else {
                secretFreq[secretDigit]++;
                guessFreq[guessDigit]++;
            }
        }

        for (int i = 0; i < 10; i++) {
            correctDigits += Math.min(secretFreq[i], guessFreq[i]);
        }

        return String.format("Correct digits: %d || Correct positions: %d", correctDigits, correctPositions);
    }

    /**
     * Helper method to send a message to a player.
     * @param player the player WebSocketSession.
     * @param message a String containing a message.
     */
    public static void sendMessageToUser(WebSocketSession player, String message) {
        try {
            player.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("Error sending message to player: {}", e.getMessage(), e);
        }
    }
}
