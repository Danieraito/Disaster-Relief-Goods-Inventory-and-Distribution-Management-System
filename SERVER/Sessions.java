package SERVER;

import LIB.UserAccount;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers who is signed in. The browser keeps only a random token in a cookie. */
final class Sessions {
    private final Map<String, UserAccount> signedIn = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    String create(UserAccount user) {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        signedIn.put(token, user);
        return token;
    }

    UserAccount find(String token) {
        return token == null ? null : signedIn.get(token);
    }

    void remove(String token) {
        if (token != null) {
            signedIn.remove(token);
        }
    }
}
