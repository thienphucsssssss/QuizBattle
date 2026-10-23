NETWORK QUIZ BATTLE - WINDOWS x64

CLIENT
1. Install QuizBattle-Setup.exe. Java/Maven/Python are NOT required.
2. Open Quiz Battle from Desktop/Start Menu. Every launch starts with the
   login form and an unselected "Who are you?" role selector. Choose Admin,
   teacher or student. No preview login or sample accounts are provided.
3. This installer includes the PUBLIC certificate of the Server it was built
   for. No TLS file/password entry is needed. Obtain it from your LAN operator.
4. No IP/port setup is shown in Client. Students can select a discovered LAN
   room or enter its 6-character code. Teachers/Admin select the discovered
   trusted Server (auto-selected when only one exists).
5. Teachers can create an account and sign in immediately. Admin uses the
   account bootstrapped on Server; students use nickname + room code.
   The Client never needs server.p12 or a copy of the database.

SERVER
1. Extract the ENTIRE Server folder to a writable location on your PC.
   Do not run start.bat from inside the ZIP. Do not place mutable data under
   Program Files. Java/Maven are NOT required: runtime/ is included.
2. Run configure.bat ONCE to save this PC's LAN IPv4, TLS key password and,
   if not initialized, a new admin password (8-128 characters, entered twice).
   Alternatively fill config/server.json directly. The bank starts empty.
3. Build the Client installer with -ClientTruststore pointing to THIS Server's
   certs/client-truststore.p12, and -ServerHost set to its LAN IPv4. Verify its
   fingerprint. A freshly initialized Server has a different certificate; do
   not pair it with an installer built for another Server.
4. Double-click start.bat. It reads config/server.json and does NOT prompt.
   Wait for NQB_SERVER_READY. Keep the window open. Ctrl+C stops the Server.
5. If first-run bootstrap was interrupted after creating an empty database,
   start.bat uses the bootstrap password saved in config/server.json. You can
   also use init-admin.bat; it refuses to replace an existing admin. Never delete the
   database to recover a password. Stop Server before manual initialization.

Saved TLS passwords are encrypted by Windows DPAPI for the current account.
The TLS password field in JSON is cleared after conversion; the bootstrap
password is cleared after the Admin account is created. Moving to a different
PC/Windows account requires configure.bat once again. Edit server.json for
IP/port/database settings; server.runtime.properties is auto-generated.

Both PCs must be on the same reachable LAN. 127.0.0.1 means THIS PC, not the
Server on another PC. Windows Firewall may need inbound TCP 5050 and UDP 5051 rules
limited to Private/LocalSubnet; these scripts never disable/change firewall.
No router port forwarding, cloud host or Internet is needed during play.

BACKUP / UPDATES
Stop Server and back up data/, certs/, config/ together. Keep TLS passwords
safe. This release does not include YOUR credentials or existing database.
Do not overwrite these folders with files from another Server installation.
Changing Server IP can require issuing a new matching certificate and giving
clients the new truststore. Setup refuses to overwrite existing certificates.

The installer is unsigned (no publisher certificate purchased). Windows may
show Unknown publisher/SmartScreen; verify the file source and SHA256SUMS.txt.
Do not disable antivirus or system-wide security to install it.
Uninstall Client via Windows Installed apps. Preferences may remain in the
Windows user profile. Private Server keys are never installed on Client.

TESTING ROLES ON ONE PC
Keep Server running, open QuizBattle several times. Log in as Admin with
the password you created, then use Users to create teacher accounts, or use
Create teacher account on the login form. Self-registration can be disabled
with registration.enabled=false in Server config. Log out
or use a second window to log in as a teacher (change the temporary password
first). Students use their own nickname and a real room code. Two or more
student windows are needed to start a match. One account supports one active
login at a time; use distinct teachers when testing simultaneous managers.

IMPORT QUESTIONS (0.3.4)
Log in as Admin or teacher, open your question bank, then choose Excel / Word
import. Preview all answers, review errors/duplicates, select rows and confirm.
Use View / Download template to save a blank .xlsx or .docx locally. No Office
installation or Internet is needed. Excel uses the CauHoi sheet; Word uses the
7-column table. Keep the headers. Text only, A/B/C/D correct answer, up to 500
questions and 5 MiB per file. Legacy .xls/.doc, arbitrary Word layouts, images,
macros and formulas are not supported. Import requires the waiting room state.
Preview/cancel never writes the bank; confirmation appends selected rows in
one transaction. Existing questions are never overwritten. Duplicates are
unchecked unless explicitly allowed. An expired preview can be checked again.
Both Client and Server must be updated to use this feature.
