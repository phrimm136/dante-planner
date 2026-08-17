const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const CLIENT_ID = process.env.CLIENT_ID;
const CLIENT_SECRET = process.env.CLIENT_SECRET;
const ISSUER = 'https://idp-staging.dante-planner.com';
const PRIVATE_KEY = crypto.createPrivateKey(fs.readFileSync(process.env.ID_TOKEN_KEY_FILE, 'utf8'));
const PUBLIC_JWK = crypto.createPublicKey(PRIVATE_KEY).export({ format: 'jwk' });
const KID = 'e2e-stub';
const codes = new Map();
const tokens = new Map();
const b64u = (s) => Buffer.from(s).toString('base64url');
const subOf = (email) => crypto.createHash('sha256').update(email).digest('hex').slice(0, 21);

http.createServer((req, res) => {
  const u = new URL(req.url, ISSUER);
  const fail = (status, error) => {
    res.writeHead(status, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error }));
  };

  if (u.pathname === '/healthz') {
    res.writeHead(200);
    return res.end('ok');
  }

  if (u.pathname === '/auth' && req.method === 'GET') {
    const q = u.searchParams;
    if (q.get('client_id') !== CLIENT_ID || q.get('response_type') !== 'code'
        || q.get('code_challenge_method') !== 'S256' || !q.get('code_challenge')
        || !q.get('redirect_uri')) {
      return fail(400, 'invalid_request');
    }
    const email = q.get('login_hint') || 'e2e-user@example.invalid';
    const code = b64u(crypto.randomBytes(24));
    codes.set(code, { challenge: q.get('code_challenge'), email, used: false });
    const loc = new URL(q.get('redirect_uri'));
    loc.searchParams.set('code', code);
    if (q.get('state') !== null) loc.searchParams.set('state', q.get('state'));
    res.writeHead(302, { Location: loc.toString() });
    return res.end();
  }

  if (u.pathname === '/token' && req.method === 'POST') {
    let body = '';
    req.on('data', (c) => { body += c; });
    req.on('end', () => {
      const p = new URLSearchParams(body);
      if (p.get('grant_type') !== 'authorization_code') return fail(400, 'unsupported_grant_type');
      if (p.get('client_id') !== CLIENT_ID || p.get('client_secret') !== CLIENT_SECRET) {
        return fail(401, 'invalid_client');
      }
      const rec = codes.get(p.get('code'));
      if (!rec || rec.used) return fail(400, 'invalid_grant');
      rec.used = true;
      const digest = crypto.createHash('sha256').update(p.get('code_verifier') || '').digest();
      if (Buffer.from(digest).toString('base64url') !== rec.challenge) return fail(400, 'invalid_grant');
      const sub = subOf(rec.email);
      const accessToken = b64u(crypto.randomBytes(24));
      tokens.set(accessToken, { sub, email: rec.email });
      const now = Math.floor(Date.now() / 1000);
      const signingInput = [
        b64u(JSON.stringify({ alg: 'RS256', typ: 'JWT', kid: KID })),
        b64u(JSON.stringify({ iss: ISSUER, aud: CLIENT_ID, sub, email: rec.email, iat: now, exp: now + 3600 })),
      ].join('.');
      const signature = crypto.sign('RSA-SHA256', Buffer.from(signingInput), PRIVATE_KEY).toString('base64url');
      const idToken = `${signingInput}.${signature}`;
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({
        access_token: accessToken,
        id_token: idToken,
        token_type: 'Bearer',
        expires_in: 3600,
      }));
    });
    return;
  }

  if (u.pathname === '/jwks' && req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ keys: [{ ...PUBLIC_JWK, kid: KID, alg: 'RS256', use: 'sig' }] }));
  }

  if (u.pathname === '/userinfo' && req.method === 'GET') {
    const rec = tokens.get((req.headers.authorization || '').replace(/^Bearer /, ''));
    if (!rec) return fail(401, 'invalid_token');
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({ id: rec.sub, email: rec.email, verified_email: true }));
  }

  fail(404, 'not_found');
}).listen(3000);
