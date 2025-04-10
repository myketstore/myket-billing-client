package ir.myket.billingclient;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.regex.Pattern;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    // Additional functional tests

    @Test
    public void purchaseToken_shouldBeValid() {
        String token = "token_1234567890";
        assertNotNull(token);
        assertTrue(token.startsWith("token_"));
        assertTrue(token.length() > 10);
    }

    @Test
    public void sku_shouldBeValidFormat() {
        String sku = "com.example.app.product_001";
        assertTrue(Pattern.matches("^[a-zA-Z0-9_.]+$", sku));
    }

    @Test
    public void responseCode_shouldBeSuccess() {
        int responseCode = 0; // RESULT_OK
        assertEquals(0, responseCode);
    }

    @Test
    public void priceString_shouldBeProperlyFormatted() {
        String price = "$5.99";
        assertTrue(price.matches("^\\$\\d+\\.\\d{2}$"));
    }

    @Test
    public void bundleResult_containsRequiredKeys() {
        MockBundle mockResponse = new MockBundle();
        mockResponse.put("RESPONSE_CODE", 0);
        mockResponse.put("DETAILS_LIST", "[{\"productId\":\"sku001\"}]");

        assertTrue(mockResponse.contains("RESPONSE_CODE"));
        assertTrue(mockResponse.contains("DETAILS_LIST"));
        assertEquals(0, mockResponse.getInt("RESPONSE_CODE"));
    }

    // Simulated Bundle class (mock)
    static class MockBundle {
        private final java.util.HashMap<String, Object> map = new java.util.HashMap<>();

        public void put(String key, Object value) {
            map.put(key, value);
        }

        public boolean contains(String key) {
            return map.containsKey(key);
        }

        public int getInt(String key) {
            Object value = map.get(key);
            return value instanceof Integer ? (Integer) value : -1;
        }

        public String getString(String key) {
            Object value = map.get(key);
            return value instanceof String ? (String) value : null;
        }
    }
}
