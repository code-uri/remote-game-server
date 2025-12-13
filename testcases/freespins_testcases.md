# Remote Game Server - API Test Cases Documentation
**Project:** Remote Game Server
**Date:** December 12, 2025
**Version:** 1.0

---

## Table of Contents
1. [Award Bonus Promotion API Test Cases](#1-award-bonus-promotion-api-test-cases)
2. [Get Promotion API Test Cases](#2-get-promotion-api-test-cases)
3. [Cancel Promotion API Test Cases](#3-cancel-promotion-api-test-cases)
4. [Claim Promotion API Test Cases](#4-claim-promotion-api-test-cases)
5. [Game Launch API Test Cases](#5-game-launch-api-test-cases)
6. [Test Summary](#test-summary)

---

## 1. Award Bonus Promotion API Test Cases

### Test Case ID: TC_AWARD_PROMO_001
**Title:** Verify awarding promotion with valid data

**Pre-conditions:**
- Valid client credentials (X-Client-ID and X-Client-Key) exist in the system
- Target player account exists
- Game provider is configured
- Brand is active

**Test Data:**
```json
Headers:
  X-Client-ID: "client_12345"
  X-Client-Key: "key_abc123xyz"

Request Body:
{
  "promotionRefId": "PROMO_2025_001",
  "gameProvider": "Pragmatic",
  "brand": "TestBrand",
  "player": "player123",
  "games": ["slot_001", "slot_002"],
  "playerTags": ["VIP", "HIGHROLLER"],
  "promotionType": "FREE_SPINS",
  "startDate": "2025-12-12T00:00:00Z",
  "endDate": "2025-12-31T23:59:59Z",
  "freeSpins": 50,
  "payLines": 10,
  "betAmounts": {
    "USD": 1.0,
    "EUR": 0.9
  },
  "status": "ACTIVE"
}
```

**Steps to Execute:**
1. Send POST request to `/games/promotions/award`
2. Include valid X-Client-ID header
3. Include valid X-Client-Key header
4. Include request body with all required fields
5. Verify response status code
6. Verify response body structure

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains:
  - `promotionId`: Generated unique ID
  - `promotionRefId`: "PROMO_2025_001"
  - `status`: "ACTIVE"

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_AWARD_PROMO_002
**Title:** Verify awarding promotion with missing client ID header

**Pre-conditions:**
- API endpoint is accessible

**Test Data:**
```json
Headers:
  X-Client-Key: "key_abc123xyz"

Request Body:
{
  "promotionRefId": "PROMO_2025_002",
  "gameProvider": "Pragmatic",
  "brand": "TestBrand",
  "player": "player123",
  "promotionType": "FREE_SPINS",
  "freeSpins": 50
}
```

**Steps to Execute:**
1. Send POST request to `/games/promotions/award`
2. Omit X-Client-ID header
3. Include valid X-Client-Key header
4. Include request body
5. Observe response

**Expected Result:**
- HTTP Status: 400 Bad Request or 401 Unauthorized
- Error message indicating missing client ID

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_AWARD_PROMO_003
**Title:** Verify awarding promotion with invalid client credentials

**Pre-conditions:**
- API endpoint is accessible

**Test Data:**
```json
Headers:
  X-Client-ID: "invalid_client"
  X-Client-Key: "invalid_key"

Request Body:
{
  "promotionRefId": "PROMO_2025_003",
  "gameProvider": "Pragmatic",
  "brand": "TestBrand",
  "player": "player123",
  "promotionType": "FREE_SPINS",
  "freeSpins": 50
}
```

**Steps to Execute:**
1. Send POST request to `/games/promotions/award`
2. Include invalid X-Client-ID header
3. Include invalid X-Client-Key header
4. Include valid request body
5. Observe error response

**Expected Result:**
- HTTP Status: 401 Unauthorized or 403 Forbidden
- Error message indicating authentication failure

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_AWARD_PROMO_004
**Title:** Verify awarding promotion with duplicate promotion reference ID

**Pre-conditions:**
- Valid client credentials exist
- Promotion with reference ID "PROMO_EXISTING_001" already exists

**Test Data:**
```json
Headers:
  X-Client-ID: "client_12345"
  X-Client-Key: "key_abc123xyz"

Request Body:
{
  "promotionRefId": "PROMO_EXISTING_001",
  "gameProvider": "Pragmatic",
  "brand": "TestBrand",
  "player": "player123",
  "promotionType": "FREE_SPINS",
  "freeSpins": 50
}
```

**Steps to Execute:**
1. Send POST request to `/games/promotions/award`
2. Include valid headers
3. Use existing promotionRefId
4. Observe response

**Expected Result:**
- HTTP Status: 400 Bad Request or 409 Conflict
- Error message indicating duplicate promotion reference ID

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_AWARD_PROMO_005
**Title:** Verify awarding promotion with missing required fields

**Pre-conditions:**
- Valid client credentials exist

**Test Data:**
```json
Headers:
  X-Client-ID: "client_12345"
  X-Client-Key: "key_abc123xyz"

Request Body:
{
  "promotionRefId": "PROMO_2025_005"
}
```

**Steps to Execute:**
1. Send POST request to `/games/promotions/award`
2. Include valid headers
3. Include minimal request body (missing required fields)
4. Observe validation error

**Expected Result:**
- HTTP Status: 400 Bad Request
- Error message listing missing required fields

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

## 2. Get Promotion API Test Cases

### Test Case ID: TC_GET_PROMO_001
**Title:** Verify retrieving promotion with valid promotion reference ID

**Pre-conditions:**
- Promotion with reference ID "PROMO_2025_001" exists in the system

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_2025_001"
```

**Steps to Execute:**
1. Send GET request to `/games/promotions/PROMO_2025_001`
2. Verify response status code
3. Verify response body structure
4. Verify returned data matches the promotion

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains:
  - `promotionId`: Valid UUID
  - `promotionRefId`: "PROMO_2025_001"
  - `status`: Current status (e.g., "ACTIVE", "EXPIRED")

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_GET_PROMO_002
**Title:** Verify retrieving promotion with non-existent promotion reference ID

**Pre-conditions:**
- API endpoint is accessible

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_NONEXISTENT"
```

**Steps to Execute:**
1. Send GET request to `/games/promotions/PROMO_NONEXISTENT`
2. Observe error response

**Expected Result:**
- HTTP Status: 404 Not Found or 400 Bad Request
- Error message: "Promotion not found"
- Error code: INVALID_REQUEST

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_GET_PROMO_003
**Title:** Verify retrieving promotion with empty promotion reference ID

**Pre-conditions:**
- API endpoint is accessible

**Test Data:**
```
URL Parameter: promotionRefId = ""
```

**Steps to Execute:**
1. Send GET request to `/games/promotions/`
2. Observe response

**Expected Result:**
- HTTP Status: 404 Not Found or 400 Bad Request
- Error message indicating invalid or missing promotion reference ID

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_GET_PROMO_004
**Title:** Verify retrieving cancelled promotion

**Pre-conditions:**
- Promotion with reference ID "PROMO_CANCELLED_001" exists with status "CANCELLED"

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_CANCELLED_001"
```

**Steps to Execute:**
1. Send GET request to `/games/promotions/PROMO_CANCELLED_001`
2. Verify response returns the promotion
3. Verify status field is "CANCELLED"

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains:
  - `promotionId`: Valid UUID
  - `promotionRefId`: "PROMO_CANCELLED_001"
  - `status`: "CANCELLED"

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_GET_PROMO_005
**Title:** Verify retrieving promotion with special characters in reference ID

**Pre-conditions:**
- Promotion with reference ID "PROMO_2025_#@!" exists in the system

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_2025_#@!"
```

**Steps to Execute:**
1. Send GET request to `/games/promotions/PROMO_2025_%23%40%21` (URL encoded)
2. Verify response

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains correct promotion details with matching promotionRefId

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** Ensure proper URL encoding of special characters

---

## 3. Cancel Promotion API Test Cases

### Test Case ID: TC_CANCEL_PROMO_001
**Title:** Verify cancelling promotion with valid promotion reference ID

**Pre-conditions:**
- Promotion with reference ID "PROMO_2025_001" exists with status "ACTIVE"

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_2025_001"
```

**Steps to Execute:**
1. Send DELETE request to `/games/promotions/PROMO_2025_001`
2. Verify response status code
3. Verify response body contains updated status
4. Verify promotion status is updated in database

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains:
  - `promotionId`: Valid UUID
  - `promotionRefId`: "PROMO_2025_001"
  - `status`: "CANCELLED"

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CANCEL_PROMO_002
**Title:** Verify cancelling promotion with non-existent promotion reference ID

**Pre-conditions:**
- API endpoint is accessible

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_NONEXISTENT"
```

**Steps to Execute:**
1. Send DELETE request to `/games/promotions/PROMO_NONEXISTENT`
2. Observe error response

**Expected Result:**
- HTTP Status: 404 Not Found or 400 Bad Request
- Error message: "Promotion not found"
- Error code: INVALID_REQUEST

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CANCEL_PROMO_003
**Title:** Verify cancelling already cancelled promotion

**Pre-conditions:**
- Promotion with reference ID "PROMO_CANCELLED_001" exists with status "CANCELLED"

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_CANCELLED_001"
```

**Steps to Execute:**
1. Send DELETE request to `/games/promotions/PROMO_CANCELLED_001`
2. Verify response

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains:
  - `promotionId`: Valid UUID
  - `promotionRefId`: "PROMO_CANCELLED_001"
  - `status`: "CANCELLED"

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** Idempotent operation - cancelling already cancelled promotion should succeed

---

### Test Case ID: TC_CANCEL_PROMO_004
**Title:** Verify cancelling expired promotion

**Pre-conditions:**
- Promotion with reference ID "PROMO_EXPIRED_001" exists with status "EXPIRED"

**Test Data:**
```
URL Parameter: promotionRefId = "PROMO_EXPIRED_001"
```

**Steps to Execute:**
1. Send DELETE request to `/games/promotions/PROMO_EXPIRED_001`
2. Verify response

**Expected Result:**
- HTTP Status: 200 OK or 400 Bad Request (depending on business rules)
- If allowed: status changes to "CANCELLED"
- If not allowed: error message indicating expired promotions cannot be cancelled

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** Verify business rules for cancelling expired promotions

---

### Test Case ID: TC_CANCEL_PROMO_005
**Title:** Verify cancelling promotion with empty reference ID

**Pre-conditions:**
- API endpoint is accessible

**Test Data:**
```
URL Parameter: promotionRefId = ""
```

**Steps to Execute:**
1. Send DELETE request to `/games/promotions/`
2. Observe response

**Expected Result:**
- HTTP Status: 404 Not Found or 400 Bad Request
- Error message indicating invalid or missing promotion reference ID

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

## 4. Claim Promotion API Test Cases

### Test Case ID: TC_CLAIM_PROMO_001
**Title:** Verify claiming promotion with valid promotion ID and game session

**Pre-conditions:**
- Active promotion with ID "promo_uuid_001" exists
- Valid game session token exists for player
- Player is eligible to claim the promotion
- Free spins are available

**Test Data:**
```
URL Parameter: id = "promo_uuid_001"
Game Session: Valid session token in request context
```

**Steps to Execute:**
1. Establish valid game session
2. Send GET request to `/games/promotions/promo_uuid_001/claim`
3. Include game session in request
4. Verify response status code
5. Verify free spins allotment is created

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains FreeSpinsAllotment object with:
  - Allotment ID
  - Promotion ID
  - Player ID
  - Number of free spins
  - Game details
  - Status: "ACTIVE"

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CLAIM_PROMO_002
**Title:** Verify claiming promotion without game session

**Pre-conditions:**
- Active promotion with ID "promo_uuid_001" exists

**Test Data:**
```
URL Parameter: id = "promo_uuid_001"
Game Session: None
```

**Steps to Execute:**
1. Send GET request to `/games/promotions/promo_uuid_001/claim`
2. Omit game session from request
3. Observe error response

**Expected Result:**
- HTTP Status: 401 Unauthorized or 400 Bad Request
- Error message indicating missing or invalid game session

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CLAIM_PROMO_003
**Title:** Verify claiming non-existent promotion

**Pre-conditions:**
- Valid game session exists

**Test Data:**
```
URL Parameter: id = "promo_nonexistent"
Game Session: Valid session token
```

**Steps to Execute:**
1. Establish valid game session
2. Send GET request to `/games/promotions/promo_nonexistent/claim`
3. Observe error response

**Expected Result:**
- HTTP Status: 404 Not Found or 400 Bad Request
- Error message indicating promotion not found

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CLAIM_PROMO_004
**Title:** Verify claiming already claimed promotion

**Pre-conditions:**
- Active promotion with ID "promo_uuid_001" exists
- Player has already claimed this promotion
- Valid game session exists

**Test Data:**
```
URL Parameter: id = "promo_uuid_001"
Game Session: Valid session token for player who already claimed
```

**Steps to Execute:**
1. Establish valid game session
2. Send GET request to `/games/promotions/promo_uuid_001/claim`
3. Observe response

**Expected Result:**
- HTTP Status: 400 Bad Request or 409 Conflict
- Error message indicating promotion already claimed

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CLAIM_PROMO_004
**Title:** Verify claiming already claimed promotion

**Pre-conditions:**
- Active promotion with ID "promo_uuid_001" exists
- Player has already claimed this promotion
- Valid game session exists

**Test Data:**
```
URL Parameter: id = "promo_uuid_001"
Game Session: Valid session token for player who already claimed
```

**Steps to Execute:**
1. Establish valid game session
2. Send GET request to `/games/promotions/promo_uuid_001/claim`
3. Observe response

**Expected Result:**
- HTTP Status: 400 Bad Request or 409 Conflict
- Error message indicating promotion already claimed

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CLAIM_PROMO_005
**Title:** Verify claiming cancelled promotion

**Pre-conditions:**
- Promotion with ID "promo_cancelled_001" exists with status "CANCELLED"
- Valid game session exists

**Test Data:**
```
URL Parameter: id = "promo_cancelled_001"
Game Session: Valid session token
```

**Steps to Execute:**
1. Establish valid game session
2. Send GET request to `/games/promotions/promo_cancelled_001/claim`
3. Observe error response

**Expected Result:**
- HTTP Status: 400 Bad Request
- Error message indicating promotion is not available or has been cancelled

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_CLAIM_PROMO_006
**Title:** Verify claiming expired promotion

**Pre-conditions:**
- Promotion with ID "promo_expired_001" exists with endDate in the past
- Valid game session exists

**Test Data:**
```
URL Parameter: id = "promo_expired_001"
Game Session: Valid session token
```

**Steps to Execute:**
1. Establish valid game session
2. Send GET request to `/games/promotions/promo_expired_001/claim`
3. Observe error response

**Expected Result:**
- HTTP Status: 400 Bad Request
- Error message indicating promotion has expired

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

## 5. Game Launch API Test Cases

### Test Case ID: TC_LAUNCH_001
**Title:** Verify launching game with valid token and parameters

**Pre-conditions:**
- Valid game session token exists
- Game with ID "slot_001" exists and is active
- Brand "TestBrand" exists and is active

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "slot_001"
  brand = "TestBrand"
```

**Steps to Execute:**
1. Send GET request to `/games/launch/valid_session_token_xyz123?gameId=slot_001&brand=TestBrand`
2. Verify response status code
3. Verify redirect or game launch URL is provided

**Expected Result:**
- HTTP Status: 302 Found (for REDIRECT mode) or 200 OK (for EMBEDDED/JSON mode)
- Response contains Location header with valid game URL
- Game URL is accessible and loads properly

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** Default launch mode is REDIRECT

---

### Test Case ID: TC_LAUNCH_002
**Title:** Verify launching game with invalid token

**Pre-conditions:**
- Game with ID "slot_001" exists
- Brand "TestBrand" exists

**Test Data:**
```
URL Path Parameter: token = "invalid_token_12345"
Query Parameters:
  gameId = "slot_001"
  brand = "TestBrand"
```

**Steps to Execute:**
1. Send GET request to `/games/launch/invalid_token_12345?gameId=slot_001&brand=TestBrand`
2. Observe error response

**Expected Result:**
- HTTP Status: 401 Unauthorized or 403 Forbidden
- Error message indicating invalid or expired token

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_LAUNCH_003
**Title:** Verify launching game with missing required parameter (gameId)

**Pre-conditions:**
- Valid game session token exists
- Brand "TestBrand" exists

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  brand = "TestBrand"
```

**Steps to Execute:**
1. Send GET request to `/games/launch/valid_session_token_xyz123?brand=TestBrand`
2. Omit gameId parameter
3. Observe error response

**Expected Result:**
- HTTP Status: 400 Bad Request
- Error message indicating missing required parameter: gameId

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_LAUNCH_004
**Title:** Verify launching game with missing required parameter (brand)

**Pre-conditions:**
- Valid game session token exists
- Game with ID "slot_001" exists

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "slot_001"
```

**Steps to Execute:**
1. Send GET request to `/games/launch/valid_session_token_xyz123?gameId=slot_001`
2. Omit brand parameter
3. Observe error response

**Expected Result:**
- HTTP Status: 400 Bad Request
- Error message indicating missing required parameter: brand

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_LAUNCH_005
**Title:** Verify launching non-existent game

**Pre-conditions:**
- Valid game session token exists
- Brand "TestBrand" exists

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "nonexistent_game"
  brand = "TestBrand"
```

**Steps to Execute:**
1. Send GET request to `/games/launch/valid_session_token_xyz123?gameId=nonexistent_game&brand=TestBrand`
2. Observe error response

**Expected Result:**
- HTTP Status: 404 Not Found or returns null URI
- Error code: GAME_COMING_SOON
- Error message indicating game not found or coming soon

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_LAUNCH_006
**Title:** Verify launching game with EMBEDDED launch mode

**Pre-conditions:**
- Valid game session token exists
- Game with ID "slot_001" exists and is active
- Brand "TestBrand" exists and is active

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "slot_001"
  brand = "TestBrand"
  mode = "EMBEDDED"
```

**Steps to Execute:**
1. Send GET request to `/games/launch/valid_session_token_xyz123?gameId=slot_001&brand=TestBrand&mode=EMBEDDED`
2. Verify response status code
3. Verify response format

**Expected Result:**
- HTTP Status: 200 OK
- Response contains Location header with game URL
- No redirect occurs (suitable for iframe embedding)

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_LAUNCH_007
**Title:** Verify launching game with JSON launch mode

**Pre-conditions:**
- Valid game session token exists
- Game with ID "slot_001" exists and is active
- Brand "TestBrand" exists and is active

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "slot_001"
  brand = "TestBrand"
  mode = "JSON"
```

**Steps to Execute:**
1. Send GET request to `/games/launch/valid_session_token_xyz123?gameId=slot_001&brand=TestBrand&mode=JSON`
2. Verify response status code
3. Verify response body format

**Expected Result:**
- HTTP Status: 200 OK
- Response body contains game URL as JSON string
- Example: "https://gameprovider.com/game/launch?token=xyz"

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** _None_

---

### Test Case ID: TC_LAUNCH_008
**Title:** Verify launching game captures correct IP address from X-Forwarded-For header

**Pre-conditions:**
- Valid game session token exists
- Game with ID "slot_001" exists
- Brand "TestBrand" exists

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "slot_001"
  brand = "TestBrand"
Headers:
  X-Forwarded-For = "192.168.1.100, 10.0.0.1"
```

**Steps to Execute:**
1. Send GET request with X-Forwarded-For header containing multiple IPs
2. Verify game launches successfully
3. Verify first IP (192.168.1.100) is captured and stored

**Expected Result:**
- HTTP Status: 302 Found or 200 OK
- IP address 192.168.1.100 is correctly extracted and associated with game session

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** Verify IP is logged/stored correctly for audit purposes

---

### Test Case ID: TC_LAUNCH_009
**Title:** Verify launching game with additional query parameters

**Pre-conditions:**
- Valid game session token exists
- Game with ID "slot_001" exists
- Brand "TestBrand" exists

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "slot_001"
  brand = "TestBrand"
  locale = "en_US"
  currency = "USD"
  customParam = "value123"
```

**Steps to Execute:**
1. Send GET request with additional query parameters
2. Verify game launches successfully
3. Verify additional parameters are passed through to game provider

**Expected Result:**
- HTTP Status: 302 Found or 200 OK
- Game URL includes all additional query parameters
- Game loads with correct locale and currency settings

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** Additional parameters should be forwarded to game provider

---

### Test Case ID: TC_LAUNCH_010
**Title:** Verify launching game with Referer header for brand URL tracking

**Pre-conditions:**
- Valid game session token exists
- Game with ID "slot_001" exists
- Brand "TestBrand" exists

**Test Data:**
```
URL Path Parameter: token = "valid_session_token_xyz123"
Query Parameters:
  gameId = "slot_001"
  brand = "TestBrand"
Headers:
  Referer = "https://testbrand.com/casino/games"
```

**Steps to Execute:**
1. Send GET request with Referer header
2. Verify game launches successfully
3. Verify Referer URL is captured and stored as brandUrl

**Expected Result:**
- HTTP Status: 302 Found or 200 OK
- Brand URL "https://testbrand.com/casino/games" is correctly extracted and associated with game launch

**Actual Result:** _(To be filled during execution)_

**Status:** Not Executed

**Remarks:** Referer tracking helps identify brand-specific launches

---

## Test Summary

**Total Test Cases:** 35

| API Category | Total Test Cases | Priority |
|--------------|------------------|----------|
| Award Promotion API | 5 | High |
| Get Promotion API | 5 | High |
| Cancel Promotion API | 5 | High |
| Claim Promotion API | 6 | High |
| Game Launch API | 10 | Critical |

### Coverage Areas:
- ✓ Positive scenarios (happy path)
- ✓ Negative scenarios (error handling)
- ✓ Missing parameters
- ✓ Invalid data
- ✓ Boundary conditions
- ✓ Authentication/Authorization
- ✓ Idempotency
- ✓ Special characters and edge cases

---

**Document Version:** 1.0  
**Last Updated:** December 12, 2025
