# openam-auth-post-social

## TODO: edit, change all references to ClientMatcher
## About

*An OpenAM Post-Social Authentication Module*

Instruction

1. Build this project by mvn
2. Copy your target/jar file to /path/to/tomcat/webapps/openam/WEB-INF/lib/
3. Execute create-svc command from Admin Tools UI. Here you need to copypaste the amAuthPostSocial.xml, not just provide the path to the file!
4. Execute register-auth-module command from Tools UI. Here you need to enter the main class full name: com.tallink.sso.openam.postsocial.PostSocial.
5. Experience shows that OpenAM might need to be restarted after that, otherwise exceptions like "Not Found" might appear later on "Add Module" step. I also restarted after updating the module jar file.
6. Now go to OpenAM dashboard and select your Realm.
7. Select Authentication -> Modules. Click Add Module. Select your module from the list of available modules. Search by the name "Post Social Module".
8. Now you can create chain with this module. 
9. Use address  http://openamhost:port/openam/json/authenticate?authIndexType=service&authIndexValue=<your-chain-name>
to connect by REST client. 
10. Use address http://openamhost:port/openam/XUI/#login/&service=<your-chain-name> 


