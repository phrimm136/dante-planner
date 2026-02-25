# JWT vs Session-Based Authentication

## The Problem
Users need to authenticate once, then make multiple authenticated requests without re-logging in.

## Solution 1: Session-Based
```
Client → Login → Server creates session in DB/Redis
Server → Cookie with session ID → Client
Client → Request + session ID → Server looks up session → Validates
```

**Pros:**
- Simple revocation (delete session from DB)
- Small cookie size (just session ID)
- Server controls everything

**Cons:**
- Stateful (need session storage)
- Horizontal scaling needs sticky sessions or shared storage
- Database lookup on every request

## Solution 2: JWT-Based
```
Client → Login → Server creates JWT token
Server → Cookie with JWT → Client
Client → Request + JWT → Server validates signature → Extracts user info
```

**Pros:**
- Stateless (no server storage)
- Easy horizontal scaling
- Contains user info (no DB lookup)
- Works across domains

**Cons:**
- Large cookie size (contains claims)
- Revocation requires blacklist
- Can't update token content

## When to Choose JWT

**Choose JWT when:**
- Microservices architecture
- Need horizontal scaling
- Mobile + web clients
- Cross-domain auth

**Choose Sessions when:**
- Monolithic application
- Need instant revocation
- Session data changes frequently
- Single domain

## LimbusPlanner's Choice: JWT

**Why:**
- Planning to scale horizontally (multiple backend instances)
- Simpler deployment (no shared session storage)
- Future mobile app support

**Solved revocation problem:**
- Token blacklist (for logout/rotation)
- Refresh token pattern (short-lived access tokens)
- User token invalidation timestamp (for role changes)
