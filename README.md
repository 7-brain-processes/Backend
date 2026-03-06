# Backend
To run:
1. create .env
2. docker compose up
3. tests: .\mvnw.cmd test -pl . -Dtest="AuthServiceTest,JwtProviderTest,CustomUserDetailsServiceTest,UserPrincipalTest,JwtAuthenticationFilterTest"
4. swagger: http://localhost:8080/swagger-ui/index.html