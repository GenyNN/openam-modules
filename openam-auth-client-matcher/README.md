# openam-auth-client-matcher

## About

*An OpenAM Client Matcher Custom Authentication Module*

Instruction

1. Build this project by mvn
2. Copy your target/jar file to /path/to/tomcat/webapps/openam/WEB-INF/lib/
3. Execute create-svc command from Admin Tools UI. Here you need to copypaste the amClientMatcherAuth.xml from the client matcher module, not just provide the path to the file!
4. Execute register-auth-module command from Tools UI. Here you need to enter the main class full name from client matcher module, e.g. ClientMatcherAuth.
5. Experience shows that OpenAM might need to be restarted after that, otherwise exceptions like "Not Found" might appear later on "Add Module" step. I also restarted after updating the module jar file.
6. Now go to OpenAM dashboard and select your Realm.
7. Select Authentication -> Modules. Click Add Module. Select your module from the list of available modules. Search by the name.
So you should search for the name in client-matcher-auth-service-description = Client Matcher  Authentication Module
8. Now you can create chain with this module. E.g. if the module name is ClientMatcher, then the create chain with name like cmchain
9. Use address  http://openamhost:port/openam/json/authenticate?authIndexType=service&authIndexValue=cmchain
to connect by REST client. First request shold  be called with empty body.
10. Copy response to next request with same url and run request again with required params in callbacks: email, club one, phone
11. If all performed OK token id should be in response

