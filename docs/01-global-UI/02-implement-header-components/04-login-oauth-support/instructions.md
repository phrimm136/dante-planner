# Task: Login Oauth Support

## Description
For guests, pressing the user icon in the header shows a dropdown menu that shows a text "Welcome, guest!" and then google login and apple login button. Pressing each of them pops up the corresponding oauth2 login process window. Implement oauth process in in both frontend and backend, issue JWT token for authentication, and save the account info into the mysql db.

## Research
- Is there appropriate frontend libraries for google login and apple login?
- How to implement oauth2 process?
- Security concerns such as CSRF
- Proper JWT issue principle, access/refresh token structure
- DB schema and the corresponding hibernate ORM for storing the user info
- Cross-login between google and apple login

## Scope
- backend/src/main/
- backend/application.properties
- frontend/src/components/Header.tsx

## Target Code Areas
- backend/src/main/
- frontend/src/components/Header.tsx
- frontend/src/components

## Testing Guidelines
- Prompt the developer to login via google and apple
- The test account is `phrimm136@gmail.com`. Make sure that this account resides in the db.
- Google login and apple login with the same account must share the account info. After two logins, there must be only one account, `phrimm136@gmailcom`.