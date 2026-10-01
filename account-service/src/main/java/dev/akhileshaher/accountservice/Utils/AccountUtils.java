package dev.akhileshaher.accountservice.Utils;


import java.util.Random;

public class AccountUtils {

    public static String generateAccNo() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        sb.append(6);
        for (int i = 0; i < 11; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

}
